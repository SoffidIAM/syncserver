/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.AttributeMapping;
import com.soffid.iam.api.ObjectMapping;
import com.soffid.iam.api.ObjectMappingProperty;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.interp.Evaluator;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObjectMapping;
import com.soffid.iam.sync.intf.ExtensibleObjects;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.tools.NullSqlObjet;

import es.caib.seycon.ng.comu.AttributeDirection;
import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 * 
 */
public class ObjectTranslator
{
	com.soffid.iam.api.System dispatcher;
	ServerService serverService;
	ExtensibleObjectFinder objectFinder;
	es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder objectFinderV1;
	private Collection<ExtensibleObjectMapping> objects;
	BSHAgentObject agentObject;
	
	/**
	 * @throws InternalErrorException
	 * 
	 */
	public ObjectTranslator (com.soffid.iam.api.System dispatcher) throws InternalErrorException
	{
		DispatcherService dispatcherService;
		ServiceLocator sl = ServiceLocator.instance();
		dispatcherService = sl.getDispatcherService();
		serverService = sl.getServerService();
		this.dispatcher = dispatcher;
		Collection<ObjectMapping> tmpObjects = dispatcherService.findObjectMappingsByDispatcher(dispatcher.getId());
		objects = new LinkedList<ExtensibleObjectMapping>();
		for (ObjectMapping tmpObject: tmpObjects)
		{
			ExtensibleObjectMapping object = new ExtensibleObjectMapping(tmpObject);
			objects.add (object);
			HashMap<String,String> properties = new HashMap<String, String>();
			for ( ObjectMappingProperty property: dispatcherService.findObjectMappingPropertiesByObject(object.getId()))
			{
				properties.put(property.getProperty(), property.getValue());
			}
			object.setProperties(properties);
			object.setAttributes(dispatcherService.findAttributeMappingsByObject(object.getId()));
			object.setTriggers(dispatcherService.findObjectMappingTriggersByObject(object.getId()));
		}
		agentObject = new BSHAgentObject(null, null, this);
	}

	public ObjectTranslator (com.soffid.iam.api.System dispatcher, ServerService serverService, Collection<ExtensibleObjectMapping> objects) throws InternalErrorException
	{
		this.serverService = serverService;
		this.dispatcher = dispatcher;
		this.objects = objects;
		agentObject = new BSHAgentObject(null, null, this);
	}

	public ExtensibleObjectFinder getObjectFinder() {
		return objectFinder;
	}

	public void setObjectFinder(ExtensibleObjectFinder objectFinder) {
		this.objectFinder = objectFinder;
		objectFinderV1 = null;
		agentObject = new BSHAgentObject(objectFinder, null, this);
	}

	public es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder getObjectFinderV1() {
		return objectFinderV1;
	}

	public void setObjectFinderV1(es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder objectFinder) {
		this.objectFinderV1 = objectFinder;
		objectFinder = null;
		agentObject = new BSHAgentObject(null, objectFinderV1, this);
	}

	public Collection<ExtensibleObjectMapping> getObjects ()
	{
		return objects;
	}

	public ExtensibleObjects parseInputObjects (ExtensibleObject source) throws InternalErrorException
	{
		ExtensibleObjects target = new ExtensibleObjects();
		
		for (ExtensibleObjectMapping objectMapping: objects)
		{
			ExtensibleObject obj = parseInputObject(source, objectMapping);
			if (obj != null)
    			target.getObjects().add(obj);
		}

		return target;

	}

	public ExtensibleObject parseInputObject (ExtensibleObject source, ExtensibleObjectMapping objectMapping) throws InternalErrorException
	{
		if (objectMapping.getSystemObject().equals(source.getObjectType()))
		{
			ExtensibleObject obj = new ExtensibleObject();
			obj.setObjectType(objectMapping.getSoffidObject().getValue());
			
			for (AttributeMapping attribute : objectMapping.getAttributes())
			{
				if (attribute.getDirection().equals(AttributeDirection.INPUT)
								|| attribute.getDirection().equals(
												AttributeDirection.INPUTOUTPUT))
				{
					evaluate (source, obj, attribute.getSoffidAttribute(), attribute.getSystemAttribute());
				}
			}
			if ( objectMapping.getSoffidObject() == SoffidObjectType.OBJECT_CUSTOM)
			{
				obj.setAttribute("type", objectMapping.getSoffidCustomObject());
			}
			return obj;
		}
		else
			return null;
	}

	public ExtensibleObjects generateObjects (ExtensibleObject sourceObject)
					throws InternalErrorException
	{
		ExtensibleObjects targetObjects = new ExtensibleObjects();
		for (ExtensibleObjectMapping mapping: objects)
		{
			if (mapping.appliesToSoffidObject( sourceObject))
			{
    			ExtensibleObject obj = generateObject(sourceObject, mapping);
    			if (obj != null)
    				targetObjects.getObjects().add(obj);
			}
		}
		return targetObjects;
	}

	public ExtensibleObject generateObject (ExtensibleObject sourceObject,
					ExtensibleObjectMapping objectMapping)
					throws InternalErrorException
	{
		return generateObject (sourceObject, objectMapping, false);
	}

	public ExtensibleObject generateObject (ExtensibleObject sourceObject,
					ExtensibleObjectMapping objectMapping, boolean ignoreErrors)
					throws InternalErrorException
	{
		if (!evalCondition(sourceObject, objectMapping))
			return null;
		else
		{
			ExtensibleObject targetObject = new ExtensibleObject();
			targetObject.setObjectType(objectMapping.getSystemObject());
			boolean anyAttribute = false;
			for (AttributeMapping attribute : objectMapping.getAttributes())
			{
				if (attribute.getDirection().equals(AttributeDirection.OUTPUT)
								|| attribute.getDirection().equals(
												AttributeDirection.INPUTOUTPUT))
				{
					try {
						evaluate (sourceObject, targetObject, attribute.getSystemAttribute(), attribute.getSoffidAttribute());
						anyAttribute = true;
					} catch (InternalErrorException e) {
						if (!ignoreErrors) throw e;
					}
				}
			}
			if (anyAttribute)
				return targetObject;
			else
				return null;
		}
	}



	/**
	 * @param object2 
	 * @param accountName
	 * @param ead
	 * @param dades
	 * @param attributeExpression
	 * @return
	 * @throws InternalErrorException 
	 */
	private void evaluate (ExtensibleObject sourceObject, ExtensibleObject targetObject,
					String attribute, String attributeExpression)
					throws InternalErrorException
	{
		try { 
			AttributeReference ar = AttributeReferenceParser.parse(sourceObject, attributeExpression);
			Object value = ar.getValue();
			if (value == null || 
					value instanceof NullSqlObjet || 
					value instanceof es.caib.seycon.ng.sync.bootstrap.NullSqlObjet)
				AttributeReferenceParser.parse(targetObject, attribute).setValue( null );
			else
				AttributeReferenceParser.parse(targetObject, attribute).setValue( ar.getValue() );
		} catch (Exception ear) {
			Map<String,Object> vars = new ExtensibleObjectVars(sourceObject, serverService, agentObject);
			try {
				Object result = Evaluator.instance().evaluate(attributeExpression, vars, "attribute "+attribute);
				AttributeReferenceParser.parse(targetObject, attribute)
					.setValue(result);
			} catch (InternalErrorException e) {
				throw e;
			} catch (Exception e) {
				throw new InternalErrorException ("Error evaluating attribute "+attribute, e);
			}
		}
	}

	
	public Collection<ExtensibleObjectMapping> getObjectsBySoffidType (SoffidObjectType type)
	{
		Collection<ExtensibleObjectMapping> result = new LinkedList<ExtensibleObjectMapping>();
		for (ExtensibleObjectMapping object: objects)
		{
			if (object.getSoffidObject() != null && object.getSoffidObject().equals (type))
				result.add (object);
		}
		return result;
	}
	
	public Map<String,String> getObjectProperties (ExtensibleObject obj)
	{
		for (ExtensibleObjectMapping mapping: this.objects)
		{
			if (mapping.getSystemObject().equals(obj.getObjectType()))
				return mapping.getProperties();
		}
		return Collections.emptyMap();
	}

	public Object parseInputAttribute(String attributeName, ExtensibleObject sourceObject,
			ExtensibleObjectMapping objectMapping) throws InternalErrorException {
		if (objectMapping.getSystemObject().equals(sourceObject.getObjectType()))
		{
			ExtensibleObject obj = new ExtensibleObject();
			obj.setObjectType(objectMapping.getSoffidObject().getValue());
			
			for (AttributeMapping attribute : objectMapping.getAttributes())
			{
				if (attribute.getSoffidAttribute().equals (attributeName))
				{
					if (attribute.getDirection().equals(AttributeDirection.INPUT)
									|| attribute.getDirection().equals(
													AttributeDirection.INPUTOUTPUT))
					{
						try { 
							return AttributeReferenceParser.parse(sourceObject, attribute.getSystemAttribute()).getValue();
						} catch (Exception ear) {
							ExtensibleObjectVars newNs = new ExtensibleObjectVars(
											sourceObject, serverService,
											agentObject);
	
							try {
								Object result = Evaluator.instance().evaluate(attribute.getSystemAttribute(), newNs, "attribute "+attribute.getSoffidAttribute());
								return result;
							} catch (Exception e) {
								throw new InternalErrorException ("Error evaluating attribute "+attribute.getSoffidAttribute(), e);
							}
						}
					}
				}
			}
		}
		return null;
	}

	public Object generateAttribute(String attributeName, ExtensibleObject sourceObject,
			ExtensibleObjectMapping objectMapping) throws InternalErrorException {
		if (objectMapping.getSoffidObject().toString().equals(sourceObject.getObjectType()))
		{
			ExtensibleObject obj = new ExtensibleObject();
			obj.setObjectType(objectMapping.getSoffidObject().getValue());
			
			for (AttributeMapping attribute : objectMapping.getAttributes())
			{
				if (attribute.getSystemAttribute().equals (attributeName))
				{
					if (attribute.getDirection().equals(AttributeDirection.OUTPUT)
									|| attribute.getDirection().equals(
													AttributeDirection.INPUTOUTPUT))
					{
						ExtensibleObjectVars newNs = new ExtensibleObjectVars(sourceObject, serverService,
										agentObject);

						try {
							Object result = Evaluator.instance().evaluate(attribute.getSoffidAttribute(), newNs, "attribute "+attribute.getSystemAttribute());
							return result;
						} catch (Exception e) {
							throw new InternalErrorException ("Error evaluating attribute "+attribute.getSystemAttribute(), e);
						}
					}
				}
			}
		}
		return null;
	}

	public boolean evalCondition(ExtensibleObject sourceObject,
			ExtensibleObjectMapping objectMapping) throws InternalErrorException {
		String condition = objectMapping.getCondition();
		return evalExpression(sourceObject, condition);
	}

	public boolean evalExpression(ExtensibleObject sourceObject, String condition) throws InternalErrorException {
		if (condition == null || condition.trim().length() == 0)
			return true;
		
		ExtensibleObjectVars newNs = new ExtensibleObjectVars(sourceObject, serverService,
						agentObject);
		try {
			Object result = Evaluator.instance().evaluate(condition, newNs, condition);
			if (result == null || "false".equalsIgnoreCase(result.toString()))
				return false;
			else
				return true;
		} catch (Exception ex) {
			throw new InternalErrorException ("Error parsing expression "+condition, ex);
		}
	}
	
	public Object eval(String expr, ExtensibleObject eo) throws InternalErrorException {
		ExtensibleObjectVars newNs = new ExtensibleObjectVars(eo, serverService,
						agentObject);

		try {
			Object result = Evaluator.instance().evaluate(expr, newNs, expr);
			return result;
		} catch (Exception e) {
			throw new InternalErrorException ("Error evaluating expression "+expr, e);
		}
	}

}

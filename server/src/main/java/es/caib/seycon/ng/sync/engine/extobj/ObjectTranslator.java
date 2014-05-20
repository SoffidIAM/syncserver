/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.axis.InternalException;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSpace;
import bsh.Primitive;
import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.AttributeDirection;
import es.caib.seycon.ng.comu.AttributeMapping;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.ObjectMapping;
import es.caib.seycon.ng.comu.ObjectMappingProperty;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.sync.intf.AuthoritativeChange;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObjectMapping;
import es.caib.seycon.ng.sync.intf.ExtensibleObjects;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 * 
 */
public class ObjectTranslator
{
	Dispatcher dispatcher;
	ServerService serverService;
	private Collection<ExtensibleObjectMapping> objects;

	/**
	 * @throws InternalErrorException
	 * 
	 */
	public ObjectTranslator (Dispatcher dispatcher) throws InternalErrorException
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
		}
	}

	public ObjectTranslator (Dispatcher dispatcher, ServerService serverService, Collection<ExtensibleObjectMapping> objects) throws InternalErrorException
	{
		this.serverService = serverService;
		this.dispatcher = dispatcher;
		this.objects = objects;
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
			if (mapping.getSoffidObject().getValue().equals(sourceObject.getObjectType()))
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
		Interpreter interpret = new Interpreter();
		NameSpace ns = interpret.getNameSpace();

		ExtensibleObjectNamespace newNs = new ExtensibleObjectNamespace(ns, interpret.getClassManager(),
						"translator" + dispatcher.getCodi(), sourceObject);

		try {
			Object result = interpret.eval(attributeExpression, newNs);
			if (result instanceof Primitive)
			{
				result = ((Primitive)result).getValue();
			}
			targetObject.setAttribute(attribute, result);
		} catch (EvalError e) {
			throw new InternalErrorException ("Error evaluating attribute "+attribute+": "+e.getErrorText());
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
}

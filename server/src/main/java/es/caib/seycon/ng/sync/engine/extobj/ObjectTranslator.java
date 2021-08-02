/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.soffid.iam.api.System;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.TargetError;
import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.AttributeDirection;
import es.caib.seycon.ng.comu.AttributeMapping;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.ObjectMapping;
import es.caib.seycon.ng.comu.ObjectMappingProperty;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObjectMapping;
import es.caib.seycon.ng.sync.intf.ExtensibleObjects;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 * 
 */
public class ObjectTranslator {
	private com.soffid.iam.sync.engine.extobj.ObjectTranslator delegate = null;

	ExtensibleObjectFinder objectFinder = null;
	private Collection<ExtensibleObjectMapping> objects;
	/**
	 * @throws InternalErrorException
	 * 
	 */
	public ObjectTranslator (Dispatcher dispatcher) throws InternalErrorException
	{
		delegate = new com.soffid.iam.sync.engine.extobj.ObjectTranslator (System.toSystem(dispatcher));
		List<ExtensibleObjectMapping> l = ExtensibleObjectMapping.toExtensibleObjectMappingList(delegate.getObjects());
		Collections.sort(l, new Comparator<ExtensibleObjectMapping>() {
			@Override
			public int compare(ExtensibleObjectMapping o1, ExtensibleObjectMapping o2) {
				return o1.getSystemObject().compareTo(o2.getSystemObject());
			}
			
		});
		this.objects = l;
	}

	public ObjectTranslator (Dispatcher dispatcher, ServerService serverService, Collection<ExtensibleObjectMapping> objects) throws InternalErrorException
	{
		this.objects = objects;
		delegate = new com.soffid.iam.sync.engine.extobj.ObjectTranslator (
				System.toSystem(dispatcher), 
				InterfaceWrapper.getServerService(serverService),
				com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMappingList(objects));
	}

	public ExtensibleObjectFinder getObjectFinder() {
		return objectFinder;
	}

	public void setObjectFinder(ExtensibleObjectFinder objectFinder) {
		this.objectFinder = objectFinder;
		delegate.setObjectFinder(InterfaceWrapper.getExtensibleObjectFinder(objectFinder));
	}

	public Collection<ExtensibleObjectMapping> getObjects ()
	{
		return objects;
	}

	

	public ExtensibleObjects parseInputObjects (ExtensibleObject source) throws InternalErrorException
	{
		return ExtensibleObjects.toExtensibleObjects(
				delegate.parseInputObjects(source));
	}

	public ExtensibleObject parseInputObject (ExtensibleObject source, ExtensibleObjectMapping objectMapping) throws InternalErrorException
	{
		return ExtensibleObject.toExtensibleObject(
				delegate.parseInputObject(
						source, 
						com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMapping(objectMapping)));
	}

	public ExtensibleObjects generateObjects (ExtensibleObject sourceObject)
					throws InternalErrorException
	{
		return ExtensibleObjects.toExtensibleObjects(
				delegate.generateObjects(sourceObject));
	}

	public ExtensibleObject generateObject (ExtensibleObject sourceObject,
					ExtensibleObjectMapping objectMapping)
					throws InternalErrorException
	{
		return ExtensibleObject.toExtensibleObject(
				delegate.generateObject(
						sourceObject,
						com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMapping(objectMapping)
						));
	}

	public ExtensibleObject generateObject (ExtensibleObject sourceObject,
					ExtensibleObjectMapping objectMapping, boolean ignoreErrors)
					throws InternalErrorException
	{
		return ExtensibleObject.toExtensibleObject(
				delegate.generateObject(
						sourceObject,
						com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMapping(objectMapping),
						ignoreErrors
						));
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
		return delegate.parseInputAttribute(
				attributeName, 
				sourceObject, 
				com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMapping(objectMapping));
	}

	public Object generateAttribute(String attributeName, ExtensibleObject sourceObject,
			ExtensibleObjectMapping objectMapping) throws InternalErrorException {
		return delegate.generateAttribute(
				attributeName, 
				sourceObject, 
				com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMapping(objectMapping));
	}

	public boolean evalCondition(ExtensibleObject sourceObject,
			ExtensibleObjectMapping objectMapping) throws InternalErrorException {
		return delegate.evalCondition(
				sourceObject, 
				com.soffid.iam.sync.intf.ExtensibleObjectMapping.toExtensibleObjectMapping(objectMapping));
	}

	public boolean evalExpression(ExtensibleObject sourceObject, String condition) throws InternalErrorException {
		return delegate.evalExpression(sourceObject, condition);
	}
	public Object eval(String expr, ExtensibleObject eo)
			throws InternalErrorException {
		return delegate.eval(expr, eo);
	}
}

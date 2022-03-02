package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.Map;

import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObjects;

import es.caib.seycon.ng.exception.InternalErrorException;

public class BSHAgentObject {
	ExtensibleObjectFinder finder = null;
	ObjectTranslator translator = null;
	private es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder finderV1;

	public BSHAgentObject(ExtensibleObjectFinder finder,
			es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder finderV1,
			ObjectTranslator translator) {
		super();
		this.finder = finder;
		this.finderV1 = finderV1;
		this.translator = translator;
	}

	public Map<String,Object> search (ExtensibleObject pattern) throws Exception
	{
		Object[] __r = (Object[]) java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<Object>() {
			public Object run() {
				try {
					Object o = null;
					if (finder != null)
					{
						o = finder.find(pattern);
					}
					else if (finderV1 == null)
					{
						es.caib.seycon.ng.sync.intf.ExtensibleObject eo = new es.caib.seycon.ng.sync.intf.ExtensibleObject();
						eo.setObjectType(pattern.getObjectType());
						eo.putAll(pattern);
						o = finderV1.find(eo);
					}
					else
						o = null;
					return new Object[] {o};
				} catch (Throwable th) {
					return new Object[] {null,th};
				}
			}
		});
		if (__r.length == 1 ) 
			return (Map<String, Object>) __r[0];
		else
			throw (Exception) __r[1];
	}

	public ExtensibleObject soffidToSystem (ExtensibleObject soffidObject) throws InternalErrorException
	{
		Object[] __r = (Object[]) java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<Object>() {
			public Object run() {
				try {
					ExtensibleObjects objs = translator.generateObjects(soffidObject);
					if (objs.getObjects().isEmpty())
						return null;
					else
						return new Object[] {objs.getObjects().get(0)};
				} catch (InternalErrorException th) {
					return new Object[] {null,th};
				}
			}
		});
		if (__r.length == 1 ) 
			return (ExtensibleObject) __r[0];
		else
			throw (InternalErrorException) __r[1];
	}

	public ExtensibleObject systemToSoffid (ExtensibleObject systemObject) throws InternalErrorException
	{
		Object[] __r = (Object[]) java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<Object>() {
			public Object run() {
				try {
					ExtensibleObjects objs = translator.parseInputObjects(systemObject);
					if (objs.getObjects().isEmpty())
						return null;
					else
						return new Object[] {objs.getObjects().get(0)};
				} catch (InternalErrorException th) {
					return new Object[] {null,th};
				}
			}
		});
		if (__r.length == 1 ) 
			return (ExtensibleObject) __r[0];
		else
			throw (InternalErrorException) __r[1];
	}

	public Collection<Map<String,Object>> invoke (String verb, String command, Map<String, Object> params) throws InternalErrorException
	{
		Object[] __r = (Object[]) java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<Object>() {
			public Object run() {
				try {
					if (finder != null)
						return new Object[] { finder.invoke(verb, command, params)};
			
					if (finderV1 != null)
					{
						return new Object[] {finderV1.invoke(verb, command, params)};
					}
			
					throw new InternalErrorException("Invoke method not supported");
				} catch (InternalErrorException th) {
					return new Object[] {null,th};
				}
			}
		});
		if (__r.length == 1 ) 
			return (Collection<Map<String, Object>>) __r[0];
		else
			throw (InternalErrorException) __r[1];
		
	}
}

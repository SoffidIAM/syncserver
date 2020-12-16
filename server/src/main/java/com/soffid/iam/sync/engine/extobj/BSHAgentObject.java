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
		if (finder != null)
		{
			return finder.find(pattern);
		}
		else if (finderV1 == null)
		{
			es.caib.seycon.ng.sync.intf.ExtensibleObject eo = new es.caib.seycon.ng.sync.intf.ExtensibleObject();
			eo.setObjectType(pattern.getObjectType());
			eo.putAll(pattern);
			return finderV1.find(eo);
		}
		else
			return null;
	}

	public ExtensibleObject soffidToSystem (ExtensibleObject soffidObject) throws InternalErrorException
	{
		ExtensibleObjects objs = translator.generateObjects(soffidObject);
		if (objs.getObjects().isEmpty())
			return null;
		else
			return objs.getObjects().get(0);
	}

	public ExtensibleObject systemToSoffid (ExtensibleObject systemObject) throws InternalErrorException
	{
		ExtensibleObjects objs = translator.parseInputObjects(systemObject);
		if (objs.getObjects().isEmpty())
			return null;
		else
			return objs.getObjects().get(0);
	}

	public Collection<Map<String,Object>> invoke (String verb, String command, Map<String, Object> params) throws InternalErrorException
	{
		if (finder != null)
			return finder.invoke(verb, command, params);

		if (finderV1 != null)
		{
			return finderV1.invoke(verb, command, params);
		}

		throw new InternalErrorException("Invoke method not supported");
		
	}
	
	
}

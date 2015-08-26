package com.soffid.iam.sync.engine.extobj;

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

	public Map<String,Object> search (Map<String,Object> pattern) throws Exception
	{
		if (finder != null)
		{
			ExtensibleObject eo = new ExtensibleObject();
			eo.putAll(pattern);
			return finder.find(eo);
		}
		else if (finderV1 == null)
		{
			es.caib.seycon.ng.sync.intf.ExtensibleObject eo = new es.caib.seycon.ng.sync.intf.ExtensibleObject();
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
}

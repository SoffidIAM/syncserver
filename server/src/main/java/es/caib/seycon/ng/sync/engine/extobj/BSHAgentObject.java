package es.caib.seycon.ng.sync.engine.extobj;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObjects;

public class BSHAgentObject {
	ExtensibleObjectFinder finder = null;
	ObjectTranslator translator = null;

	public BSHAgentObject(ExtensibleObjectFinder finder,
			ObjectTranslator translator) {
		super();
		this.finder = finder;
		this.translator = translator;
	}

	public ExtensibleObject search (ExtensibleObject pattern) throws Exception
	{
		if (finder == null)
			return null;
		else
			return finder.find(pattern);
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

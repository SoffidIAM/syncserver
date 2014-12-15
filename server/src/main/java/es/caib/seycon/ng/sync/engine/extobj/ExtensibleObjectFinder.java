package es.caib.seycon.ng.sync.engine.extobj;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public interface ExtensibleObjectFinder {
	ExtensibleObject find (ExtensibleObject pattern) throws Exception;
}

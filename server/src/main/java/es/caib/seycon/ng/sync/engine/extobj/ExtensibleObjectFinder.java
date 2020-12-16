package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.Map;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public interface ExtensibleObjectFinder {
	ExtensibleObject find (ExtensibleObject pattern) throws Exception;

	Collection<Map<String, Object>> invoke(String verb, String command, Map<String, Object> params) throws InternalErrorException;
}

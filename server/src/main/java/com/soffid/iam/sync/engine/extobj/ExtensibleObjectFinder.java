package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.Map;

import com.soffid.iam.sync.intf.ExtensibleObject;

import es.caib.seycon.ng.exception.InternalErrorException;

public interface ExtensibleObjectFinder {
	ExtensibleObject find (ExtensibleObject pattern) throws Exception;
	
	Collection<Map<String,Object>> invoke(String verb, String command, Map<String, Object> params) throws InternalErrorException;

}

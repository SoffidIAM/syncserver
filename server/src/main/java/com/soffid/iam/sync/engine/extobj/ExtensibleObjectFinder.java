package com.soffid.iam.sync.engine.extobj;

import com.soffid.iam.sync.intf.ExtensibleObject;

public interface ExtensibleObjectFinder {
	ExtensibleObject find (ExtensibleObject pattern) throws Exception;
}

/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.sync.engine.InterfaceWrapper;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;

/**
 * @author bubu
 *
 */
public class CustomExtensibleObject extends com.soffid.iam.sync.engine.extobj.CustomExtensibleObject
{
	public CustomExtensibleObject (CustomObject obj, es.caib.seycon.ng.sync.servei.ServerService serverService)
	{
		super(obj, InterfaceWrapper.getServerService(serverService));
	}
	
}

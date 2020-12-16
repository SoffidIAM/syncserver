/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.api.Role;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class RoleExtensibleObject extends com.soffid.iam.sync.engine.extobj.RoleExtensibleObject
{
	public RoleExtensibleObject (Rol role, ServerService serverService)
	{
		super(Role.toRole(role), InterfaceWrapper.getServerService(serverService));
	}
}

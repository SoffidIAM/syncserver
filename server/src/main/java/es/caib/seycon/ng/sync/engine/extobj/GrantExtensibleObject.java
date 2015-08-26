/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class GrantExtensibleObject extends com.soffid.iam.sync.engine.extobj.GrantExtensibleObject
{
	public GrantExtensibleObject (RolGrant grant, ServerService serverService)
	{
		super(RoleGrant.toRoleGrant(grant), InterfaceWrapper.getServerService(serverService));
	}
}

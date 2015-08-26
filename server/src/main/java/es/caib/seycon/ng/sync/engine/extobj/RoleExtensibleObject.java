/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.soffid.iam.api.Role;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
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

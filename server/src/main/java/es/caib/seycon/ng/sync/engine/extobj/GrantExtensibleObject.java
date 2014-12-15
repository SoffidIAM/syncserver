/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.UserAccount;
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
public class GrantExtensibleObject extends ExtensibleObject
{
	RolGrant grant;
	
	ServerService serverService;

	public GrantExtensibleObject (RolGrant grant, ServerService serverService)
	{
		super();
		this.grant = grant;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_GRANT.getValue());
	}

	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		if (obj != null)
			return obj;
		
		if ("id".equals(attribute))
			obj = grant.getId();
		else if ("grantedRole".equals(attribute))
			obj = grant.getRolName();
		else if ("grantedRoleSystem".equals(attribute))
			obj = grant.getDispatcher();
		else if ("grantedRoleId".equals(attribute))
			obj = grant.getIdRol();
		else if ("domainValue".equals(attribute))
			obj = grant.getDomainValue();
		else if ("ownerAccount".equals(attribute))
			obj = grant.getOwnerAccountName();
		else if ("ownerSystem".equals(attribute))
			obj = grant.getOwnerDispatcher();
		else if ("ownerGroup".equals(attribute))
			obj = grant.getOwnerGroup();
		else if ("ownerRoleId".equals(attribute))
			obj = grant.getOwnerRol();
		else if ("ownerRoleName".equals(attribute))
			obj = grant.getOwnerRolName();
		else if ("ownerUser".equals(attribute))
			obj = grant.getUser();
		else if ("ownerUserObject".equals(attribute) && grant.getUser() != null)
		{
			try {
				obj = null;
				Usuari usuari = serverService.getUserInfo(grant.getOwnerAccountName(), grant.getOwnerDispatcher());
				if (usuari == null)
					return null;
				for (UserAccount acc: serverService.getUserAccounts(usuari.getId(), grant.getOwnerDispatcher()))
				{
					if (acc.getName().equals(grant.getOwnerAccountName()))
					{
						obj = new UserExtensibleObject(acc, usuari, serverService);
						break;
					}
				}
			} catch (InternalErrorException e) {
				throw new RuntimeException(e);
			} catch (UnknownUserException e) {
				obj = null;
			}
		}
		else if ("ownerAccountObject".equals(attribute) && grant.getOwnerAccountName() != null)
		{
			Account acc = new Account();
			acc.setName(grant.getOwnerAccountName());
			acc.setDispatcher(grant.getOwnerDispatcher());
			obj = new AccountExtensibleObject(acc, serverService);
		}
		else if ("grantedRoleObject".equals(attribute))
		{
			try {
				Rol role = serverService.getRoleInfo(grant.getRolName(), grant.getDispatcher());
				obj = new RoleExtensibleObject(role, serverService);
			} catch (InternalErrorException e) {
				throw new RuntimeException(e);
			} catch (UnknownRoleException e) {
				obj = null;
			}
		}
		else
			return null;
		put (attribute, obj);
		return obj;
	}
}

/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
/**
 * @author bubu
 *
 */
public class GrantExtensibleObject extends ExtensibleObject
{
	RoleGrant grant;
	
	ServerService serverService;

	public GrantExtensibleObject (RoleGrant grant, ServerService serverService)
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
			obj = grant.getRoleName();
		else if ("grantedRoleSystem".equals(attribute))
			obj = grant.getSystem();
		else if ("grantedRoleId".equals(attribute))
			obj = grant.getRoleId();
		else if ("domainValue".equals(attribute))
			obj = grant.getDomainValue();
		else if ("ownerAccount".equals(attribute))
			obj = grant.getOwnerAccountName();
		else if ("ownerSystem".equals(attribute))
			obj = grant.getOwnerSystem();
		else if ("ownerGroup".equals(attribute))
			obj = grant.getOwnerGroup();
		else if ("ownerRoleId".equals(attribute))
			obj = grant.getOwnerRole();
		else if ("ownerRoleName".equals(attribute))
			obj = grant.getOwnerRoleName();
		else if ("ownerUser".equals(attribute))
			obj = grant.getUser();
		else if ("holderGroup".equals(attribute))
			obj = grant.getHolderGroup();
		else if ("ownerUserObject".equals(attribute) && grant.getUser() != null)
		{
			try {
				obj = null;
				User usuari = serverService.getUserInfo(grant.getOwnerAccountName(), grant.getOwnerSystem());
				if (usuari == null)
					return null;
				for (UserAccount acc: serverService.getUserAccounts(usuari.getId(), grant.getOwnerSystem()))
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
			try {
				Account acc = serverService.getAccountInfo(grant.getOwnerAccountName(), grant.getOwnerSystem()); 
				obj = new AccountExtensibleObject(acc, serverService);
			} catch (InternalErrorException e) {
				throw new RuntimeException(e);
			}
		}
		else if ("grantedRoleObject".equals(attribute))
		{
			try {
				Role role = serverService.getRoleInfo(grant.getRoleName(), grant.getSystem());
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

/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;

/**
 * @author bubu
 *
 */
public class AccountExtensibleObject extends ExtensibleObject
{
	ServerService serverService;
	private Account account;
	
	public AccountExtensibleObject (Account account, ServerService serverService)
	{
		super();
		this.account = account;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_ACCOUNT.getValue());
		if (account.getId() == null)
		{
			try {
				Account account2 = serverService.getAccountInfo(account.getName(), account.getSystem());
				if (account2 != null)
					account = account2;
			} catch (InternalErrorException e) {
			}
		}

	}

	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		try
		{
    		if (obj != null)
    			return obj;
    		
    		if ("accountId".equals(attribute))
    			obj = account.getId();
    		else if ("accountName".equals(attribute))
    			obj = account.getName();
    		else if ("loginName".equals(attribute))
    			obj = account.getLoginName();
    		else if ("oldAccountName".equals(attribute) )
    			obj = account.getOldName();
    		else if ("passwordPolicy".equals(attribute))
    			obj = account.getPasswordPolicy();
    		else if ("system".equals(attribute))
    			obj = account.getSystem();
    		else if ("accountDescription".equals(attribute))
    			obj = account.getDescription();
    		else if ("accountStatus".equals(attribute))
    			obj = account.getStatus();
    		else if ("accountDisabled".equals(attribute))
    			obj = account.isDisabled();
    		else if ("type".equals(attribute))
    			obj = account.getType();
    		else if ("lastUpdate".equals(attribute))
    			obj = account.getLastUpdated();
    		else if ("lastLogin".equals(attribute))
    			obj = account.getLastLogin();
    		else if ("lastPasswordUpdate".equals(attribute))
    			obj = account.getLastPasswordSet();
    		else if ("passwordExpiration".equals(attribute))
    			obj = account.getPasswordExpiration();
    		else if ("ownerUsers".equals(attribute))
    			obj = account.getOwnerUsers();
    		else if ("ownerGroups".equals(attribute))
    			obj = account.getOwnerGroups();
    		else if ("ownerRoles".equals(attribute))
    			obj = account.getOwnerRoles();
    		else if ("grantedUsers".equals(attribute))
    			obj = account.getGrantedUsers();
    		else if ("granteeUsers".equals(attribute))
    			obj = account.getGrantedUsers();
    		else if ("grantedGroups".equals(attribute))
    			obj = account.getGrantedGroups();
    		else if ("granteeGroups".equals(attribute))
    			obj = account.getGrantedGroups();
    		else if ("granteeRoles".equals(attribute))
    			obj = account.getGrantedRoles();
    		else if ("managerUsers".equals(attribute))
    			obj = account.getManagerUsers();
    		else if ("managerGroups".equals(attribute))
    			obj = account.getManagerGroups();
    		else if ("managerRoles".equals(attribute))
    			obj = account.getManagerRoles();
    		else if ("grantedRoles".equals(attribute))
    		{
    			Collection<RoleGrant> grants = serverService.getAccountExplicitRoles(account.getName(), account.getSystem());
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
    			for (RoleGrant grant: grants)
    			{
    				dadesList.add ( new GrantExtensibleObject(grant, serverService));
    			}
    			obj = dadesList;
    		}
    		else if ("allGrantedRoles".equals(attribute))
    		{
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
    			Collection<RoleGrant> grants = serverService.getAccountRoles(account.getName(), account.getSystem());
    			for (RoleGrant grant: grants)
    			{
    				dadesList.add ( new GrantExtensibleObject(grant, serverService));
    			}
    			obj = dadesList;
    		}
    		else if ("granted".equals(attribute))
    		{
    			Collection<RoleGrant> grants = serverService.getAccountExplicitRoles(account.getName(), account.getSystem());
    			List<String> dadesList = new LinkedList<String>();
    			for (RoleGrant grant: grants)
    			{
    				dadesList.add ( grant.getRoleName());
    			}
    			for (Group grup: serverService.getUserGroups(account.getName(), account.getSystem()))
    			{
    				dadesList.add(grup.getName());
    			}
    			obj = dadesList;
    		}
    		else if ("allGranted".equals(attribute))
    		{
    			Collection<RoleGrant> grants = serverService.getAccountRoles(account.getName(), account.getSystem());
    			List<String> dadesList = new LinkedList<String>();
    			for (RoleGrant grant: grants)
    			{
    				dadesList.add ( grant.getRoleName());
    			}
    			for (Group grup: serverService.getUserGroupsHierarchy(account.getName(), account.getSystem()))
    			{
    				dadesList.add(grup.getName());
    			}
    			obj = dadesList;
    		}
    		else if ("attributes".equals(attribute) || "accountAttributes".equals(attribute))
    			obj = account.getAttributes() == null ? new HashMap<String, Object>(): account.getAttributes();
    		else
    			return null;
    		
   			put (attribute, obj);
   			return obj;
		}
		catch (InternalErrorException e)
		{
			throw new RuntimeException (e);
		}
		catch (UnknownUserException e)
		{
			throw new RuntimeException (e);
		}
	}

	@Override
	public boolean equals (Object o)
	{
		if (o instanceof AccountExtensibleObject)
			return account.getId().equals (((AccountExtensibleObject) o).account.getId());
		else
			return false;
	}

	@Override
	public int hashCode ()
	{
		return account.getId().hashCode();
	}


}

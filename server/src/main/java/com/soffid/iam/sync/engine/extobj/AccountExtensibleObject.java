/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
				account = serverService.getAccountInfo(account.getName(), account.getSystem());
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
    		else if ("system".equals(attribute))
    			obj = account.getSystem();
    		else if ("accountDescription".equals(attribute))
    			obj = account.getDescription();
    		else if ("accountDisabled".equals(attribute))
    			obj = account.isDisabled();
    		else if ("lastUpdate".equals(attribute))
    			obj = account.getLastUpdated();
    		else if ("lastPasswordUpdate".equals(attribute))
    			obj = account.getLastPasswordSet();
    		else if ("passwordExpiration".equals(attribute))
    			obj = account.getPasswordExpiration();
    		else if ("attributes".equals(attribute))
    			obj = new HashMap<String, String>();
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
    			Collection<RoleGrant> grants = serverService.getAccountRoles(account.getName(), account.getSystem());
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
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
    			return account.getAttributes();
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

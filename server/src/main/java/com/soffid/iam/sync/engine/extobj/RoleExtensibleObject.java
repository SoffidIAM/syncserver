/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
/**
 * @author bubu
 *
 */
public class RoleExtensibleObject extends ExtensibleObject
{
	private Role role;
	private ServerService serverService;
	public RoleExtensibleObject (Role role, ServerService serverService)
	{
		super();
		this.role = role;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_ROLE.getValue());
	}
	
	
	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		try
		{
    		if (obj != null)
    			return obj;
    		
    		if ("roleId".equals(attribute))
    			obj = role.getId();
    		else if ("system".equals(attribute))
    			obj = role.getSystem();
    		else if ("name".equals(attribute))
    			obj = role.getName();
    		else if ("category".equals(attribute))
    			obj = role.getCategory();
    		else if ("application".equals(attribute))
    			obj = role.getInformationSystemName();
    		else if ("passwordProtected".equals(attribute))
    			obj = role.getPassword();
    		else if ("description".equals(attribute))
    			obj = role.getDescription();
    		else if ("ownedRoles".equals(attribute))
    		{
    			List<ExtensibleObject> ownedRoles = new LinkedList<ExtensibleObject>();
    			if (role.getOwnedRoles() != null)
    			{
        			for (RoleGrant grant: role.getOwnedRoles())
        			{
        				ownedRoles.add(new GrantExtensibleObject(grant, serverService));
        			}
    			}
    			obj = ownedRoles;
    		}
    		else if ("ownerRoles".equals(attribute))
    		{
    			List<ExtensibleObject> ownerRoles = new LinkedList<ExtensibleObject>();
    			if (role.getOwnerRoles() != null)
    			{
        			for (RoleGrant grant: role.getOwnerRoles())
        			{
        				ownerRoles.add(new GrantExtensibleObject(grant, serverService));
        			}
    			}
    			obj = ownerRoles;
    		}
    		else if ("ownerGroups".equals(attribute))
    		{
    			List<ExtensibleObject> ownerGroups = new LinkedList<ExtensibleObject>();
    			if (role.getOwnerGroups() != null)
    			{
        			for (Group group: role.getOwnerGroups())
        			{
        				ownerGroups.add(new GroupExtensibleObject(group, role.getSystem(), serverService));
        			}
    			}
    			obj = ownerGroups;
    		}
    		else if ("domain".equals(attribute))
    		{
    			if (role.getDomain().getExternalCode() == null)
    				return role.getDomain().getName();
    			else
    				return role.getDomain().getExternalCode();
    		}
    		else if ("grantedAccountNames".equals(attribute) || "allGrantedAccountNames".equals(attribute))
    		{
    			List<String> accounts = new LinkedList<String>();
    			if (role.getId() != null && role.getSystem() != null)
    			{
        			Collection<Account> userList = serverService.getRoleAccounts(role.getId(), role.getSystem());
        			for (Account acc: userList)
        				accounts.add (acc.getName());
    			}
    			obj = accounts;
    		}
    		else if ("grantedAccounts".equals(attribute)  || "allGrantedAccounts".equals(attribute))
    		{
    			List<ExtensibleObject> accounts = new LinkedList<ExtensibleObject>();
    			if (role.getId() != null && role.getSystem() != null)
    			{
        			Collection<Account> userList = serverService.getRoleAccounts(role.getId(), role.getSystem());
        			for (Account acc: userList)
        			{
        				User usuari = null;
        				try {
        					usuari = serverService.getUserInfo(acc.getName(), acc.getSystem());
        				} catch (UnknownUserException e) {
        				}
        				if (usuari == null)
        					accounts.add (new AccountExtensibleObject(acc, serverService));
        				else
        					accounts.add (new UserExtensibleObject(acc, usuari, serverService));
        			}
    			}
    			obj = accounts;
    		}
    		else if ("attributes".equals(attribute))
    			obj = role.getAttributes();
    		else
    			return null;

   			put (attribute, obj);
   			return obj;
			
		}
		catch (InternalErrorException e)
		{
			throw new RuntimeException (e);
		}
		catch (UnknownRoleException e)
		{
			throw new RuntimeException (e);
		}
	}

}

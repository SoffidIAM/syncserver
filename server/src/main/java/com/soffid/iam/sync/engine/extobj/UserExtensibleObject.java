/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserData;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
/**
 * @author bubu
 *
 */
public class UserExtensibleObject extends ExtensibleObject
{
	User usuari;
	ServerService serverService;
	private Account account;
	
	public UserExtensibleObject (Account account, User usuari, ServerService serverService)
	{
		super();
		this.account = account;
		this.usuari = usuari;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_USER.getValue());
		if (account.getId() == null && account.getName() != null && account.getSystem() != null)
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
    		else if ("active".equals(attribute))
    			obj = usuari.getActive();
    		else if ("mailAlias".equals(attribute))
    			obj = usuari.getMailAlias();
    		else if ("userName".equals(attribute))
    			obj = usuari.getUserName();
    		else if ("primaryGroup".equals(attribute))
    			obj = usuari.getPrimaryGroup();
    		else if ("comments".equals(attribute))
    			obj = usuari.getComments();
    		else if ("createdOn".equals(attribute))
    			obj = usuari.getCreatedDate();
    		else if ("modifiedOn".equals(attribute))
    			obj = usuari.getModifiedDate();
    		else if ("mailDomain".equals(attribute))
    			obj = usuari.getMailDomain();
    		else if ("fullName".equals(attribute))
    			obj = usuari.getFullName();
    		else if ("id".equals(attribute))
    			obj = usuari.getId();
    		else if ("multiSession".equals(attribute))
    			obj = usuari.getMultiSession();
    		else if ("firstName".equals(attribute))
    			obj = usuari.getFirstName();
    		else if ("shortName".equals(attribute))
    			obj = usuari.getShortName();
    		else if ("lastName".equals(attribute))
    			obj = usuari.getLastName();
    		else if ("lastName2".equals(attribute))
    			obj = usuari.getMiddleName();
    		else if ("mailServer".equals(attribute))
    			obj = usuari.getMailServer();
    		else if ("homeServer".equals(attribute))
    			obj = usuari.getHomeServer();
    		else if ("profileServer".equals(attribute))
    			obj = usuari.getProfileServer();
    		else if ("phone".equals(attribute))
    			obj = usuari.getPhoneNumber();
    		else if ("userType".equals(attribute))
    			obj = usuari.getUserType();
    		else if ("createdBy".equals(attribute))
    			obj = usuari.getCreatedByUser();
    		else if ("modifiedBy".equals(attribute))
    			obj = usuari.getModifiedByUser();
    		else if ("primaryGroupObject".equals(attribute))
    		{
    			Group group = null;
				try {
					group = serverService.getGroupInfo(usuari.getPrimaryGroup(), account.getSystem());
				} catch (UnknownGroupException e) {
				}
    			if (group == null)
    				obj = null;
    			else
    				obj = new GroupExtensibleObject(group, account.getSystem(), serverService);
    		}
    		else if ("secondaryGroups".equals(attribute))
    		{
    			Collection<Group> groups;
    			groups = serverService.getUserGroups(account.getName(), account.getSystem());
    			List<GroupExtensibleObject> list = new LinkedList<GroupExtensibleObject>();
    			for ( Group group: groups)
    			{
    				list.add(new GroupExtensibleObject(group, account.getSystem(), serverService));
    			}
    			obj = list;
    		} 
    		else if ("accountAttributes".equals(attribute))
    		{
    			return account.getAttributes();
    		}
    		else if ("userAttributes".equals(attribute))
    		{
    			Collection<UserData> dades = serverService.getUserData(usuari.getId());
    			Map<String, Object> dadesMap = new HashMap<String, Object>();
    			for (UserData dada: dades)
    			{
    				if (dada.getDateValue() != null)
        				dadesMap.put(dada.getAttribute(), dada.getDateValue().getTime());
    				else
    					dadesMap.put(dada.getAttribute(), dada.getValue());
    			}
    			obj = dadesMap;
    		}
    		else if ("attributes".equals(attribute))
    		{
    			Collection<UserData> dades = serverService.getUserData(usuari.getId());
    			Map<String, Object> dadesMap = new HashMap<String, Object>();
   				if (account.getAttributes() != null)
    				dadesMap.putAll(account.getAttributes());
    			for (UserData dada: dades)
    			{
    				if (dada.getDateValue() != null)
        				dadesMap.put(dada.getAttribute(), dada.getDateValue().getTime());
    				else
    					dadesMap.put(dada.getAttribute(), dada.getValue());
    			}
    			obj = dadesMap;
    		}
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
    			for (Group Group: serverService.getUserGroups(account.getName(), account.getSystem()))
    			{
    				dadesList.add(Group.getName());
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
    			for (Group Group: serverService.getUserGroupsHierarchy(account.getName(), account.getSystem()))
    			{
    				dadesList.add(Group.getName());
    			}
    			obj = dadesList;
    		}
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
		if (o instanceof UserExtensibleObject)
			return account.getId().equals (((UserExtensibleObject) o).account.getId());
		else
			return false;
	}

	@Override
	public int hashCode ()
	{
		return account.getId().hashCode();
	}


}

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
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;

/**
 * @author bubu
 *
 */
public class GroupExtensibleObject extends es.caib.seycon.ng.sync.intf.ExtensibleObject
{
	Group grup;
	ServerService serverService;
	private String dispatcher;
	public GroupExtensibleObject (Group grup, String dispatcher, ServerService serverService)
	{
		super();
		this.grup = grup;
		this.serverService = serverService;
		this.dispatcher = dispatcher;
		setObjectType(SoffidObjectType.OBJECT_GROUP.getValue());
	}
	
	
	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		try
		{
    		if (obj != null)
    			return obj;
    		
    		if ("groupId".equals(attribute))
    			obj = grup.getId();
    		else if ("name".equals(attribute))
    			obj = grup.getName();
    		else if ("description".equals(attribute))
    			obj = grup.getDescription();
    		else if ("parent".equals(attribute))
    			obj = grup.getParentGroup();
    		else if ("server".equals(attribute))
    			obj = grup.getDriveServerName();
    		else if ("disabled".equals(attribute))
    			obj = grup.getObsolete();
    		else if ("accountingGroup".equals(attribute))
    			obj = grup.getSection();
    		else if ("type".equals(attribute))
    			obj = grup.getType();
    		else if ("driveLetter".equals(attribute))
    			obj = grup.getDriveLetter();
    		else if ("users".equals(attribute))
    		{
    			List<UserExtensibleObject> userList = getGroupUsers(grup.getId());
    			obj = userList;
    		}
    		else if ("userNames".equals(attribute))
    		{
    			List<String> userList = getGroupUserNames(grup.getId());
    			obj = userList;
    		}
    		else if ("allUsers".equals(attribute))
    		{
    			List<UserExtensibleObject> userList = new LinkedList<UserExtensibleObject>(getAllGroupUsers(grup.getId()));
    			obj = userList;
    		}
    		else if ("allUserNames".equals(attribute))
    		{
    			List<String> userList = new LinkedList<String>(getAllGroupUserNames(grup.getId()));
    			obj = userList;
    		}
    		else if ("grantedRoles".equals(attribute))
    		{
    			Collection<RoleGrant> grants = serverService.getGroupExplicitRoles(grup.getId());
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
    			for (RoleGrant grant: grants)
    			{
    				if (grant.getSystem().equals(dispatcher))
    					dadesList.add ( new GrantExtensibleObject(grant, serverService));
    			}
    			obj = dadesList;
    		}
    		else if ("grantedRoleNames".equals(attribute))
    		{
    			Collection<RoleGrant> grants = serverService.getGroupExplicitRoles(grup.getId());
    			List<String> dadesList = new LinkedList<String>();
    			for (RoleGrant grant: grants)
    			{
    				if (grant.getSystem().equals(dispatcher))
    					dadesList.add ( grant.getRoleName());
    			}
    			obj = dadesList;
    		}
    		else if ("attributes".equals(attribute))
    			obj = grup.getAttributes();
    		else
    			return null;
   			put (attribute, obj);
   			return obj;
		}
		catch (InternalErrorException e)
		{
			throw new RuntimeException (e);
		}
		catch (UnknownGroupException e)
		{
			throw new RuntimeException (e);
		}
	}


	private List<String> getGroupUserNames (Long groupId) throws InternalErrorException,
					UnknownGroupException
	{
		Collection<User> users = serverService.getGroupUsers(groupId, true, dispatcher);
		List<String> userList = new LinkedList<String>();
		for (User user: users)
		{
			for (Account account: serverService.getUserAccounts(user.getId(), dispatcher))
			{
				userList.add ( account.getName());
			}
		}
		return userList;
	}


	private List<UserExtensibleObject> getGroupUsers (Long groupId) throws InternalErrorException,
					UnknownGroupException
	{
		Collection<User> users = serverService.getGroupUsers(groupId, true, dispatcher);
		List<UserExtensibleObject> userList = new LinkedList<UserExtensibleObject>();
		for (User user: users)
		{
			for (Account account: serverService.getUserAccounts(user.getId(), dispatcher))
			{
				userList.add ( new UserExtensibleObject(account, user, serverService));
			}
		}
		return userList;
	}

	private Set<UserExtensibleObject> getAllGroupUsers (Long groupId)
					throws InternalErrorException, UnknownGroupException
	{
		HashSet<UserExtensibleObject> users = new HashSet<UserExtensibleObject>();
		users.addAll(getGroupUsers(groupId));
		for (Group grup: serverService.getGroupChildren(groupId, dispatcher))
		{
			users.addAll(getAllGroupUsers(grup.getId()));
		}
		return users;
	}

	private Set<String> getAllGroupUserNames (Long groupId)
					throws InternalErrorException, UnknownGroupException
	{
		HashSet<String> users = new HashSet<String>();
		users.addAll(getGroupUserNames(groupId));
		for (Group grup: serverService.getGroupChildren(groupId, dispatcher))
		{
			users.addAll(getAllGroupUserNames(grup.getId()));
		}
		return users;
	}
}

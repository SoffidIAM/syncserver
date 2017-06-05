/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class GroupExtensibleObject extends ExtensibleObject
{
	Grup grup;
	ServerService serverService;
	private String dispatcher;
	public GroupExtensibleObject (Grup grup, String dispatcher, ServerService serverService)
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
    			obj = grup.getCodi();
    		else if ("description".equals(attribute))
    			obj = grup.getDescripcio();
    		else if ("parent".equals(attribute))
    			obj = grup.getCodiPare();
    		else if ("server".equals(attribute))
    			obj = grup.getNomServidorOfimatic();
    		else if ("disabled".equals(attribute))
    			obj = grup.getObsolet();
    		else if ("accountingGroup".equals(attribute))
    			obj = grup.getSeccioPressupostaria();
    		else if ("type".equals(attribute))
    			obj = grup.getTipus();
    		else if ("driveLetter".equals(attribute))
    			obj = grup.getUnitatOfimatica();
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
    			Collection<RolGrant> grants = serverService.getGroupExplicitRoles(grup.getId());
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
    			for (RolGrant grant: grants)
    			{
    				if (grant.getDispatcher().equals(dispatcher))
    					dadesList.add ( new GrantExtensibleObject(grant, serverService));
    			}
    			obj = dadesList;
    		}
    		else if ("grantedRoleNames".equals(attribute))
    		{
    			Collection<RolGrant> grants = serverService.getGroupExplicitRoles(grup.getId());
    			List<String> dadesList = new LinkedList<String>();
    			for (RolGrant grant: grants)
    			{
    				if (grant.getDispatcher().equals(dispatcher))
    					dadesList.add ( grant.getRolName());
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
		Collection<Usuari> users = serverService.getGroupUsers(groupId, true, dispatcher);
		List<String> userList = new LinkedList<String>();
		for (Usuari user: users)
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
		Collection<Usuari> users = serverService.getGroupUsers(groupId, true, dispatcher);
		List<UserExtensibleObject> userList = new LinkedList<UserExtensibleObject>();
		for (Usuari user: users)
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
		for (Grup grup: serverService.getGroupChildren(groupId, dispatcher))
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
		for (Grup grup: serverService.getGroupChildren(groupId, dispatcher))
		{
			users.addAll(getAllGroupUserNames(grup.getId()));
		}
		return users;
	}
}

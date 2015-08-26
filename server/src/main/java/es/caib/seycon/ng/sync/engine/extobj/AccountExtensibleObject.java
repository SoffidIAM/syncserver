/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.HashMap;

import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class AccountExtensibleObject extends com.soffid.iam.sync.engine.extobj.AccountExtensibleObject
{
	ServerService serverService;
	private Account account;
	
	public AccountExtensibleObject (Account account, ServerService serverService)
	{
		super( com.soffid.iam.api.Account.toAccount(account), InterfaceWrapper.getServerService (serverService));

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
    			obj = account.getDispatcher();
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
    		else if ("attributes".equals(attribute) || "accountAttributes".equals(attribute))
    			return account.getAttributes();
    		else if ("grantedRoles".equals(attribute))
    		{
    			Collection<RolGrant> grants = serverService.getAccountExplicitRoles(account.getName(), account.getDispatcher());
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
    			for (RolGrant grant: grants)
    			{
    				dadesList.add ( new GrantExtensibleObject(grant, serverService));
    			}
    			obj = dadesList;
    		}
    		else if ("allGrantedRoles".equals(attribute))
    		{
    			Collection<RolGrant> grants = serverService.getAccountRoles(account.getName(), account.getDispatcher());
    			List<GrantExtensibleObject> dadesList = new LinkedList<GrantExtensibleObject>();
    			for (RolGrant grant: grants)
    			{
    				dadesList.add ( new GrantExtensibleObject(grant, serverService));
    			}
    			obj = dadesList;
    		}
    		else if ("granted".equals(attribute))
    		{
    			Collection<RolGrant> grants = serverService.getAccountExplicitRoles(account.getName(), account.getDispatcher());
    			List<String> dadesList = new LinkedList<String>();
    			for (RolGrant grant: grants)
    			{
    				dadesList.add ( grant.getRolName());
    			}
    			for (Grup grup: serverService.getUserGroups(account.getName(), account.getDispatcher()))
    			{
    				dadesList.add(grup.getCodi());
    			}
    			obj = dadesList;
    		}
    		else if ("allGranted".equals(attribute))
    		{
    			Collection<RolGrant> grants = serverService.getAccountRoles(account.getName(), account.getDispatcher());
    			List<String> dadesList = new LinkedList<String>();
    			for (RolGrant grant: grants)
    			{
    				dadesList.add ( grant.getRolName());
    			}
    			for (Grup grup: serverService.getUserGroupsHierarchy(account.getName(), account.getDispatcher()))
    			{
    				dadesList.add(grup.getCodi());
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
>>>>>>> 8d3e4fd0e7a0e46b23ae904eb93c4a14b8ac119e
	}

}

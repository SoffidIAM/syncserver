/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.DadaUsuari;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;

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
				account = serverService.getAccountInfo(account.getName(), account.getDispatcher());
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

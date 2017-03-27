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
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
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
public class RoleExtensibleObject extends ExtensibleObject
{
	private Rol role;
	private ServerService serverService;
	public RoleExtensibleObject (Rol role, ServerService serverService)
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
    			obj = role.getBaseDeDades();
    		else if ("name".equals(attribute))
    			obj = role.getNom();
    		else if ("category".equals(attribute))
    			obj = role.getCategory();
    		else if ("application".equals(attribute))
    			obj = role.getCodiAplicacio();
    		else if ("passwordProtected".equals(attribute))
    			obj = role.getContrasenya();
    		else if ("description".equals(attribute))
    			obj = role.getDescripcio();
    		else if ("ownedRoles".equals(attribute))
    		{
    			List<ExtensibleObject> ownedRoles = new LinkedList<ExtensibleObject>();
    			if (role.getOwnedRoles() != null)
    			{
        			for (RolGrant grant: role.getOwnedRoles())
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
        			for (RolGrant grant: role.getOwnerRoles())
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
        			for (Grup group: role.getOwnerGroups())
        			{
        				ownerGroups.add(new GroupExtensibleObject(group, role.getBaseDeDades(), serverService));
        			}
    			}
    			obj = ownerGroups;
    		}
    		else if ("domain".equals(attribute))
    		{
    			if (role.getDomini().getCodiExtern() == null)
    				return role.getDomini().getNom();
    			else
    				return role.getDomini().getCodiExtern();
    		}
    		else if ("grantedAccountNames".equals(attribute) || "allGrantedAccountNames".equals(attribute))
    		{
    			List<String> accounts = new LinkedList<String>();
    			if (role.getId() != null && role.getBaseDeDades() != null)
    			{
        			Collection<Account> userList = serverService.getRoleAccounts(role.getId(), role.getBaseDeDades());
        			for (Account acc: userList)
        				accounts.add (acc.getName());
    			}
    			obj = accounts;
    		}
    		else if ("grantedAccounts".equals(attribute)  || "allGrantedAccounts".equals(attribute))
    		{
    			List<ExtensibleObject> accounts = new LinkedList<ExtensibleObject>();
    			if (role.getId() != null && role.getBaseDeDades() != null)
    			{
        			Collection<Account> userList = serverService.getRoleAccounts(role.getId(), role.getBaseDeDades());
        			for (Account acc: userList)
        			{
        				Usuari usuari = null;
						try {
							usuari = serverService.getUserInfo(acc.getName(), acc.getDispatcher());
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

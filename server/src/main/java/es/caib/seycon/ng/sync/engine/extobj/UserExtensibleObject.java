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
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class UserExtensibleObject extends ExtensibleObject
{
	Usuari usuari;
	ServerService serverService;
	private Account account;
	
	public UserExtensibleObject (Account account, Usuari usuari, ServerService serverService)
	{
		super();
		this.account = account;
		this.usuari = usuari;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_USER.getValue());
		if (account.getId() == null && account.getName() != null && account.getName().trim().length() > 0 &&
				account.getDispatcher() != null && account.getDispatcher().trim().length() > 0 )
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
    		
    		if ("accountId".equals(attribute) && account != null)
    			obj = account.getId();
    		else if ("accountName".equals(attribute)  && account != null)
    			obj = account.getName();
    		else if ("system".equals(attribute)  && account != null)
    			obj = account.getDispatcher();
    		else if ("accountDescription".equals(attribute)  && account != null)
    			obj = account.getDescription();
    		else if ("accountDisabled".equals(attribute)  && account != null)
    			obj = account.isDisabled();
    		else if ("active".equals(attribute))
    			obj = usuari.getActiu();
    		else if ("mailAlias".equals(attribute))
    			obj = usuari.getAliesCorreu();
    		else if ("userName".equals(attribute))
    			obj = usuari.getCodi();
    		else if ("primaryGroup".equals(attribute))
    			obj = usuari.getCodiGrupPrimari();
    		else if ("comments".equals(attribute))
    			obj = usuari.getComentari();
    		else if ("createdOn".equals(attribute))
    			obj = usuari.getDataCreacioUsuari();
    		else if ("modifiedOn".equals(attribute))
    			obj = usuari.getDataDarreraModificacioUsuari();
    		else if ("mailDomain".equals(attribute))
    			obj = usuari.getDominiCorreu();
    		else if ("fullName".equals(attribute))
    			obj = usuari.getFullName();
    		else if ("id".equals(attribute))
    			obj = usuari.getId();
    		else if ("multiSession".equals(attribute))
    			obj = usuari.getMultiSessio();
    		else if ("firstName".equals(attribute))
    			obj = usuari.getNom();
    		else if ("shortName".equals(attribute))
    			obj = usuari.getNomCurt();
    		else if ("lastName".equals(attribute))
    			obj = usuari.getPrimerLlinatge();
    		else if ("lastName2".equals(attribute))
    			obj = usuari.getSegonLlinatge();
    		else if ("mailServer".equals(attribute))
    			obj = usuari.getServidorCorreu();
    		else if ("homeServer".equals(attribute))
    			obj = usuari.getServidorHome();
    		else if ("profileServer".equals(attribute))
    			obj = usuari.getServidorPerfil();
    		else if ("phone".equals(attribute))
    			obj = usuari.getTelefon();
    		else if ("userType".equals(attribute))
    			obj = usuari.getTipusUsuari();
    		else if ("createdBy".equals(attribute))
    			obj = usuari.getUsuariCreacio();
    		else if ("modifiedBy".equals(attribute))
    			obj = usuari.getUsuariDarreraModificacio();
    		else if ("primaryGroupObject".equals(attribute))
    		{
    			Grup group = null;
				try {
					group = serverService.getGroupInfo(usuari.getCodiGrupPrimari(), account.getDispatcher());
				} catch (UnknownGroupException e) {
				}
    			if (group == null)
    				obj = null;
    			else
    				obj = new GroupExtensibleObject(group, account.getDispatcher(), serverService);
    		}
    		else if ("secondaryGroups".equals(attribute))
    		{
    			Collection<Grup> groups;
    			groups = serverService.getUserGroups(account.getName(), account.getDispatcher());
    			List<GroupExtensibleObject> list = new LinkedList<GroupExtensibleObject>();
    			for ( Grup group: groups)
    			{
    				list.add(new GroupExtensibleObject(group, account.getDispatcher(), serverService));
    			}
    			obj = list;
    		} 
    		else if ("accountAttributes".equals(attribute))
    		{
    			obj = account.getAttributes();
    		}
    		else if ("userAttributes".equals(attribute))
    		{
    			Collection<DadaUsuari> dades = serverService.getUserData(usuari.getId());
    			Map<String, Object> dadesMap = new HashMap<String, Object>();
    			for (DadaUsuari dada: dades)
    			{
    				if (dada.getValorDadaDate() != null)
        				dadesMap.put(dada.getCodiDada(), dada.getValorDadaDate().getTime());
    				else if (dada.getBlobDataValue() != null)
    					dadesMap.put(dada.getCodiDada(), dada.getBlobDataValue());
    				else
    					dadesMap.put(dada.getCodiDada(), dada.getValorDada());
    			}
    			obj = dadesMap;
    		}
    		else if ("attributes".equals(attribute))
    		{
    			Collection<DadaUsuari> dades = serverService.getUserData(usuari.getId());
    			Map<String, Object> dadesMap = new HashMap<String, Object>();
   				if ( account != null && account.getAttributes() != null )
    				dadesMap.putAll(account.getAttributes());
    			for (DadaUsuari dada: dades)
    			{
    				if (dada.getValorDadaDate() != null)
        				dadesMap.put(dada.getCodiDada(), dada.getValorDadaDate().getTime());
    				else if (dada.getBlobDataValue() != null)
    					dadesMap.put(dada.getCodiDada(), dada.getBlobDataValue());
    				else
    					dadesMap.put(dada.getCodiDada(), dada.getValorDada());
    			}
    			obj = dadesMap;
    		}
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

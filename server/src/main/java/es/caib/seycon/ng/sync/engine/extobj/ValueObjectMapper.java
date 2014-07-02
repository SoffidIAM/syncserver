/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.lang.reflect.Array;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.sync.intf.AuthoritativeChange;
import es.caib.seycon.ng.sync.intf.AuthoritativeChangeIdentifier;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObjects;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class ValueObjectMapper
{
	public Map<String, Object> generateAccountMap (Account account)
	{
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("id", account.getId());
		map.put("description", account.getDescription());
		map.put("system", account.getDispatcher());
		map.put("name", account.getName());
		map.put("type", account.getType());
		
		return map;
	}

	public Map<String, Object> generateGroupMap (Grup grup)
	{
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("id", grup.getId());
		map.put("name", grup.getCodi());
		map.put("parent", grup.getCodiPare());
		map.put("description", grup.getDescripcio());
		map.put("server", grup.getNomServidorOfimatic());
		map.put("disabled", grup.getObsolet());
		map.put("quota", grup.getQuota());
		map.put("accountingGroup", grup.getSeccioPressupostaria());
		map.put("type", grup.getTipus());
		map.put("driveLetter", grup.getUnitatOfimatica());
		return map;
	}

	public String[] toStringArray (Object obj[])
	{
		String values[] = new String[obj.length];
		for (int i= 0; i < values.length; i ++)
		{
			values[i] = toString(obj[i]);
		}
		return values;
	}
	public Object toSingleton (Object obj)
	{
		if (obj != null  && obj.getClass().isArray())
		{
			int len = Array.getLength(obj);
			if (len == 0)
				return null;
			else if (len > 1)
			{
				StringBuffer b = new StringBuffer();
				for (int i = 0; i < len; i ++)
				{
					if (i > 0) b.append (", ");
					Object e = Array.get(obj, i); 
					b.append (e == null? "null": e.toString());
				}
				throw new RuntimeException("Multi valued attribute not supported ["+b.toString()+"]");
				
			}
			else
				return Array.get(obj, 0);
		}
		else
			return obj;
	}
	
	public Calendar toCalendar (Object obj)
	{
		if (obj == null)
			return null;
		if (obj instanceof Calendar)
			return (Calendar) obj;
		if (obj instanceof Date)
		{
			Calendar c = Calendar.getInstance();
			c.setTime((Date)obj);
			return c;
		}
		throw new ClassCastException(Calendar.class.getName());
	}
	
	public Date toDate (Object obj)
	{
		if (obj == null)
			return null;
		if (obj instanceof Date)
			return (Date) obj;
		if (obj instanceof Calendar)
		{
			return ((Calendar) obj).getTime();
		}
		throw new ClassCastException(Calendar.class.getName());
	}
	
	public String toString (Object obj)
	{
		if (obj == null)
			return null;
		else
			return obj.toString();
	}

	public String toSingleString (Object obj)
	{
		return toString(toSingleton(obj));
	}
	
	public Long toLong (Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Long)
			return (Long) obj;
		else
			return Long.decode(obj.toString());
	}
	
	public Boolean toBoolean (Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Boolean)
			return (Boolean) obj;
		else
			return Boolean.valueOf(obj.toString());
	}
	
	
	public Usuari parseUsuari (ExtensibleObject object) throws InternalErrorException
	{
		Usuari usuari = null;
		if (object.getObjectType().equals(SoffidObjectType.OBJECT_USER.getValue()))
		{
			usuari = new Usuari();
			for (String attribute: object.getAttributes())
			{
				try 
				{
					Object value = toSingleton(object.getAttribute(attribute));
					if ("active".equals(attribute)) usuari.setActiu("true".equals(value));
					else if ("mailAlias".equals(attribute)) usuari.setAliesCorreu(toString (value));
					else if ("userName".equals(attribute)) usuari.setCodi(toString( value) );
					else if ("primaryGroup".equals(attribute)) usuari.setCodiGrupPrimari(toString( value));
					else if ("comments".equals(attribute)) usuari.setComentari (toString(value));
					else if ("createdOn".equals(attribute)) usuari.setDataCreacioUsuari(toCalendar(value));
					else if ("modifiedOn".equals(attribute)) usuari.setDataDarreraModificacioUsuari(toCalendar(value));
					else if ("mailDomain".equals(attribute)) usuari.setDominiCorreu(toString(value));
					else if ("fullName".equals(attribute)) usuari.setFullName(toString(value));
					else if ("id".equals(attribute)) usuari.setId(toLong(value));
					else if ("multiSession".equals(attribute)) usuari.setMultiSessio(toBoolean(value));
					else if ("firstName".equals(attribute)) usuari.setNom(toString(value));
					else if ("shortName".equals(attribute)) usuari.setNomCurt(toString(value));
					else if ("lastName".equals(attribute)) usuari.setPrimerLlinatge(toString(value));
					else if ("lastName2".equals(attribute)) usuari.setSegonLlinatge(toString(value));
					else if ("mailServer".equals(attribute)) usuari.setServidorCorreu(toString(value));
					else if ("homeServer".equals(attribute)) usuari.setServidorHome(toString(value));
					else if ("profileServer".equals(attribute)) usuari.setServidorPerfil(toString(value));
					else if ("phone".equals(attribute)) usuari.setTelefon(toString(value));
					else if ("userType".equals(attribute)) usuari.setTipusUsuari(toString(value));
					else if ("createdBy".equals(attribute)) usuari.setUsuariCreacio(toString(value));
					else if ("modifiedBy".equals(attribute)) usuari.setDataDarreraModificacioUsuari(toCalendar(value));
				} catch (Exception e ) {
					throw new InternalErrorException ("Error parsing attribute "+attribute, e);
				}
			}
		}
		return usuari;
	}
	
	public AuthoritativeChange parseAuthoritativeChange (ExtensibleObject object) throws InternalErrorException
	{
		AuthoritativeChange change = null;
		if (object.getObjectType().equals(SoffidObjectType.OBJECT_AUTHORITATIVE_CHANGE.getValue()))
		{
			change = new AuthoritativeChange ();
			Usuari usuari = change.getUser();
			if (usuari == null)
			{
				usuari = new Usuari();
				change.setUser(usuari);
			}
			AuthoritativeChangeIdentifier id = change.getId();
			if (id == null)
			{
				id = new AuthoritativeChangeIdentifier();
				change.setId(id);
			}
			for (String attribute: object.getAttributes())
			{
				try {
					Object value = object.getAttribute(attribute);
					if ("active".equals(attribute)) usuari.setActiu(toBoolean(toSingleton(value)));
					else if ("mailAlias".equals(attribute)) usuari.setAliesCorreu(toSingleString (value));
					else if ("userName".equals(attribute)) usuari.setCodi(toSingleString( value) );
					else if ("primaryGroup".equals(attribute)) usuari.setComentari(toSingleString( value));
					else if ("comments".equals(attribute)) usuari.setCodiGrupPrimari(toSingleString(value));
					else if ("createdOn".equals(attribute)) usuari.setDataCreacioUsuari(toCalendar(toSingleton(value)));
					else if ("modifiedOn".equals(attribute)) usuari.setDataDarreraModificacioUsuari(toCalendar(toSingleton(value)));
					else if ("mailDomain".equals(attribute)) usuari.setDominiCorreu(toSingleString(value));
					else if ("fullName".equals(attribute)) usuari.setFullName(toSingleString(value));
					else if ("id".equals(attribute)) usuari.setId(toLong(toSingleton(value)));
					else if ("multiSession".equals(attribute)) usuari.setMultiSessio(toBoolean(toSingleton(value)));
					else if ("firstName".equals(attribute)) usuari.setNom(toSingleString(value));
					else if ("shortName".equals(attribute)) usuari.setNomCurt(toSingleString(value));
					else if ("lastName".equals(attribute)) usuari.setPrimerLlinatge(toSingleString(value));
					else if ("lastName2".equals(attribute)) usuari.setSegonLlinatge(toSingleString(value));
					else if ("mailServer".equals(attribute)) usuari.setServidorCorreu(toSingleString(value));
					else if ("homeServer".equals(attribute)) usuari.setServidorHome(toSingleString(value));
					else if ("profileServer".equals(attribute)) usuari.setServidorPerfil(toSingleString(value));
					else if ("phone".equals(attribute)) usuari.setTelefon(toSingleString(value));
					else if ("userType".equals(attribute)) usuari.setTipusUsuari(toSingleString(value));
					else if ("createdBy".equals(attribute)) usuari.setUsuariCreacio(toSingleString(value));
					else if ("modifiedBy".equals(attribute)) usuari.setDataDarreraModificacioUsuari(toCalendar(toSingleton(value)));
					// Ignore account attributes
					else if ("accountDescription".equals(attribute)) ;
					else if ("accountId".equals(attribute)) ;
					else if ("system".equals(attribute)) ;
					else if ("accountName".equals(attribute)) ;
					else if ("secondaryGroups". equals(attribute))
					{
						change.setGroups(new HashSet<String>((Collection<String>) value));
					}
					else
					{
						if (change.getAttributes() == null)
							change.setAttributes(new HashMap<String, Object>());
						
						if ("changeId".equals(attribute)) id.setChangeId(toSingleton(value));
						else if ("changeDate".equals(attribute)) id.setDate(toDate(toSingleton(value)));
						else if ("employeeId". equals(attribute)) id.setChangeId(toSingleton(value));
						
						change.getAttributes().put(attribute, toString(toSingleton(value)));
					}
				} catch (Exception e ) {
					throw new InternalErrorException ("Error parsing attribute "+attribute, e);
				}
			}
		}
		return change;
	}

	public Account parseAccount (ExtensibleObject object) throws InternalErrorException
	{
		Account account = null;
		if (object.getObjectType().equals(SoffidObjectType.OBJECT_USER.getValue()) ||
			object.getObjectType().equals(SoffidObjectType.OBJECT_ACCOUNT.getValue()))
		{
			account = new Account();
			account.setDescription(toSingleString (object.getAttribute("accountDescription")));
			account.setId(toLong (toSingleton(object.getAttribute("accountId"))));
			account.setDispatcher(toString (toSingleton(object.getAttribute("system"))));
			account.setName(toString (toSingleton(object.getAttribute("accountName"))));
			if (object.getObjectType().equals(SoffidObjectType.OBJECT_USER.getValue()))
				account.setType(AccountType.SHARED);
			else
				account.setType(AccountType.USER);
		}
		return account;
	}

	public Grup parseGroup (ExtensibleObject object) throws InternalErrorException
	{
		Grup grup = null;
		if (object.getObjectType().equals("group"))
		{
			grup = parseGroupFromMap(object);
		}
		return grup;
	}

	private Grup parseGroupFromMap (Map<String,Object> object)
	{
		Grup grup;
		grup = new Grup();
		grup.setId(toLong(toSingleton(object.get("groupId"))));
		grup.setCodi(toSingleString(object.get("name")));
		grup.setCodiPare(toSingleString(object.get("parent")));
		grup.setDescripcio(toSingleString(object.get("description")));
		grup.setNomServidorOfimatic(toSingleString(object.get("server")));
		grup.setObsolet(toBoolean(toSingleton(object.get("disabled"))));
		grup.setQuota(toSingleString(object.get("quota")));
		grup.setSeccioPressupostaria(toSingleString(object.get("accountingGroup")));
		grup.setTipus(toSingleString(object.get("type")));
		grup.setUnitatOfimatica(toSingleString(object.get("driveLetter")));
		return grup;
	}
	
	public Rol parseRol (ExtensibleObject object) throws InternalErrorException
	{
		Rol rol = null;
		if (object.getObjectType().equals("role"))
		{
			rol = new Rol();
			rol.setId(toLong(object.getAttribute("roleId")));
			rol.setBaseDeDades(toSingleString(object.getAttribute("system")));
			rol.setCodiAplicacio(toSingleString(object.getAttribute("application")));
			rol.setContrasenya(toBoolean(toSingleton(object.getAttribute("passwordProtected"))));
			rol.setDefecte(toBoolean(toSingleton(object.getAttribute("default"))));
			rol.setDescripcio(toSingleString(object.getAttribute("description")));
			rol.setGestionableWF(toBoolean(toSingleton(object.getAttribute("wfmanaged"))));
			rol.setNom(toSingleString(object.getAttribute("name")));
			Collection ownedRolesMap = (Collection) object.getAttribute("ownedRoles");
			if (ownedRolesMap != null)
			{
				LinkedList<RolGrant> ownedRoles = new LinkedList<RolGrant>();
				for (Object ownedRoleMap: ownedRolesMap)
				{
					RolGrant grant = parseRolGrant((Map<String, Object>) ownedRoleMap);
					ownedRoles.add(grant);
				}
				rol.setOwnedRoles(ownedRoles);
			}
			Collection ownerRolesMap = (Collection) object.getAttribute("ownerRoles");
			if (ownerRolesMap != null)
			{
				LinkedList<RolGrant> ownerRoles = new LinkedList<RolGrant>();
				for (Object ownerRoleMap: ownerRolesMap)
				{
					RolGrant grant = parseRolGrant((Map<String, Object>) ownerRoleMap);
					ownerRoles.add (grant);
				}
				rol.setOwnerRoles(ownerRoles);
			}
			Collection ownerGroupsMap = (Collection) object.getAttribute("ownerGroupss");
			if (ownerRolesMap != null)
			{
				LinkedList<Grup> ownerGroups = new LinkedList<Grup>();
				for (Object ownerGroupMap: ownerGroupsMap)
				{
					Grup group = parseGroupFromMap((Map<String, Object>) ownerGroupMap);
					ownerGroups.add(group);
				}
				rol.setOwnerGroups(ownerGroups);
			}
		}
		return rol;
	}
	
	/**
	 * @param ownedRoleMap
	 * @return
	 */
	private RolGrant parseRolGrant (Map<String,Object> map)
	{
		RolGrant rg = new RolGrant();
		rg.setDispatcher(toSingleString(map.get("grantedRolSystem")));
		rg.setDomainValue(toSingleString(map.get("domainVaue")));
		rg.setId(toLong(toSingleton(map.get("id"))));
		rg.setIdRol(toLong(toSingleton(map.get("grantedRoleId"))));
		rg.setOwnerAccountName(toSingleString(map.get("ownerAccount")));
		rg.setOwnerDispatcher(toSingleString(map.get("ownerSystem")));
		rg.setOwnerGroup(toSingleString(map.get("ownerGroup")));
		rg.setOwnerRol(toLong(map.get("ownerRoleId")));
		rg.setOwnerRolName(toSingleString(map.get("ownerRoleName")));
		rg.setRolName(toSingleString(map.get("grantedRoleId")));
		rg.setUser(toSingleString(map.get("ownerUser")));
		return rg;
	}

	/**
	 * @param rol
	 * @param serverService
	 * @return
	 * @throws UnknownRoleException 
	 * @throws InternalErrorException 
	 */
	public Set<Object> generateRolGrants (Rol rol, ServerService serverService) throws InternalErrorException, UnknownRoleException
	{
		Set<Object> grants = new HashSet<Object>();
		for (Account account: serverService.getRoleAccounts(rol.getId(), rol.getBaseDeDades()))
		{
			grants.add( generateAccountMap(account));
		}
		return grants;
	}

	/**
	 * @param soffidObjects
	 * @return
	 */
	public RolGrant parseGrant (ExtensibleObjects soffidObjects)
	{
		RolGrant grant = null;
		for (ExtensibleObject object: soffidObjects.getObjects())
		{
			if (object.getObjectType().equals("grant"))
			{
				grant = parseGrant(object);
			}
		}
		return grant;
	}

	private RolGrant parseGrant (Map<String,Object> object)
	{
		RolGrant grant;
		grant = new RolGrant();
		
		grant.setId(toLong(toSingleton(object.get("id"))));
		grant.setDispatcher(toSingleString(object.get("grantedRoleSystem")));
		grant.setDomainValue(toSingleString(object.get("domainValue")));
		grant.setHasDomain(grant.getDomainValue() != null);
		grant.setIdRol(toLong(toSingleton(object.get("grantedRoleId"))));
		grant.setOwnerAccountName(toSingleString(object.get("ownerAccount")));
		grant.setOwnerDispatcher(toSingleString(object.get("ownerDispatcher")));
		grant.setOwnerGroup(toSingleString(object.get("ownerGroup")));
		grant.setOwnerRol(toLong(toSingleton(object.get("ownerRoleId"))));
		grant.setOwnerRolName(toSingleString(object.get("ownerRoleName")));
		grant.setRolName(toSingleString(object.get("grantedRoleName")));
		grant.setUser(toSingleString(object.get("user")));
		return grant;
	}

}

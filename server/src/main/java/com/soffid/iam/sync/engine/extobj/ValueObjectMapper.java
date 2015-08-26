/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.lang.reflect.Array;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Domain;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.intf.AuthoritativeChange;
import com.soffid.iam.sync.intf.AuthoritativeChangeIdentifier;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObjects;
import com.soffid.iam.util.DateParser;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.TipusDomini;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownRoleException;

/**
 * @author bubu
 *
 */
public class ValueObjectMapper
{
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

		Calendar c = DateParser.parseDate((obj.toString()));
		if (c == null)
			throw new ClassCastException(Calendar.class.getName());
		else
			return  c;
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
	
	
	
	public User parseUser (ExtensibleObject object) throws InternalErrorException
	{
		User usuari = null;
		if (object.getObjectType().equals(SoffidObjectType.OBJECT_USER.getValue()))
		{
			usuari = new User();
			for (String attribute: object.getAttributes())
			{
				try 
				{
					Object value = toSingleton(object.getAttribute(attribute));
					if ("active".equals(attribute)) usuari.setActive(value == null ? null: "true".equals(value.toString()));
					else if ("mailAlias".equals(attribute)) usuari.setMailAlias(toString (value));
					else if ("userName".equals(attribute)) usuari.setUserName(toString( value) );
					else if ("primaryGroup".equals(attribute)) usuari.setPrimaryGroup(toString( value));
					else if ("comments".equals(attribute)) usuari.setComments(toString(value));
					else if ("createdOn".equals(attribute)) usuari.setCreatedDate(toCalendar(value));
					else if ("modifiedOn".equals(attribute)) usuari.setModifiedDate(toCalendar(value));
					else if ("mailDomain".equals(attribute)) usuari.setMailDomain(toString(value));
					else if ("fullName".equals(attribute)) usuari.setFullName(toString(value));
					else if ("id".equals(attribute)) usuari.setId(toLong(value));
					else if ("multiSession".equals(attribute)) usuari.setMultiSession(toBoolean(value));
					else if ("firstName".equals(attribute)) usuari.setFirstName(toString(value));
					else if ("shortName".equals(attribute)) usuari.setShortName(toString(value));
					else if ("lastName".equals(attribute)) usuari.setLastName(toString(value));
					else if ("lastName2".equals(attribute)) usuari.setMiddleName(toString(value));
					else if ("mailServer".equals(attribute)) usuari.setMailServer(toString(value));
					else if ("homeServer".equals(attribute)) usuari.setHomeServer(toString(value));
					else if ("profileServer".equals(attribute)) usuari.setProfileServer(toString(value));
					else if ("phone".equals(attribute)) usuari.setPhoneNumber(toString(value));
					else if ("userType".equals(attribute)) usuari.setUserType(toString(value));
					else if ("createdBy".equals(attribute)) usuari.setCreatedByUser(toString(value));
					else if ("modifiedBy".equals(attribute)) usuari.setModifiedByUser(toString(value));
					else if ("attributes".equals(attribute) || "userAttributes".equals(attribute))
					{
						if (object.getAttribute(attribute) instanceof Map)
						{
							@SuppressWarnings("rawtypes")
							Map atts = (Map) object.getAttribute(attribute);
							if (atts.containsKey("NIF"))
								usuari.setNationalID((String) atts.get("NIF"));
							if (atts.containsKey("PHONE"))
								usuari.setPhoneNumber((String) atts.get("PHONE"));
						}
					}
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
			User usuari = change.getUser();
			if (usuari == null)
			{
				usuari = new User();
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
					if ("active".equals(attribute)) usuari.setActive(toBoolean(toSingleton(value)));
					else if ("mailAlias".equals(attribute)) usuari.setMailAlias(toSingleString (value));
					else if ("userName".equals(attribute)) usuari.setUserName(toSingleString( value) );
					else if ("primaryGroup".equals(attribute)) usuari.setComments(toSingleString( value));
					else if ("comments".equals(attribute)) usuari.setPrimaryGroup(toSingleString(value));
					else if ("createdOn".equals(attribute)) usuari.setCreatedDate(toCalendar(toSingleton(value)));
					else if ("modifiedOn".equals(attribute)) usuari.setModifiedDate(toCalendar(toSingleton(value)));
					else if ("mailDomain".equals(attribute)) usuari.setMailDomain(toSingleString(value));
					else if ("fullName".equals(attribute)) usuari.setFullName(toSingleString(value));
					else if ("id".equals(attribute)) usuari.setId(toLong(toSingleton(value)));
					else if ("multiSession".equals(attribute)) usuari.setMultiSession(toBoolean(toSingleton(value)));
					else if ("firstName".equals(attribute)) usuari.setFirstName(toSingleString(value));
					else if ("shortName".equals(attribute)) usuari.setShortName(toSingleString(value));
					else if ("lastName".equals(attribute)) usuari.setLastName(toSingleString(value));
					else if ("lastName2".equals(attribute)) usuari.setMiddleName(toSingleString(value));
					else if ("mailServer".equals(attribute)) usuari.setMailServer(toSingleString(value));
					else if ("homeServer".equals(attribute)) usuari.setHomeServer(toSingleString(value));
					else if ("profileServer".equals(attribute)) usuari.setProfileServer(toSingleString(value));
					else if ("phone".equals(attribute)) usuari.setPhoneNumber(toSingleString(value));
					else if ("userType".equals(attribute)) usuari.setUserType(toSingleString(value));
					else if ("createdBy".equals(attribute)) usuari.setCreatedByUser(toSingleString(value));
					else if ("modifiedBy".equals(attribute)) usuari.setModifiedByUser(toSingleString(value));
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
			account.setSystem(toString (toSingleton(object.getAttribute("system"))));
			account.setName(toString (toSingleton(object.getAttribute("accountName"))));
			if (object.getObjectType().equals(SoffidObjectType.OBJECT_USER.getValue()))
				account.setType(AccountType.SHARED);
			else
				account.setType(AccountType.USER);
			account.setLastUpdated(toCalendar (object.getAttribute("lastUpdate")));
			account.setLastPasswordSet(toCalendar (object.getAttribute("lastPasswordUpdate")));
			account.setPasswordExpiration(toCalendar (object.getAttribute("passwordExpiration")));
			Object map = object.getAttribute("accountAttributes");
			if (map != null && map instanceof Map)
			{
				account.setAttributes((Map<String, Object>) map);
			} else {
				map = object.getAttribute("attributes");
				if (map != null && map instanceof Map)
					account.setAttributes((Map<String, Object>) map);
			}
		}
		return account;
	}

	public Group parseGroup (ExtensibleObject object) throws InternalErrorException
	{
		Group grup = null;
		if (object.getObjectType().equals("group"))
		{
			grup = parseGroupFromMap(object);
		}
		return grup;
	}

	private Group parseGroupFromMap (Map<String,Object> object)
	{
		Group grup;
		grup = new Group();
		grup.setId(toLong(toSingleton(object.get("groupId"))));
		grup.setName(toSingleString(object.get("name")));
		grup.setParentGroup(toSingleString(object.get("parent")));
		grup.setDescription(toSingleString(object.get("description")));
		grup.setDriveServerName(toSingleString(object.get("server")));
		grup.setObsolete(toBoolean(toSingleton(object.get("disabled"))));
		grup.setQuota(toSingleString(object.get("quota")));
		grup.setSection(toSingleString(object.get("accountingGroup")));
		grup.setType(toSingleString(object.get("type")));
		grup.setDriveLetter(toSingleString(object.get("driveLetter")));
		return grup;
	}
	
	public Role parseRole (ExtensibleObject object) throws InternalErrorException
	{
		Role rol = null;
		if (object.getObjectType().equals("role"))
		{
			rol = new Role();
			rol.setId(toLong(object.getAttribute("roleId")));
			rol.setSystem(toSingleString(object.getAttribute("system")));
			rol.setInformationSystemName(toSingleString(object.getAttribute("application")));
			rol.setPassword(toBoolean(toSingleton(object.getAttribute("passwordProtected"))));
			rol.setEnableByDefault(toBoolean(toSingleton(object.getAttribute("default"))));
			rol.setDescription(toSingleString(object.getAttribute("description")));
			rol.setBpmEnforced(toBoolean(toSingleton(object.getAttribute("wfmanaged"))));
			rol.setName(toSingleString(object.getAttribute("name")));
			rol.setCategory(toSingleString(object.getAttribute("category")));
			String domain = toSingleString(object.getAttribute("domain"));
			Domain d = new Domain ();
			rol.setDomain(d);
			if (domain == null)
			{
				d.setName(TipusDomini.SENSE_DOMINI);
			}
			else if (domain.equals (TipusDomini.APLICACIONS) || 
					domain.equals(TipusDomini.GRUPS) || 
					domain.equals(TipusDomini.GRUPS_USUARI) ||
					domain.equals(TipusDomini.SENSE_DOMINI))
			{
				d.setName(domain);
			}
			else
			{
				d.setName(domain);
				d.setExternalCode(rol.getInformationSystemName());
			}
			Collection ownedRolesMap = (Collection) object.getAttribute("ownedRoles");
			if (ownedRolesMap != null)
			{
				LinkedList<RoleGrant> ownedRoles = new LinkedList<RoleGrant>();
				for (Object ownedRoleMap: ownedRolesMap)
				{
					RoleGrant grant = parseGrant((Map<String, Object>) ownedRoleMap);
					ownedRoles.add(grant);
				}
				rol.setOwnedRoles(ownedRoles);
			}
			Collection ownerRolesMap = (Collection) object.getAttribute("ownerRoles");
			if (ownerRolesMap != null)
			{
				LinkedList<RoleGrant> ownerRoles = new LinkedList<RoleGrant>();
				for (Object ownerRoleMap: ownerRolesMap)
				{
					RoleGrant grant = parseGrant((Map<String, Object>) ownerRoleMap);
					ownerRoles.add (grant);
				}
				rol.setOwnerRoles(ownerRoles);
			}
			Collection ownerGroupsMap = (Collection) object.getAttribute("ownerGroupss");
			if (ownerRolesMap != null)
			{
				LinkedList<Group> ownerGroups = new LinkedList<Group>();
				for (Object ownerGroupMap: ownerGroupsMap)
				{
					Group group = parseGroupFromMap((Map<String, Object>) ownerGroupMap);
					ownerGroups.add(group);
				}
				rol.setOwnerGroups(ownerGroups);
			}
		}
		return rol;
	}
	
	/**
	 * @param soffidObjects
	 * @return
	 */
	public RoleGrant parseGrant (ExtensibleObjects soffidObjects)
	{
		RoleGrant grant = null;
		for (ExtensibleObject object: soffidObjects.getObjects())
		{
			if (object.getObjectType().equals("grant"))
			{
				grant = parseGrant(object);
			}
		}
		return grant;
	}

	public RoleGrant parseGrant (Map<String,Object> map)
	{
		RoleGrant rg = new RoleGrant();
		rg.setSystem(toSingleString(map.get("grantedRolSystem")));
		rg.setDomainValue(toSingleString(map.get("domainVaue")));
		rg.setId(toLong(toSingleton(map.get("id"))));
		rg.setRoleId(toLong(toSingleton(map.get("grantedRoleId"))));
		rg.setOwnerAccountName(toSingleString(map.get("ownerAccount")));
		rg.setOwnerSystem(toSingleString(map.get("ownerSystem")));
		rg.setOwnerGroup(toSingleString(map.get("ownerGroup")));
		rg.setOwnerRole(toLong(map.get("ownerRoleId")));
		rg.setOwnerRoleName(toSingleString(map.get("ownerRoleName")));
		rg.setRoleName(toSingleString(map.get("grantedRoleId")));
		rg.setUser(toSingleString(map.get("ownerUser")));
		rg.setHasDomain(rg.getDomainValue() != null);
		return rg;
	}

}

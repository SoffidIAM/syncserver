package es.caib.seycon.ng.sync.engine.extobj;


import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import com.soffid.iam.api.RoleGrant;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.intf.AuthoritativeChange;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObjects;

public class ValueObjectMapper {
	public String[] toStringArray(Object[] obj) {
		return delegate.toStringArray(obj);
	}

	public Object toSingleton(Object obj) {
		return delegate.toSingleton(obj);
	}

	public Calendar toCalendar(Object obj) {
		return delegate.toCalendar(obj);
	}

	public Date toDate(Object obj) {
		return delegate.toDate(obj);
	}

	public String toString(Object obj) {
		return delegate.toString(obj);
	}

	public String toSingleString(Object obj) {
		return delegate.toSingleString(obj);
	}

	public Long toLong(Object obj) {
		return delegate.toLong(obj);
	}

	public Boolean toBoolean(Object obj) {
		return delegate.toBoolean(obj);
	}

	com.soffid.iam.sync.engine.extobj.ValueObjectMapper delegate = new com.soffid.iam.sync.engine.extobj.ValueObjectMapper();
	
	public Usuari parseUsuari (ExtensibleObject object) throws InternalErrorException
	{
		return Usuari.toUsuari(delegate.parseUser(object));
	}
	
	public AuthoritativeChange parseAuthoritativeChange (ExtensibleObject object) throws InternalErrorException
	{
		return AuthoritativeChange.toAuthoritativeChange( delegate.parseAuthoritativeChange(object));
	}

	public Account parseAccount (ExtensibleObject object) throws InternalErrorException
	{
		return Account.toAccount(delegate.parseAccount(object));
		
	}
	
	public Grup parseGroup (ExtensibleObject object) throws InternalErrorException
	{
		return Grup.toGrup( delegate.parseGroup(object));
		
	}
	
	public es.caib.seycon.ng.comu.Rol parseRol (ExtensibleObject object) throws InternalErrorException
	{
		return Rol.toRol(delegate.parseRole (object));
	}


	public RolGrant parseGrant (ExtensibleObjects object)
	{
		return RolGrant.toRolGrant(
				delegate.parseGrant(
						com.soffid.iam.sync.intf.ExtensibleObjects.toExtensibleObjects(object)));
	}

	public RolGrant parseGrant (Map<String,Object> map)
	{
		return RolGrant.toRolGrant(
				delegate.parseGrant(map));
	}
}

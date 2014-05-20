/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class GrantExtensibleObject extends ExtensibleObject
{
	RolGrant grant;
	
	ServerService serverService;

	public GrantExtensibleObject (RolGrant grant, ServerService serverService)
	{
		super();
		this.grant = grant;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_GRANT.getValue());
	}

	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		if (obj != null)
			return obj;
		
		if ("id".equals(attribute))
			obj = grant.getId();
		else if ("grantedRole".equals(attribute))
			obj = grant.getRolName();
		else if ("grantedRoleSystem".equals(attribute))
			obj = grant.getDispatcher();
		else if ("grantedRoleId".equals(attribute))
			obj = grant.getIdRol();
		else if ("domainValue".equals(attribute))
			obj = grant.getDomainValue();
		else if ("ownerAccount".equals(attribute))
			obj = grant.getOwnerAccountName();
		else if ("ownerSystem".equals(attribute))
			obj = grant.getOwnerDispatcher();
		else if ("ownerGroup".equals(attribute))
			obj = grant.getOwnerGroup();
		else if ("ownerRoleId".equals(attribute))
			obj = grant.getOwnerRol();
		else if ("ownerRoleName".equals(attribute))
			obj = grant.getOwnerRolName();
		else if ("ownerUser".equals(attribute))
			obj = grant.getUser();
		else
			return null;
		put (attribute, obj);
		return obj;
	}
}

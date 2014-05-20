/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class MembershipExtensibleObject extends ExtensibleObject
{
	Account account;
	Usuari user;
	Grup grup;
	
	ServerService serverService;

	public MembershipExtensibleObject (Account account, Usuari user, Grup grup, ServerService serverService)
	{
		super();
		this.grup = grup;
		this.account = account;
		this.user = user;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_GRANTED_GROUP.getValue());

	}

	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		if (obj != null)
			return obj;
		
		if ("user".equals(attribute))
			obj = new UserExtensibleObject(account, user, serverService);
		else if ("group".equals(attribute))
			obj = new GroupExtensibleObject(grup, account.getDispatcher(), serverService);
		else
			return null;
		put (attribute, obj);
		return obj;
		
	}
}

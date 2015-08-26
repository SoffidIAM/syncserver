/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;


import com.soffid.iam.api.Group;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.engine.InterfaceWrapper;

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
public class MembershipExtensibleObject extends com.soffid.iam.sync.engine.extobj.MembershipExtensibleObject
{
	public MembershipExtensibleObject (Account account, Usuari user, Grup grup, ServerService serverService)
	{
		super(com.soffid.iam.api.Account.toAccount(account),
				User.toUser(user),
				Group.toGroup(grup), InterfaceWrapper.getServerService(serverService));
	}
}

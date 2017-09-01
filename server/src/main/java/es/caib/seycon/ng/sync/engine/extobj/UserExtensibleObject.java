/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.api.User;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.sync.servei.ServerService;

public class UserExtensibleObject extends com.soffid.iam.sync.engine.extobj.UserExtensibleObject
{
	public UserExtensibleObject (Account account, Usuari usuari, ServerService serverService)
	{
		super(com.soffid.iam.api.Account.toAccount(account), User.toUser(usuari), InterfaceWrapper.getServerService(serverService));
	}
}

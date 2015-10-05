/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.HashMap;

import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class AccountExtensibleObject extends com.soffid.iam.sync.engine.extobj.AccountExtensibleObject
{
	ServerService serverService;
	private Account account;
	
	public AccountExtensibleObject (Account account, ServerService serverService)
	{
		super( com.soffid.iam.api.Account.toAccount(account), InterfaceWrapper.getServerService (serverService));

	}

}

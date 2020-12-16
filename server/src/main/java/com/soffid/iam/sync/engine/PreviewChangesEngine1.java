/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.intf.ReconcileMgr;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class PreviewChangesEngine1 extends PreviewChangesEngine
{

	private ReconcileMgr agent;

	public PreviewChangesEngine1(com.soffid.iam.api.System dispatcher, ReconcileMgr agent,
			PrintWriter out) {
		super (dispatcher, out);
		this.agent = agent;
	}

	@Override
	protected List<String[]> getAccountChanges(Account account) throws RemoteException, InternalErrorException {
		return agent.getAccountChangesToApply(account);
	}

	@Override
	protected List<String[]> getRoleChanges(Role role) throws RemoteException, InternalErrorException {
		return agent.getRoleChangesToApply(role);
	}


}

/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.List;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.sync.intf.ReconcileMgr2;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class ReconcileEngine2 extends ReconcileEngine
{
	int timeout = 10000; 
	private ReconcileMgr2 agent;

	public ReconcileEngine2(com.soffid.iam.api.System dispatcher, ReconcileMgr2 agent,
			PrintWriter out) {
		super (dispatcher, out);
		this.agent = agent;
		if (!"local".equals( dispatcher.getUrl()))
			timeout = 60000; // Remote servers require longer timeout
	}



	@Override
	protected Account getAccountInfo(String accountName) throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				return agent.getAccountInfo(accountName);
			} catch (Throwable e) {
				log.append("Error getting account info. Retrying in 10 seconds\n");
				reconnect();
			}
		}
		return agent.getAccountInfo(accountName);
	}



	public void reconnect() {
		try { 
			Thread.sleep(timeout);
			DispatcherHandlerImpl h = (DispatcherHandlerImpl) 
					ServiceLocator.instance().getTaskGenerator()
					.getDispatcher(dispatcher.getName());
			if (h != null)
			{
				try {
					agent = (ReconcileMgr2) h.connect(false, false);
				} catch (Exception e) {
					log.println("WARNING. Cannot reconnect: "+e.toString());
				}
				
			}
		} catch (InterruptedException e1) { 
		}catch (InternalErrorException e1) {
			log.println("WARNING. Cannot reconnect: "+e1.toString());
		}
	}


	@Override
	protected List<String> getAccountList() throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				return agent.getAccountsList();
			} catch (Throwable e) {
				log.append("Error getting accounts list. Retrying");
				reconnect();
			}
		}
		return agent.getAccountsList();
	}


	@Override
	protected Role getRoleFullInfo(String roleName) throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				return agent.getRoleFullInfo(roleName);
			} catch (Throwable e) {
				log.append("Error getting role info. Retrying\n");
				reconnect();
			}
		}
		return agent.getRoleFullInfo(roleName);
	}

	@Override
	protected List<String> getRoleList() throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				return agent.getRolesList();
			} catch (Throwable e) {
				log.append("Error getting roles list. Retrying\n");
				reconnect();
			}
		}
		return agent.getRolesList();
	}

	@Override
	protected List<RoleGrant> getAccountGrants(Account acc) throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				List<RoleGrant> grants = agent.getAccountGrants(acc.getName());
				for (RoleGrant grant: grants)
				{
					grant.setSystem(dispatcher.getName());
				}
				return grants;
			} catch (Throwable e) {
				log.append("Error getting account grants. Retrying\n");
				reconnect();
			}
		}
		return agent.getAccountGrants(acc.getName());
	}



	public ReconcileMgr2 getAgent() {
		return agent;
	}



	public void setAgent(ReconcileMgr2 agent) {
		this.agent = agent;
	}

}

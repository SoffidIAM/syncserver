/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.HostService;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.intf.ReconcileMgr;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class ReconcileEngine1 extends ReconcileEngine
{

	private ReconcileMgr agent;

	public ReconcileEngine1(com.soffid.iam.api.System dispatcher, ReconcileMgr agent,
			PrintWriter out) {
		super (dispatcher, out);
		this.agent = agent;
	}


	Account userToAccount(User u)
	{
		if (u == null)
			return null;
		Account acc = new Account();
		acc.setName(u.getUserName());
		acc.setDescription(u.getFullName());
		if (acc.getDescription() == null || acc.getDescription().trim().isEmpty())
			acc.setDescription(u.getFirstName()+" "+u.getLastName());
		acc.setDisabled( u.getActive() != null && u.getActive().booleanValue());
		return acc;
	}
	

	@Override
	protected Account getAccountInfo(String accountName) throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				return userToAccount( agent.getUserInfo(accountName) );
			} catch (Throwable e) {
				log.append("Error getting account info. Retrying in 10 seconds\n");
				try { Thread.sleep(10000); } catch (InterruptedException e1) { }
			}
		}
		return userToAccount( agent.getUserInfo(accountName) );
	}


	@Override
	protected List<String> getAccountList() throws RemoteException, InternalErrorException {
		for (int i = 0; i < 2; i++)
		{
			try {
				return agent.getAccountsList();
			} catch (Throwable e) {
				log.append("Error getting accounts list. Retrying");
				try { Thread.sleep(10000); } catch (InterruptedException e1) { }
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
				try { Thread.sleep(10000); } catch (InterruptedException e1) { }
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
				try { Thread.sleep(10000); } catch (InterruptedException e1) { }
			}
		}
		return agent.getRolesList();
	}

	@Override
	protected  List<RoleGrant> getAccountGrants(Account acc) throws RemoteException, InternalErrorException {
		for (int i = 0; i < 3; i++)
		{
			try {
				List<RoleGrant> grants = new LinkedList<RoleGrant>(); 
				for (Role role: agent.getAccountRoles(acc.getName()))
				{
					RoleGrant grant = new RoleGrant();
					grant.setRoleName(role.getName());
					grant.setOwnerAccountName(acc.getName());
					grant.setSystem(dispatcher.getName());
					grant.setOwnerSystem(dispatcher.getName());
					grants.add(grant);
				}
				return grants;
			} catch (RemoteException e) {
				if (i == 2)
					throw e;
			} catch (InternalErrorException e) {
				if (i == 2)
					throw e;
			}
		}
		return null;
	}


	@Override
	public List<HostService> getServicesList() throws InternalErrorException {
		return null;
	}
}

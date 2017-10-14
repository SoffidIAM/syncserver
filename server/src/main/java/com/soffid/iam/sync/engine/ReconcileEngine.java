/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Application;
import com.soffid.iam.api.Domain;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleAccount;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;
import com.soffid.iam.model.AccountEntityDao;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.ApplicationService;
import com.soffid.iam.service.SyncServerService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.sync.intf.ReconcileMgr;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.NeedsAccountNameException;
import es.caib.seycon.ng.exception.UnknownRoleException;

/**
 * @author bubu
 *
 */
public class ReconcileEngine
{

	private ReconcileMgr agent;
	private AccountService accountService;
	private ApplicationService appService;
	private com.soffid.iam.api.System dispatcher;
	private ServerService serverService;
	private UserDomainService dominiService;
	private UserService usuariService;

	/**
	 * @param dispatcher 
	 * @param agent
	 */
	public ReconcileEngine (com.soffid.iam.api.System dispatcher, ReconcileMgr agent)
	{
		this.agent = agent;
		this.dispatcher = dispatcher;
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getApplicationService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getUserDomainService();
		usuariService = ServiceLocator.instance().getUserService();
	}

	/**
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 * 
	 */
	public void reconcile () throws RemoteException, InternalErrorException
	{
		String passwordPolicy = guessPasswordPolicy ();
		for (String accountName: agent.getAccountsList())
		{
			Account acc = accountService.findAccount(accountName, dispatcher.getName());
			if (acc == null)
			{
				User usuari = agent.getUserInfo(accountName);
				if (usuari != null)
				{
					acc = new Account ();
					acc.setName(accountName);
					acc.setSystem(dispatcher.getName());
					if (usuari.getFullName() != null)
						acc.setDescription(usuari.getFullName());
					else if (usuari.getLastName() != null)
					{
						if (usuari.getFirstName() == null)
							acc.setDescription(usuari.getLastName());
						else
							acc.setDescription(usuari.getFirstName()+" "+usuari.getLastName());
					}
					else
						acc.setDescription("Autocreated account "+accountName);
					
					acc.setDisabled(false);
					acc.setLastUpdated(Calendar.getInstance());
					acc.setType(AccountType.IGNORED);
					acc.setPasswordPolicy(passwordPolicy);
					acc.setGrantedGroups(new LinkedList<Group>());
					acc.setGrantedRoles(new LinkedList<Role>());
					acc.setGrantedUsers(new LinkedList<User>());
					try {
						acc = accountService.createAccount(acc);
					} catch (AccountAlreadyExistsException e) {
						throw new InternalErrorException ("Unexpected exception", e);
					}
				}
			}
			
			if (acc != null && acc.getId() != null && (dispatcher.isReadOnly() || dispatcher.isAuthoritative() || AccountType.IGNORED.equals(acc.getType())))
				reconcileRoles (acc);
		}
		
		reconcileAllRoles();
	}

	/**
	 * @return
	 * @throws InternalErrorException 
	 */
	private String guessPasswordPolicy () throws InternalErrorException
	{
		String p = null;
		List<PasswordPolicy> policies = new LinkedList<PasswordPolicy>(
						dominiService.findAllPasswordPolicyDomain(dispatcher.getPasswordsDomain()));
		if (policies.size () == 0)
			throw new InternalErrorException (
							String.format("There is no password policy defined for system %s", 
											dispatcher.getName()));
							
		Collections.sort(policies, new Comparator<PasswordPolicy>()
		{

			public int compare (PasswordPolicy o1, PasswordPolicy o2)
			{
				if (o1.getType().equals(o2.getType()))
					if ( o1.getMaximumPeriod() < o2.getMaximumPeriod())
						return -1;
					else if ( o1.getMaximumPeriod() > o2.getMaximumPeriod())
						return +1;
					else return 0;
				else if (o1.getType().equals("M"))
					return -1;
				else
					return 1;
			}			
		});
		
		return policies.get(0).getUserType();
	}

	/**
	 * @param acc
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 */
	private void reconcileRoles (Account acc) throws RemoteException, InternalErrorException
	{
		Collection<RoleGrant> grants = serverService.getAccountRoles(acc.getName(), acc.getSystem());
		for (Role role: agent.getAccountRoles(acc.getName()))
		{
			if (role.getSystem() == null)
				role.setSystem(dispatcher.getName());
			Role role2;
			try
			{
				role2 = serverService.getRoleInfo(role.getName(), role.getSystem());
				if (role2 == null)
					role2 = createRole (role);
				else if (role.getDescription() != null && ! role2.getDescription().equals(role.getDescription()))
				{
					role2.setDescription(role.getDescription());
					if (role2.getDescription().length() > 150)
						role2.setDescription(role2.getDescription().substring(0, 150));
					appService.update(role2);
				}
			}
			catch (UnknownRoleException e)
			{
				role2 = createRole (role);
			}
			// Look if this role is already granted
			boolean found = false;
			for (Iterator<RoleGrant> it = grants.iterator(); 
							! found && it.hasNext();)
			{
				RoleGrant grant = it.next();
				if (grant.getRoleId().equals (role2.getId()))
				{
					found = true;
					it.remove ();
				}
			}
			if (!found)
				grant (acc, role2);
		}

		// Now remove not present roles
		for (RoleGrant grant: grants)
		{
			if (grant.getOwnerGroup() == null &&
					grant.getOwnerRole() == null &&
					grant.getId() != null)
			{
				RoleAccount ra = new RoleAccount();
				ra.setAccountId(acc.getId());
				ra.setAccountSystem(acc.getSystem());
				ra.setAccountName(acc.getName());
				ra.setSystem(grant.getSystem());
				ra.setRoleName(grant.getRoleName());
				ra.setId(grant.getId());
				appService.delete(ra);
			}
		}
	}

	/**
	 * @param acc
	 * @param role2
	 * @throws InternalErrorException 
	 */
	private void grant (Account acc, Role role2) throws InternalErrorException
	{
		RoleAccount ra = new RoleAccount();
		ra.setAccountId(acc.getId());
		ra.setAccountSystem(acc.getSystem());
		ra.setAccountName(acc.getName());
		ra.setSystem(role2.getSystem());
		ra.setRoleName(role2.getName());
		ra.setInformationSystemName(role2.getInformationSystemName());
		
		appService.create(ra);
	}

	/**
	 * @param role
	 * @return
	 * @throws InternalErrorException 
	 */
	private Role createRole (Role role) throws InternalErrorException
	{
		if (role.getInformationSystemName() == null)
			role.setInformationSystemName(dispatcher.getName());
		Application app = appService.findApplicationByApplicationName(role.getInformationSystemName());
		if (app == null)
		{
			app = new Application();
			app.setName(role.getInformationSystemName());
			app.setDatabase(dispatcher.getName());
			app.setBpmEnforced(Boolean.FALSE);
			app.setDescription(dispatcher.getDescription());
			app = appService.create(app);
		}
		if (role.getPassword() == null)
			role.setPassword(Boolean.FALSE);
		
		if (role.getEnableByDefault() == null)
			role.setEnableByDefault(Boolean.FALSE);
		
		if (role.getDescription() == null)
			role.setDescription("Autogenerated role "+role.getName());
		
		if (role.getOwnedRoles() == null)
			role.setOwnedRoles(new LinkedList<RoleGrant> ());
		
		if (role.getOwnerGroups() == null)
			role.setOwnerGroups(new LinkedList<Group>());
		
		if (role.getOwnerRoles() == null)
			role.setOwnerRoles(new LinkedList<RoleGrant>());
		
		if (role.getDomain() == null)
		{
			role.setDomain(new Domain());
		}
		
		role.getDomain().setExternalCode(role.getInformationSystemName());

		if (role.getDescription().length() > 150)
			role.setDescription(role.getDescription().substring(0, 150));
				
		return appService.create(role);
	}


	private void reconcileAllRoles() throws RemoteException, InternalErrorException {
		List<String> roles = agent.getRolesList();
		if (roles == null)
			return;
		
		for (String roleName: agent.getRolesList())
		{
			if (roleName != null)
			{
				Role existingRole =  null;
				try {
					existingRole = serverService.getRoleInfo(roleName, dispatcher.getName());
				} catch (UnknownRoleException e) {
				}
				if (existingRole == null)
				{
					Role r = agent.getRoleFullInfo(roleName);
					if (r != null)
						createRole(r);
				}
			}
		}
	}
}

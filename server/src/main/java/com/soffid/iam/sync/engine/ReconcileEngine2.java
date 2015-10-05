/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Application;
import com.soffid.iam.api.AttributeVisibilityEnum;
import com.soffid.iam.api.DataType;
import com.soffid.iam.api.Domain;
import com.soffid.iam.api.DomainValue;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleAccount;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserData;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.service.ApplicationService;
import com.soffid.iam.service.DomainService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.intf.ReconcileMgr2;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.NeedsAccountNameException;
import es.caib.seycon.ng.exception.UnknownRoleException;

/**
 * @author bubu
 *
 */
public class ReconcileEngine2
{

	private ReconcileMgr2 agent;
	private AccountService accountService;
	private AdditionalDataService dadesAddicionalsService;
	private ApplicationService appService;
	private com.soffid.iam.api.System dispatcher;
	private ServerService serverService;
	private UserDomainService dominiService;
	private UserService usuariService;
	private DomainService rolDomainService;
	private StringBuffer log;

	/**
	 * @param dispatcher 
	 * @param agent
	 */
	public ReconcileEngine2 (com.soffid.iam.api.System dispatcher, ReconcileMgr2 agent)
	{
		this.agent = agent;
		this.dispatcher = dispatcher;
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getApplicationService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getUserDomainService();
		usuariService = ServiceLocator.instance().getUserService();
		rolDomainService = ServiceLocator.instance().getDomainService();
		dadesAddicionalsService = ServiceLocator.instance().getAdditionalDataService();
		log = new StringBuffer();
	}

	public ReconcileEngine2(com.soffid.iam.api.System dispatcher, ReconcileMgr2 agent,
			StringBuffer result) {
		this.agent = agent;
		this.dispatcher = dispatcher;
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getApplicationService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getUserDomainService();
		usuariService = ServiceLocator.instance().getUserService();
		rolDomainService = ServiceLocator.instance().getDomainService();
		dadesAddicionalsService = ServiceLocator.instance().getAdditionalDataService();
		log = result;
	}

	/**
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 * 
	 */
	public void reconcile () throws RemoteException, InternalErrorException
	{
		String passwordPolicy = guessPasswordPolicy ();
		reconcileAllRoles ();
		List<String> accountsList;
		try {
			Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
			accountsList = agent.getAccountsList();
		} finally {
			Watchdog.instance().dontDisturb();
		}
		for (String accountName: accountsList)
		{
			Account acc = accountService.findAccount(accountName, dispatcher.getName());
			Account existingAccount;
			if (acc == null)
			{
				try {
					Watchdog.instance().interruptMe(dispatcher.getTimeout());
					existingAccount = agent.getAccountInfo(accountName);
				} finally {
					Watchdog.instance().dontDisturb();
				}
				if (existingAccount != null)
				{
					acc = new Account ();
					acc.setName(accountName);
					acc.setSystem(dispatcher.getName());
					if (existingAccount.getDescription() == null)
						acc.setDescription(accountName+" "+accountName);
					else
						acc.setDescription(existingAccount.getDescription());
					
					acc.setDisabled(false);
					acc.setLastUpdated(Calendar.getInstance());
					acc.setType(AccountType.IGNORED);
					acc.setPasswordPolicy(passwordPolicy);
					acc.setGrantedGroups(new LinkedList<Group>());
					acc.setGrantedRoles(new LinkedList<Role>());
					acc.setGrantedUsers(new LinkedList<User>());
					try {
						log.append ("Creating account ").append (accountName).append ('\n');
						acc = accountService.createAccount(acc);
					} catch (AccountAlreadyExistsException e) {
						throw new InternalErrorException ("Unexpected exception", e);
					}
				}
			} else {
				Watchdog.instance().interruptMe(dispatcher.getTimeout());
				try {
					existingAccount = agent.getAccountInfo(accountName);
				} finally {
					Watchdog.instance().dontDisturb();
				}
				if (existingAccount != null)
				{
					if (existingAccount.getDescription() != null && existingAccount.getDescription().trim().length() > 0)
						acc.setDescription(existingAccount.getDescription());
					if (existingAccount.getLastPasswordSet() != null)
						acc.setLastPasswordSet(existingAccount.getLastPasswordSet());
					if (existingAccount.getLastUpdated() != null)
						acc.setLastUpdated(existingAccount.getLastUpdated());
					if (existingAccount.getPasswordExpiration() != null)
						acc.setPasswordExpiration(acc.getPasswordExpiration());
					if (existingAccount.getAttributes() != null)
						acc.getAttributes().putAll(existingAccount.getAttributes());
					try {
						log.append ("Updating account ").append (accountName).append ('\n');
						accountService.updateAccount(acc);
					} catch (AccountAlreadyExistsException e) {
						throw new InternalErrorException ("Unexpected exception", e);
					}
				}
				
			}

			// Only reconcile grants on unmanaged accounts
			// or read only dispatchers
			if (acc != null && acc.getId() != null && (dispatcher.isReadOnly() || dispatcher.isAuthoritative() || AccountType.IGNORED.equals(acc.getType())))
			{
				reconcileAccountAttributes (acc, existingAccount);
				reconcileRoles (acc);
			}
		}
		
	}

	private void reconcileAccountAttributes(Account soffidAccount, Account systemAccount) throws InternalErrorException {
		List<UserData> soffidAttributes = accountService.getAccountAttributes(soffidAccount);
		if (systemAccount.getAttributes() != null)
		{
			for (String key: systemAccount.getAttributes().keySet())
			{
				Object value = systemAccount.getAttributes().get(key);
				UserData soffidValue = null;
				for (Iterator<UserData> it = soffidAttributes.iterator(); it.hasNext();)
				{
					UserData du = it.next();
					if (du.getAttribute().equals (key))
					{
						it.remove();
						soffidValue = du;
						break;
					}
				}
				if (value == null) // Remove attribute
				{
					if (soffidValue != null )
					{
						accountService.removeAccountAttribute(soffidValue);
					}
				} else if (soffidValue == null) // Create value
				{
					// Verify attribute exists
					DataType type = dadesAddicionalsService.findSystemDataType(soffidAccount.getSystem(), key);
					if (type == null)
					{
						type = new DataType();
						type.setSystemName(soffidAccount.getSystem());
						type.setOrder(0L);
						type.setCode(key);
						type.setLabel(key);
						type.setOperatorVisibility(AttributeVisibilityEnum.HIDDEN);
						type.setAdminVisibility(AttributeVisibilityEnum.HIDDEN);
						type.setUserVisibility(AttributeVisibilityEnum.HIDDEN);
						type.setType(value instanceof byte[] ? TypeEnumeration.BINARY_TYPE:
							value instanceof Date || value instanceof Calendar ? TypeEnumeration.DATE_TYPE:
							TypeEnumeration.STRING_TYPE);
					}
					soffidValue = new UserData();
					soffidValue.setAccountName(soffidAccount.getName());
					soffidValue.setSystemName(soffidAccount.getSystem());
					soffidValue.setAttribute(key);
					setDadaValue(soffidValue, value);
					accountService.createAccountAttribute(soffidValue);
				}
				else
				{
					setDadaValue(soffidValue, value);
					accountService.updateAccountAttribute(soffidValue);
				}
			}
		}
	}

	private void setDadaValue(UserData soffidValue, Object value) {
		if (value instanceof Date)
		{
			Calendar c = Calendar.getInstance();
			c.setTime((Date) value);
			soffidValue.setDateValue(c);
		} else if (value instanceof byte[])
		{
			soffidValue.setBlobDataValue((byte[]) value);
		}
		else if (value instanceof Calendar)
		{
			soffidValue.setDateValue((Calendar) value);
		}
		else 
		{
			soffidValue.setValue(value.toString());
		}
	}

	private void reconcileAllRoles() throws RemoteException, InternalErrorException {
		List<String> roles;
		Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
		try
		{
			roles = agent.getRolesList();
		} finally {
			Watchdog.instance().dontDisturb();
		}
		if (roles == null)
			return;
		
		for (String roleName: roles)
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
					Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
					Role r = null;
					try
					{
						r = agent.getRoleFullInfo(roleName);
					} finally {
						Watchdog.instance().dontDisturb();
					}
					if (r != null)
						createRole(r);
				} else {
					Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
					Role r;
					try
					{
						r = agent.getRoleFullInfo(roleName);
					} finally {
						Watchdog.instance().dontDisturb();
					}
					if (r != null)
					{
						updateRole (existingRole, r);
					}
				}
			}
		}
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
					if ( o1.getMaximumPeriod() == null)
						return o2.getMaximumPeriod() == null ? 0: +1;
					else if ( o1.getMaximumPeriod() < o2.getMaximumPeriod())
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
		
		return policies.get(0).getType();
	}

	/**
	 * @param acc
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 */
	private void reconcileRoles (Account acc) throws RemoteException, InternalErrorException
	{
		Collection<RoleGrant> grants = serverService.getAccountRoles(acc.getName(), acc.getSystem());
		List<RoleGrant> accountGrants;
		Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
		try
		{
			accountGrants = agent.getAccountGrants(acc.getName());
		} finally {
			Watchdog.instance().dontDisturb();
		}
		for (RoleGrant existingGrant: accountGrants)
		{
			if (existingGrant.getSystem() == null)
				existingGrant.setSystem(dispatcher.getName());
			if (existingGrant.getRoleName() == null)
				throw new InternalErrorException("Received grant to "+acc.getName()+" without role name");
			Role role2;
			try
			{
				role2 = serverService.getRoleInfo(existingGrant.getRoleName(), existingGrant.getSystem());
				if (role2 == null)
				{
					Watchdog.instance().interruptMe(dispatcher.getTimeout());
					try
					{
						role2 = agent.getRoleFullInfo(existingGrant.getRoleName());
					} finally {
						Watchdog.instance().dontDisturb();
					}
					if (role2 == null)
						throw new InternalErrorException("Unable to grab information about role "+existingGrant.getRoleName());
					role2.setSystem(dispatcher.getName());
					if (role2.getDescription() != null && role2.getDescription().length() > 150)
						role2.setDescription(role2.getDescription().substring(0, 150));
					role2 = createRole (role2);
				}
			}
			catch (UnknownRoleException e)
			{
				Watchdog.instance().interruptMe(dispatcher.getTimeout());
				try
				{
					role2 = agent.getRoleFullInfo(existingGrant.getRoleName());
				} finally {
					Watchdog.instance().dontDisturb();
				}
				role2 = createRole (role2);
			}
			// Look if this role is already granted
			boolean found = false;
			for (Iterator<RoleGrant> it = grants.iterator(); 
							! found && it.hasNext();)
			{
				RoleGrant grant = it.next();
				if (grant.getRoleId().equals (role2.getId()))
				{
					if (grant.getDomainValue() == null && existingGrant.getDomainValue() == null ||
							grant.getDomainValue() != null && grant.getDomainValue().equals(existingGrant.getDomainValue()))
					{
						found = true;
						it.remove ();
					}
				}
			}
			if (!found)
			{
				log.append ("Granting ").append (existingGrant.getRoleName());
				if (existingGrant.getDomainValue() != null && existingGrant.getDomainValue().trim().length() > 0)
					log.append (" [").append (existingGrant.getDomainValue()).append("]");
				log.append (" to ").append(acc.getName()).append('\n');
				grant (acc, existingGrant, role2);
			}
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
				if (grant.getDomainValue() != null)
				{
					ra.setDomainValue(new DomainValue());
					ra.getDomainValue().setValue(grant.getDomainValue());
				}
				log.append ("Revoking ").append (grant.getRoleName());
				if (grant.getDomainValue() != null && grant.getDomainValue().trim().length() > 0)
					log.append (" [").append (grant.getDomainValue()).append("]");
				log.append (" from ").append(acc.getName()).append('\n');
				appService.delete(ra);
			}
		}
	}

	/**
	 * @param acc
	 * @param grant
	 * @param role 
	 * @throws InternalErrorException 
	 */
	private void grant (Account acc, RoleGrant grant, Role role) throws InternalErrorException
	{
		if (grant.getDomainValue() != null && role.getDomain() != null && role.getDomain().getExternalCode() != null)
		{
			// Verify domain value exists
			DomainValue dv = rolDomainService.findApplicationDomainValueByDomainNameAndDomainApplicationNameAndValue(
					role.getDomain().getName(), role.getInformationSystemName(), grant.getDomainValue());
			if (dv == null)
			{
				dv = new DomainValue();
				dv.setExternalCodeDomain(role.getInformationSystemName());
				dv.setDescription("Autogenerated value for "+grant.getDomainValue());
				dv.setDomainName(role.getDomain().getName());
				dv.setValue(grant.getDomainValue());
				rolDomainService.create(dv);
			}
			
		}
		RoleAccount ra = new RoleAccount();
		ra.setAccountId(acc.getId());
		ra.setAccountSystem(acc.getSystem());
		ra.setAccountName(acc.getName());
		ra.setSystem(grant.getSystem());
		ra.setRoleName(grant.getRoleName());
		ra.setInformationSystemName(role.getInformationSystemName());
		ra.setDomainValue(new DomainValue());
		ra.getDomainValue().setValue(grant.getDomainValue());
		ra.getDomainValue().setExternalCodeDomain(grant.getDomainValue());
		
		appService.create(ra);
	}

	/**
	 * @param role
	 * @return
	 * @throws InternalErrorException 
	 */
	private Role createRole (Role role) throws InternalErrorException
	{
		log.append ("Creating role "+role.getName());
		role.setSystem(dispatcher.getName());
		if (role.getInformationSystemName() == null)
			role.setInformationSystemName(dispatcher.getName());
		Application app = appService.findApplicationByApplicationName(role.getInformationSystemName());
		
		if (app == null)
		{
			app = new Application();
			app.setName(dispatcher.getName());
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

	private Role updateRole (Role soffidRole, Role systemRole) throws InternalErrorException
	{
		log.append ("Updating role "+soffidRole.getName()+"\n");
		
		if (systemRole.getInformationSystemName() != null)
			soffidRole.setInformationSystemName(systemRole.getInformationSystemName());

		if (systemRole.getPassword() != null)
			soffidRole.setPassword(systemRole.getPassword());
		
		if (systemRole.getEnableByDefault() != null)
			soffidRole.setEnableByDefault(systemRole.getEnableByDefault());
		
		if (systemRole.getBpmEnforced() != null)
			soffidRole.setBpmEnforced(systemRole.getBpmEnforced());

		if (systemRole.getCategory() != null)
			soffidRole.setCategory(systemRole.getCategory());

		if (systemRole.getDomain() != null)
			soffidRole.setDomain(systemRole.getDomain());

		if (systemRole.getDomain() != null)
			soffidRole.setOwnedRoles(systemRole.getOwnedRoles());

		if (systemRole.getOwnerRoles() != null)
			soffidRole.setOwnerRoles(systemRole.getOwnerRoles());

		if (systemRole.getOwnerGroups() != null)
			soffidRole.setOwnerGroups(systemRole.getOwnerGroups());

		if (systemRole.getDescription() != null)
			soffidRole.setDescription(systemRole.getDescription());

		if (soffidRole.getDescription().length() > 150)
			soffidRole.setDescription(soffidRole.getDescription().substring(0, 150));
				
		return appService.update(soffidRole);
	}

}

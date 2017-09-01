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
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserData;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.service.ApplicationService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.DomainService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.engine.extobj.AccountExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GrantExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.RoleExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ValueObjectMapper;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ReconcileMgr2;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.api.ReconcileTrigger;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.SoffidObjectTrigger;
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
	private Collection<ReconcileTrigger> triggers;
	private DispatcherService dispatcherService;
	private ObjectTranslator objectTranslator;
	private List<ReconcileTrigger> preDeleteGrant;
	private List<ReconcileTrigger> preInsertGrant;
	private List<ReconcileTrigger> postInsertGrant;
	private List<ReconcileTrigger> postDeleteGrant;
	private ValueObjectMapper vom;
	private List<ReconcileTrigger> postUpdateRole;
	private List<ReconcileTrigger> postInsertRole;
	private List<ReconcileTrigger> preInsertRole;
	private List<ReconcileTrigger> preUpdateRole;

	/**
	 * @param dispatcher 
	 * @param agent
	 */
	public ReconcileEngine2 (com.soffid.iam.api.System dispatcher, ReconcileMgr2 agent)
	{
		this.agent = agent;
		this.dispatcher = dispatcher;
		dispatcherService = ServiceLocator.instance().getDispatcherService();
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
		dispatcherService = ServiceLocator.instance().getDispatcherService();
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
		triggers = dispatcherService.findReconcileTriggersByDispatcher(dispatcher.getId());
		objectTranslator = new ObjectTranslator (dispatcher);
		vom = new ValueObjectMapper();
		
		reconcileAllRoles ();
		List<String> accountsList;
		try {
			Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
			accountsList = agent.getAccountsList();
		} finally {
			Watchdog.instance().dontDisturb();
		}

		List<ReconcileTrigger> preUpdate = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_UPDATE);
		List<ReconcileTrigger> preInsert = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_INSERT);
		List<ReconcileTrigger> postInsert = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_INSERT);
		List<ReconcileTrigger> postUpdate = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_UPDATE);

		preDeleteGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.PRE_UPDATE);
		preInsertGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.PRE_DELETE);
		postInsertGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.POST_INSERT);
		postDeleteGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.POST_DELETE);

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
					
					acc.setLastPasswordSet(existingAccount.getLastPasswordSet());
					acc.setLastUpdated(existingAccount.getLastUpdated() == null ?
							Calendar.getInstance() :
							existingAccount.getLastUpdated());
					acc.setLastLogin(existingAccount.getLastLogin());
					acc.setPasswordExpiration(existingAccount.getPasswordExpiration());

					if (existingAccount.getType() == null)
						acc.setType(AccountType.IGNORED);
					else
						acc.setType(existingAccount.getType());
					acc.setPasswordPolicy(passwordPolicy);
					acc.setGrantedGroups(new LinkedList<Group>());
					acc.setGrantedRoles(new LinkedList<Role>());
					acc.setGrantedUsers(new LinkedList<User>());
					try {
						boolean ok = true;
						
						if (! preInsert.isEmpty())
						{
							AccountExtensibleObject eo = new AccountExtensibleObject(acc, serverService);
							if (executeTriggers(preInsert, null, eo))
								acc = vom.parseAccount(eo);
							else
							{
								log.append ("Account "+acc.getName()+" not loaded due to pre-insert trigger failure\n");
								ok = false;
							}
						}
						if (ok) 
						{
							acc.setGrantedGroups(new LinkedList<Group>());
							acc.setGrantedRoles(new LinkedList<Role>());
							acc.setGrantedUsers(new LinkedList<User>());

							log.append ("Creating account ");
							log.append(acc.getName());
							log.append ('\n');
							acc = accountService.createAccount(acc);
							executeTriggers(postInsert, null, new AccountExtensibleObject(acc, serverService));
						}
					} catch (AccountAlreadyExistsException e) {
						throw new InternalErrorException ("Unexpected exception", e);
					}
					reconcileRoles (acc);
				}
			} else {
				Watchdog.instance().interruptMe(dispatcher.getTimeout());
				try {
					existingAccount = agent.getAccountInfo(accountName);
				} finally {
					Watchdog.instance().dontDisturb();
				}
				if (existingAccount != null && existingAccount.getName() != null && 
						existingAccount.getName().trim().length() > 0)
				{
					existingAccount.setSystem(dispatcher.getName());
					Account acc2 = new Account (acc);
					
					boolean anyChange = false;
					boolean isUnmanaged = acc != null && acc.getId() != null && 
							(dispatcher.isReadOnly() || dispatcher.isAuthoritative() || AccountType.IGNORED.equals(acc.getType()));
					
					if (! preUpdate.isEmpty())
					{
						boolean isManaged2 = isUnmanaged;
						AccountExtensibleObject eo = new AccountExtensibleObject(existingAccount, serverService);
						isUnmanaged = executeTriggers(preUpdate, new AccountExtensibleObject(acc, serverService), eo);
						if (isUnmanaged != isManaged2)
						{
							if (isUnmanaged)
								log.append ("Account "+acc.getName()+" is loaded due to pre-update trigger success\n");
							else
								log.append ("Account "+acc.getName()+" is not loaded due to pre-update trigger failure\n");
						}
						existingAccount = vom.parseAccount(eo);
					}

					if (! isUnmanaged &&
							existingAccount.getDescription() != null && 
							existingAccount.getDescription().trim().length() > 0 &&
							!existingAccount.getDescription().equals(acc.getDescription()))
					{
						anyChange = true;
						acc.setDescription(existingAccount.getDescription());
					}
					
					if (existingAccount.getLastPasswordSet() != null &&
							!existingAccount.getLastPasswordSet().equals(acc.getLastPasswordSet()))
					{
						anyChange = true;
						acc.setLastPasswordSet(existingAccount.getLastPasswordSet());
					}
					
					if (existingAccount.getLastLogin() != null &&
							!existingAccount.getLastLogin().equals(acc.getLastLogin()))
					{
						anyChange = true;
						acc.setLastLogin(existingAccount.getLastLogin());
					}

					if (existingAccount.getLastUpdated() != null &&
							!existingAccount.getLastUpdated().equals(acc.getLastUpdated()))
					{
						acc.setLastUpdated(existingAccount.getLastUpdated());
						anyChange = true;
					}
					
					if (existingAccount.getPasswordExpiration() != null &&
							!existingAccount.getPasswordExpiration().equals(acc.getPasswordExpiration()))
					{
						acc.setPasswordExpiration(existingAccount.getPasswordExpiration());
						anyChange = true;
					}
								
					
					if (isUnmanaged && existingAccount.getAttributes() != null)
					{
						for (String att: existingAccount.getAttributes().keySet())
						{
							Object v = existingAccount.getAttributes().get(att);
							Object v2 = acc.getAttributes().get(att);
							if (v != null &&
									!v.equals(v2))
							{
								acc.getAttributes().put(att, v);
								anyChange = true;
							}
						}
					}
					
					if (isUnmanaged && acc.isDisabled() != existingAccount.isDisabled())
					{
						acc.setDisabled(existingAccount.isDisabled());
						anyChange = true;
					}

					if (anyChange)
					{
						if (isUnmanaged)
							log.append ("Updating account ").append (accountName).append ('\n');
						else
							log.append ("Fetching password attributes for ").append (accountName).append ('\n');
						
						try {
							accountService.updateAccount(acc);
	
							executeTriggers(postUpdate, 
									new AccountExtensibleObject(acc2, serverService),
									new AccountExtensibleObject(acc, serverService));
						} catch (AccountAlreadyExistsException e) {
							throw new InternalErrorException ("Unexpected exception", e);
						}
						if (isUnmanaged)
							reconcileAccountAttributes (acc, existingAccount);
					}
					// Only reconcile grants on unmanaged accounts
					// or read only dispatchers
					if (isUnmanaged)
						reconcileRoles (acc);
				}
				
			}
		}
		
	}

	
	private List<ReconcileTrigger> findTriggers (SoffidObjectType type, SoffidObjectTrigger trigger)
	{
		List<ReconcileTrigger> r = new LinkedList<ReconcileTrigger> ();
		for (ReconcileTrigger t: triggers)
		{
			if (t.getObjectType().equals(type) &&
					t.getTrigger().equals(trigger))
				r.add (t);
		}
		
		return r;
	}
	
	
	private boolean executeTriggers (List<ReconcileTrigger> triggerList, ExtensibleObject old, ExtensibleObject newObject) throws InternalErrorException
	{
		ExtensibleObject eo = new ExtensibleObject ();
		eo.setAttribute("oldObject", old);
		eo.setAttribute("newObject", newObject);
		boolean ok = true;
		for (ReconcileTrigger t: triggerList)
		{
			if (!objectTranslator.evalExpression(eo, t.getScript()))
				ok = false;
		}
		
		return ok;
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
		
		preUpdateRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_UPDATE);
		preInsertRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_INSERT);
		postInsertRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_INSERT);
		postUpdateRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_UPDATE);
		
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
					{
						boolean ok = true;
						if (!preInsertRole.isEmpty())
						{
							RoleExtensibleObject eo = new RoleExtensibleObject(r, serverService);
							if (executeTriggers(preInsertRole, null, eo))
								r = vom.parseRole(eo);
							else
							{
								log.append ("Role "+r.getName()+" is not loaded due to pre-insert trigger failure\n");
								ok = false;
							}
						}
							
						if (ok)
						{
							r = createRole(r);
							executeTriggers(postInsertRole, null, new RoleExtensibleObject(r, serverService));
						}
					}
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
			// Remove duplicates
			for ( int i = 0; accountGrants != null && i < accountGrants.size(); )
			{	
				RoleGrant first = accountGrants.get(i);
				boolean match = false;
				for (int j = i + 1 ; !match && j < accountGrants.size(); j++)
				{
					RoleGrant second = accountGrants.get(j);
					if (first.getRoleName().equals (second.getRoleName()))
					{
						if (first.getDomainValue() == null || first.getDomainValue().trim().length() == 0 ||
								second.getDomainValue() == null || second.getDomainValue().trim().length() == 0 ||
								first.getDomainValue().equals(second.getDomainValue()))
						{
							match = true;
						}
					}
				}
				if (match)
					accountGrants.remove(i);
				else
					i++;
			}
		} finally {
			Watchdog.instance().dontDisturb();
		}
		for (RoleGrant existingGrant: accountGrants)
		{
			if (existingGrant.getSystem() == null)
				existingGrant.setSystem(dispatcher.getName());
			if (existingGrant.getRoleName() == null)
				throw new InternalErrorException("Received grant to "+acc.getName()+" without role name");
			
			boolean ok = true;
			if (!preInsertGrant.isEmpty())
			{
				GrantExtensibleObject eo = new GrantExtensibleObject(existingGrant, serverService);
				if (executeTriggers(preInsertGrant, null, eo))
					existingGrant = vom.parseGrant(eo);
				else
				{
					ok = false;
					log.append ("Grant of "+existingGrant.getRoleName()+" to "+existingGrant.getOwnerAccountName()+" is not loaded due to pre-insert trigger failure\n");
				}

			}
				
			if (ok)
			{
			
				Role role2;
				role2 = ensureRoleExist(existingGrant);
				// Look if this role is already granted
				if (role2 != null)
				{
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
			}
		}

		// Now remove not present roles
		for (RoleGrant grant: grants)
		{
			if (grant.getOwnerGroup() == null &&
					grant.getOwnerRole() == null &&
					grant.getId() != null)
			{
				boolean ok = true;
				
				if (!preDeleteGrant.isEmpty())
				{
					GrantExtensibleObject eo = new GrantExtensibleObject(grant, serverService);
					if (executeTriggers(preDeleteGrant, eo, null))
						grant = vom.parseGrant(eo);
					else
					{
						log.append ("Grant of "+grant.getRoleName()+" to "+grant.getOwnerAccountName()+" is not removed due to pre-delete trigger failure\n");
						ok = false;
					}
				}
				if (ok)
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
	}

	private Role ensureRoleExist(RoleGrant existingGrant)
			throws InternalErrorException, RemoteException {
		Role role2;
		try
		{
			role2 = serverService.getRoleInfo(existingGrant.getRoleName(), existingGrant.getSystem());
		}
		catch (UnknownRoleException e)
		{
			role2 = null;
		}
		
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
			if (role2.getDescription().length() > 150)
				role2.setDescription(role2.getDescription().substring(0, 150));
			boolean ok = true;

			if (!preInsertRole.isEmpty())
			{
				RoleExtensibleObject eo = new RoleExtensibleObject(role2, serverService);
				if (executeTriggers(preInsertRole, null, eo))
					role2 = vom.parseRole(eo);
				else
				{
					log.append ("Role "+role2.getName()+" is not loaded due to pre-insert trigger failure\n");
					role2 = null;
				}
			}
				
			if (role2 != null)
			{
				role2 = createRole(role2);
				executeTriggers(postInsertRole, null, new RoleExtensibleObject(role2, serverService));
			}
		}
		return role2;
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
		Role r = new Role(soffidRole);
		boolean anyChange = false;
		if (systemRole.getInformationSystemName() != null &&
				!systemRole.getInformationSystemName().equals(soffidRole.getInformationSystemName()))
		{
			soffidRole.setInformationSystemName(systemRole.getInformationSystemName());
			anyChange = true;
		}

		if (systemRole.getPassword() != null &&
				! systemRole.getPassword().equals(soffidRole.getPassword()))
		{
			soffidRole.setPassword(systemRole.getPassword());
			anyChange =true;
		}
		
		if (systemRole.getEnableByDefault() != null && 
				! systemRole.getEnableByDefault().equals(soffidRole.getEnableByDefault()))
		{
			anyChange = true;
			soffidRole.setEnableByDefault(systemRole.getEnableByDefault());
		}

		if (systemRole.getBpmEnforced() != null && 
				! systemRole.getBpmEnforced().equals(soffidRole.getBpmEnforced()))
		{
			soffidRole.setBpmEnforced(systemRole.getBpmEnforced());
			anyChange = true;
		}

		if (systemRole.getCategory() != null && !
				systemRole.getCategory().equals(soffidRole.getCategory()))
		{
			soffidRole.setCategory(systemRole.getCategory());
			anyChange = true;
		}

		if (systemRole.getDomain() != null &&
				systemRole.getDomain().getName() != null &&
				!systemRole.getDomain().getName().equals(soffidRole.getDomain().getName()))
		{
			soffidRole.setDomain(systemRole.getDomain());
			anyChange = true;
		}

		if (systemRole.getOwnedRoles() != null && !
				systemRole.getOwnedRoles().equals(systemRole.getOwnedRoles()))
		{
			soffidRole.setOwnedRoles(systemRole.getOwnedRoles());
			anyChange = true;
		}
		
		if (systemRole.getOwnerRoles() != null && !
				systemRole.getOwnerRoles().equals(soffidRole.getOwnerRoles()))
		{
			soffidRole.setOwnerRoles(systemRole.getOwnerRoles());
			anyChange = true;
		}

		if (systemRole.getOwnerGroups() != null && !
				systemRole.getOwnerGroups().equals(soffidRole.getOwnerGroups()))
		{
			soffidRole.setOwnerGroups(systemRole.getOwnerGroups());
			anyChange = true;
		}

		if (systemRole.getDescription() != null && 
				!systemRole.getDescription().equals(soffidRole.getDescription()))
		{
			soffidRole.setDescription(systemRole.getDescription());
			anyChange = true;
			if (soffidRole.getDescription().length() > 150)
				soffidRole.setDescription(soffidRole.getDescription().substring(0, 150));
		}

		if (systemRole.getAttributes() != null && 
				!systemRole.getAttributes().equals(soffidRole.getAttributes()))
		{
			soffidRole.setAttributes(systemRole.getAttributes());
			anyChange = true;
		}
				
		if (anyChange)
		{
			boolean ok = true;
			if (!preUpdateRole.isEmpty())
			{
				RoleExtensibleObject eo = new RoleExtensibleObject(soffidRole, serverService);
				if (executeTriggers(preUpdateRole, new RoleExtensibleObject(r, serverService), eo))
					soffidRole = vom.parseRole(eo);
				else
				{
					ok = false;
					log.append ("Role "+r.getName()+" is not loaded due to pre-update trigger failure\n");
				}
			}
				
			if (ok)
			{
				log.append ("Updating role "+soffidRole.getName()+"\n");
				soffidRole = appService.update(soffidRole);
				executeTriggers(postUpdateRole, 
						new RoleExtensibleObject(r, serverService),
						new RoleExtensibleObject(soffidRole, serverService));
			}
		}
		return soffidRole;
	}

}

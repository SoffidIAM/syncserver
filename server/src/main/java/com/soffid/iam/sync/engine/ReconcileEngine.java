/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.AccountStatus;
import com.soffid.iam.api.Application;
import com.soffid.iam.api.Domain;
import com.soffid.iam.api.DomainValue;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.HostService;
import com.soffid.iam.api.Issue;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.ReconcileTrigger;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleAccount;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserData;
import com.soffid.iam.reconcile.service.ReconcileService;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.service.ApplicationService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.DomainService;
import com.soffid.iam.service.NetworkDiscoveryService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.engine.extobj.AccountExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GrantExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.RoleExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ValueObjectMapper;
import com.soffid.iam.sync.engine.kerberos.KerberosManager;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.utils.ConfigurationCache;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.comu.TipusDomini;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownRoleException;

/**
 * @author bubu
 *
 */
public abstract class ReconcileEngine
{

	private AccountService accountService;
	private AdditionalDataService dadesAddicionalsService;
	private ApplicationService appService;
	protected com.soffid.iam.api.System dispatcher;
	private ServerService serverService;
	private UserDomainService dominiService;
	private UserService usuariService;
	private DomainService rolDomainService;
	protected PrintWriter log;
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
	Long reconcileProcessId;
	protected ReconcileService reconcileService;
	int errors = 0;
	
	List<String> roles = new LinkedList<String>();
	List<String> accountsList = new LinkedList<String>();
	private TaskGenerator taskGenerator;

	public ReconcileEngine(com.soffid.iam.api.System dispatcher, PrintWriter out) {
		this.dispatcher = dispatcher;
		dispatcherService = ServiceLocator.instance().getDispatcherService();
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getApplicationService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getUserDomainService();
		usuariService = ServiceLocator.instance().getUserService();
		rolDomainService = ServiceLocator.instance().getDomainService();
		dadesAddicionalsService = ServiceLocator.instance().getAdditionalDataService();
		reconcileService = ServiceLocator.instance().getReconcileService();
		taskGenerator = ServiceLocator.instance().getTaskGenerator();
		log = out;
		if (log == null)
			log = new PrintWriter( System.out );
	}

	/**
	 * @throws Exception 
	 * 
	 */
	public void reconcile () throws Exception
	{
		triggers = dispatcherService.findReconcileTriggersByDispatcher(dispatcher.getId());
		objectTranslator = new ObjectTranslator (dispatcher);
		vom = new ValueObjectMapper();
		
		String virtualTransactionId = taskGenerator.startVirtualSourceTransaction(!dispatcher.isGenerateTasksOnLoad());
		try {
			reconcileAllRoles ();
			reconcileAccounts();
			
			if (dispatcher.isFullReconciliation())
			{
				removeAccounts();
				removeRoles();
			}
			
			loadServices();

			if (errors > 0)
				throw new InternalErrorException("Found "+errors+" problems. Review log file");
		} finally {
			taskGenerator.finishVirtualSourceTransaction(virtualTransactionId);
		}
	}

	/**
	 * @throws Exception 
	 * 
	 */
	public void reconcileAccount (String accountName) throws Exception
	{
		log.println("Reconciling account "+accountName);
		triggers = dispatcherService.findReconcileTriggersByDispatcher(dispatcher.getId());
		objectTranslator = new ObjectTranslator (dispatcher);
		vom = new ValueObjectMapper();
		
		String virtualTransactionId = taskGenerator.startVirtualSourceTransaction(!dispatcher.isGenerateTasksOnLoad());
		try {
			String passwordPolicy = guessPasswordPolicy ();

			List<ReconcileTrigger> preUpdate = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_UPDATE);
			List<ReconcileTrigger> preInsert = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_INSERT);
			List<ReconcileTrigger> postInsert = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_INSERT);
			List<ReconcileTrigger> postUpdate = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_UPDATE);

			preUpdateRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_UPDATE);
			preInsertRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_INSERT);
			postInsertRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_INSERT);
			postUpdateRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_UPDATE);

			preDeleteGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.PRE_DELETE);
			preInsertGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.PRE_INSERT);
			postInsertGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.POST_INSERT);
			postDeleteGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.POST_DELETE);


			reconcileAccount(preInsert, postInsert, preUpdate, postUpdate, accountName, passwordPolicy);			
		} finally {
			taskGenerator.finishVirtualSourceTransaction(virtualTransactionId);
		}
	}

	private void removeRoles() throws InternalErrorException, Exception {
		HashSet<String> existingRoleNames = new HashSet<String> (
				appService.findRoleNames(dispatcher.getName()));
		if (roles == null)
			return;
		for (String role: roles)
			existingRoleNames.remove(role);
		
		List<ReconcileTrigger> preDelete = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_DELETE);
		List<ReconcileTrigger> postDelete = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_DELETE);
		for (String roleName: existingRoleNames)
		{
			Role role = appService.findRoleByNameAndSystem(roleName, dispatcher.getName());
			if (role != null)
			{
				boolean ok = true;
				removeRole(preDelete, postDelete, roleName, role, ok);
			}
		}		
	}

	protected void removeRole(List<ReconcileTrigger> preDelete, List<ReconcileTrigger> postDelete, String roleName,
			Role role, boolean ok) throws InternalErrorException {
		try {
			RoleExtensibleObject eo = new RoleExtensibleObject(role, serverService);
			if (! preDelete.isEmpty())
			{
				ok = executeTriggers(preDelete, eo, null);
			}
			if (ok)
			{
				log.append("Removing role "+roleName+"\n");
				try {
					appService.delete(role);
					executeTriggers(postDelete, eo, null);
				} catch (Exception e) {
					log.println(" Error: "+e.toString());
				}
			}
		} catch (Exception e) {
			errors ++;
			log.println("Error unloading role "+roleName);
			SoffidStackTrace.printStackTrace(e, log);
		}
	}

	private void removeAccounts() throws InternalErrorException {
		HashSet<String> existingAccountNames = new HashSet<String> (
				accountService.findAccountNames(dispatcher.getName()));
		for (String accountName: accountsList)
			existingAccountNames.remove(accountName);
		
		List<ReconcileTrigger> preDelete = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_DELETE);
		List<ReconcileTrigger> postDelete = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_DELETE);
		for (String accountName: existingAccountNames)
		{
			Account account = accountService.findAccount(accountName, dispatcher.getName());
			if (account != null && ! account.getStatus().equals(AccountStatus.REMOVED))
			{
				boolean ok = account.getType().equals(AccountType.IGNORED);
				AccountExtensibleObject eo = new AccountExtensibleObject(account, serverService);
				if (!preDelete.isEmpty())
					ok = executeTriggers(preDelete, eo, null);
				if (ok)
				{
					log.append("Removing account "+accountName+"\n");
					try {
						account.setStatus(AccountStatus.REMOVED);
						accountService.updateAccount2(account);
						executeTriggers(postDelete, eo, null);
					} catch (Exception e) {
						errors ++;
						log.println("Error removing account "+accountName);
						SoffidStackTrace.printStackTrace(e, log);
					}
				}
			}
		}
	}

	private void reconcileAccounts() throws RemoteException, InternalErrorException {
		String passwordPolicy = guessPasswordPolicy ();
		try {
			Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
			accountsList = getAccountList();
		} finally {
			Watchdog.instance().dontDisturb();
		}

		List<ReconcileTrigger> preUpdate = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_UPDATE);
		List<ReconcileTrigger> preInsert = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.PRE_INSERT);
		List<ReconcileTrigger> postInsert = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_INSERT);
		List<ReconcileTrigger> postUpdate = findTriggers(SoffidObjectType.OBJECT_ACCOUNT, SoffidObjectTrigger.POST_UPDATE);

		preDeleteGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.PRE_DELETE);
		preInsertGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.PRE_INSERT);
		postInsertGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.POST_INSERT);
		postDeleteGrant = findTriggers(SoffidObjectType.OBJECT_GRANT, SoffidObjectTrigger.POST_DELETE);

		for (String accountName: accountsList)
		{
			if (accountName != null && ! accountName.trim().isEmpty())
				reconcileAccount(preInsert, postInsert, preUpdate, postUpdate, accountName, passwordPolicy);
		}
	}

	protected void reconcileAccount(List<ReconcileTrigger> preInsert, List<ReconcileTrigger> postInsert,
			List<ReconcileTrigger> preUpdate, List<ReconcileTrigger> postUpdate, String accountName,
			String passwordPolicy) throws InternalErrorException, RemoteException {
		Account acc = accountService.findAccount(accountName, dispatcher.getName());
		Account existingAccount;
		if (acc == null)
		{
			try {
				Watchdog.instance().interruptMe(dispatcher.getTimeout());
				existingAccount = getAccountInfo(accountName);
			} finally {
				Watchdog.instance().dontDisturb();
			}
			if (existingAccount != null)
			{				
				loadAccount(passwordPolicy, preInsert, postInsert, accountName, existingAccount);
			}
		} else {
			Watchdog.instance().interruptMe(dispatcher.getTimeout());
			try {
				existingAccount = getAccountInfo(accountName);
			} finally {
				Watchdog.instance().dontDisturb();
			}
			if (existingAccount != null && existingAccount.getName() != null && 
					existingAccount.getName().trim().length() > 0)
			{
				updateAccount(preUpdate, postUpdate, accountName, acc, existingAccount);
			}
			
		}
	}

	protected abstract Account getAccountInfo(String accountName) throws RemoteException, InternalErrorException ;

	protected abstract List<String> getAccountList() throws RemoteException, InternalErrorException ;

	protected void updateAccount(List<ReconcileTrigger> preUpdate, List<ReconcileTrigger> postUpdate, String accountName,
			Account acc, Account existingAccount) throws InternalErrorException, RemoteException {

		boolean isUnmanaged = false;
		Account acc2 = new Account (acc);
		try {
			existingAccount.setSystem(dispatcher.getName());
			
			boolean anyChange = false;
			isUnmanaged = acc != null && acc.getId() != null && 
					(dispatcher.isReadOnly() || dispatcher.isAuthoritative() || AccountType.IGNORED.equals(acc.getType()));
			
			if (!dispatcher.isReadOnly() && accountService.isUpdatePending(acc)) {
				log.append ("Account "+acc.getName()+" is not loaded due to active synchronization task\n");			
				return;
			}
			
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
	
			if (isUnmanaged &&
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
			
			if (existingAccount.getPasswordPolicy() != null &&
					!existingAccount.getPasswordPolicy().equals(acc.getPasswordPolicy()))
			{
				acc.setPasswordPolicy(existingAccount.getPasswordPolicy());
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
				acc.setStatus(acc.isDisabled() ? AccountStatus.DISABLED: AccountStatus.ACTIVE);
				anyChange = true;
			}
	
			if (isUnmanaged && existingAccount.getType() != AccountType.IGNORED && existingAccount.getType() != null &&
					acc.getType() != existingAccount.getType())
			{
				acc.setType(existingAccount.getType());
				anyChange = true;
			}
	
			if (isUnmanaged && (acc.getOwnerUsers() == null || 
					!acc.getOwnerUsers().equals(existingAccount.getOwnerUsers()))
					&& existingAccount.getOwnerUsers() != null)
			{
				acc.setOwnerUsers(existingAccount.getOwnerUsers());
				anyChange = true;
			}
			if (isUnmanaged && (acc.getOwnerGroups() == null  ||
					!acc.getOwnerGroups().equals(existingAccount.getOwnerGroups()))  &&
					existingAccount.getOwnerGroups() != null)
			{
				acc.setOwnerGroups(existingAccount.getOwnerGroups());
				anyChange = true;
			}
			if (isUnmanaged && (acc.getOwnerRoles() == null || 
					!acc.getOwnerRoles().equals(existingAccount.getOwnerRoles()))  &&
					existingAccount.getOwnerRoles() != null)
			{
				acc.setOwnerRoles(existingAccount.getOwnerRoles());
				anyChange = true;
			}
	
			if (isUnmanaged && (acc.getManagerRoles() == null || !acc.getManagerUsers().equals(existingAccount.getManagerUsers()))  &&
					existingAccount.getManagerUsers() != null)
			{
				acc.setManagerUsers(existingAccount.getManagerUsers());
				anyChange = true;
			}
			if (isUnmanaged && (acc.getManagerGroups() == null || 
					!acc.getManagerGroups().equals(existingAccount.getManagerGroups()))  &&
					existingAccount.getManagerGroups() != null)
			{
				acc.setManagerGroups(existingAccount.getManagerGroups());
				anyChange = true;
			}
			if (isUnmanaged && (acc.getManagerRoles() == null || !acc.getManagerRoles().equals(existingAccount.getManagerRoles()))  &&
					existingAccount.getManagerRoles() != null)
			{
				acc.setManagerRoles(existingAccount.getManagerRoles());
				anyChange = true;
			}
	
			if (isUnmanaged && (acc.getGrantedUsers() == null || !acc.getGrantedUsers().equals(existingAccount.getGrantedUsers())) &&
					existingAccount.getGrantedUsers() != null)
			{
				acc.setGrantedUsers(existingAccount.getGrantedUsers());
				anyChange = true;
			}
			if (isUnmanaged && (acc.getGrantedGroups() == null || !acc.getGrantedGroups().equals(existingAccount.getGrantedGroups())) &&
					existingAccount.getGrantedGroups() != null)
			{
				acc.setGrantedGroups(existingAccount.getGrantedGroups());
				anyChange = true;
			}
			if (isUnmanaged && (acc.getGrantedRoles() == null || !acc.getGrantedRoles().equals(existingAccount.getGrantedRoles())) &&
					existingAccount.getGrantedRoles() != null)
			{
				acc.setGrantedRoles(existingAccount.getGrantedRoles());
				anyChange = true;
			}
			if (anyChange)
			{
				if (isUnmanaged)
					log.append ("Updating account ").append (accountName).append ('\n');
				else
					log.append ("Fetching password attributes for ").append (accountName).append ('\n');
				
				
				try {
					if (isUnmanaged)
					{
						if (existingAccount.getAttributes() != null)
						{
							for (String k: existingAccount.getAttributes().keySet())
							{
								acc.getAttributes().put(k, existingAccount.getAttributes().get(k));
							}
						}
						accountService.updateAccount2(acc);
					}
					else
					{
						accountService.updateAccount2(acc);
					}
	
					executeTriggers(postUpdate, 
							new AccountExtensibleObject(acc2, serverService),
							new AccountExtensibleObject(acc, serverService));
				} catch (AccountAlreadyExistsException e) {
					throw new InternalErrorException ("Unexpected exception", e);
				}
			}
		} catch (Exception e) {
			errors ++;
			log.println("Error updating account "+acc.toString());
			SoffidStackTrace.printStackTrace(e, log);
		}

		// Only reconcile grants on unmanaged accounts
		// or read only dispatchers
		if (isUnmanaged)
			reconcileRoles (acc);
	}

	protected void loadAccount(String passwordPolicy, List<ReconcileTrigger> preInsert, List<ReconcileTrigger> postInsert,
			String accountName, Account existingAccount) throws InternalErrorException, RemoteException {
		Account acc;
		acc = new Account ();
		acc.setName(accountName);
		acc.setSystem(dispatcher.getName());
		if (existingAccount.getDescription() == null || existingAccount.getDescription().trim().isEmpty())
			acc.setDescription(accountName);
		else
			acc.setDescription(existingAccount.getDescription());
		
		acc.setLastPasswordSet(existingAccount.getLastPasswordSet());
		acc.setLastUpdated(existingAccount.getLastUpdated() == null ?
				Calendar.getInstance() :
					existingAccount.getLastUpdated());
		acc.setLastLogin(existingAccount.getLastLogin());
		acc.setPasswordExpiration(existingAccount.getPasswordExpiration());
		acc.setDisabled(existingAccount.isDisabled());

		if (existingAccount.getType() == null)
			acc.setType(AccountType.IGNORED);
		else
			acc.setType(existingAccount.getType());
		if (acc.getPasswordPolicy() == null)
			acc.setPasswordPolicy(passwordPolicy);
		acc.setAttributes(existingAccount.getAttributes());
		if (existingAccount.getType() != AccountType.IGNORED && existingAccount.getType() != null )
		{
			acc.setType(existingAccount.getType());
		}
		if (existingAccount.getStatus() != null)
		{
			acc.setStatus(existingAccount.getStatus());
		}

		if (existingAccount.getOwnerUsers() != null)
		{
			acc.setOwnerUsers(existingAccount.getOwnerUsers());
		}
		if (existingAccount.getOwnerGroups() != null)
		{
			acc.setOwnerGroups(existingAccount.getOwnerGroups());
		}
		if (existingAccount.getOwnerRoles() != null)
		{
			acc.setOwnerRoles(existingAccount.getOwnerRoles());
		}
		
		if (existingAccount.getManagerUsers() != null)
		{
			acc.setManagerUsers(existingAccount.getManagerUsers());
		}
		if (existingAccount.getManagerGroups() != null)
		{
			acc.setManagerGroups(existingAccount.getManagerGroups());
		}
		if (existingAccount.getManagerRoles() != null)
		{
			acc.setManagerRoles(existingAccount.getManagerRoles());
		}
		
		if (existingAccount.getGrantedUsers() != null)
		{
			acc.setGrantedUsers(existingAccount.getGrantedUsers());
		}
		if (existingAccount.getGrantedGroups() != null)
		{
			acc.setGrantedGroups(existingAccount.getGrantedGroups());
		}
		if (existingAccount.getGrantedRoles() != null)
		{
			acc.setGrantedRoles(existingAccount.getGrantedRoles());
		}

		boolean ok = true;
		try {
	
			try {
				
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
					acc.setGrantedGroups(new LinkedList<String>());
					acc.setGrantedRoles(new LinkedList<String>());
					acc.setGrantedUsers(new LinkedList<String>());
	
					log.append ("Creating account ");
					log.append(acc.getName());
					log.append ('\n');
					acc.setAttributes(existingAccount.getAttributes());
					acc = accountService.createAccount2(acc);
					executeTriggers(postInsert, null, new AccountExtensibleObject(acc, serverService));
				}
			} catch (AccountAlreadyExistsException e) {
				throw new InternalErrorException ("Unexpected exception", e);
			}
		} catch (Exception e) {
			errors ++;
			log.println("Error loading account "+acc.toString());
			SoffidStackTrace.printStackTrace(e, log);
		}
		
		if (ok) {
			reconcileRoles (acc);
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
		Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
		try
		{
			roles = getRoleList();
		} finally {
			Watchdog.instance().dontDisturb();
		}
		
		preUpdateRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_UPDATE);
		preInsertRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.PRE_INSERT);
		postInsertRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_INSERT);
		postUpdateRole = findTriggers(SoffidObjectType.OBJECT_ROLE, SoffidObjectTrigger.POST_UPDATE);
		
		if (roles == null)
			return;

		// First, create new roles. later, update them
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
						r = getRoleFullInfo(roleName);
					} finally {
						Watchdog.instance().dontDisturb();
					}
					if (r != null)
					{
						r.setName(roleName);
						r.setOwnedRoles(new LinkedList<>());
						r.setOwnerRoles(new LinkedList<>());
						r.setOwnerGroups(new LinkedList<>());
						loadRole(r);
					}
				}
			}
		}
		for (String roleName: roles)
		{
			if (roleName != null)
			{
				Role existingRole =  null;
				try {
					existingRole = serverService.getRoleInfo(roleName, dispatcher.getName());
				} catch (UnknownRoleException e) {
				}
				// Do not try agais
				if (existingRole != null)
				{
					Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
					Role r;
					try
					{
						r = getRoleFullInfo(roleName);
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

	protected abstract Role getRoleFullInfo(String roleName) throws RemoteException, InternalErrorException ;

	protected abstract List<String> getRoleList() throws RemoteException, InternalErrorException ;

	protected void loadRole(Role r) throws InternalErrorException {
		boolean ok = true;
		try {
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
		} catch (Exception e) {
			errors ++;
			log.println("Error loading role "+r.toString());
			SoffidStackTrace.printStackTrace(e, log);
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
					else if ( o2.getMaximumPeriod() == null)
						return -1;
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
		
		return policies.get(0).getUserType();
	}

	/**
	 * @param acc
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 */
	protected void reconcileRoles (Account acc) throws RemoteException, InternalErrorException
	{
		log.println("Checking roles for account "+acc.getName());
		Collection<RoleGrant> grants = serverService.getAccountRoles(acc.getName(), acc.getSystem());
		List<RoleGrant> accountGrants;
		Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
		try
		{
			accountGrants = getAccountGrants(acc);
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
			log.println("Loading grant "+ existingGrant.getRoleName());
			if (existingGrant.getSystem() == null)
				existingGrant.setSystem(dispatcher.getName());
			if (existingGrant.getRoleName() == null)
				throw new InternalErrorException("Received grant to "+acc.getName()+" without role name");
			
			loadGrant(acc, existingGrant, grants);
		}

		// Now remove not present roles
		for (RoleGrant grant: grants)
		{
			if (grant.getOwnerGroup() == null &&
					grant.getOwnerRole() == null &&
					grant.getId() != null)
			{
				log.println("Removing grant "+ grant.getRoleName());
				unloadGrant(acc, grant);
			}
		}
	}

	protected abstract List<RoleGrant> getAccountGrants(Account acc) throws RemoteException, InternalErrorException ;

	protected void unloadGrant(Account acc, RoleGrant grant) throws InternalErrorException {
		boolean ok = true;
		
		try {
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
				if (!postDeleteGrant.isEmpty())
				{
					GrantExtensibleObject eo = new GrantExtensibleObject(grant, serverService);
					executeTriggers(postDeleteGrant, eo, null);
				}
			}
		} catch (Exception e) {
			errors ++;
			log.println("Error unloading grant "+grant.toString());
			SoffidStackTrace.printStackTrace(e, log);
		}
	}

	protected void loadGrant(Account acc, RoleGrant existingGrant, Collection<RoleGrant> grants)
			throws InternalErrorException, RemoteException {
		boolean ok = true;
		try {
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
					existingGrant.setRoleId(role2.getId());
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
						grants.add(existingGrant);
						if (!postInsertGrant.isEmpty())
						{
							GrantExtensibleObject eo = new GrantExtensibleObject(existingGrant, serverService);
							executeTriggers(postInsertGrant, null, eo);
						}
					}
				} else {
					log.print("Warning: Cannot find role to reconcile: "+existingGrant.getRoleName());
				}
			} else {
				log.print("Warning: Cannot find role to reconcile: "+existingGrant.getRoleName());
			}
		} catch (Exception e) {
			errors ++;
			log.println("Error loading grant "+existingGrant.toString());
			SoffidStackTrace.printStackTrace(e, log);
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
				role2 = getRoleFullInfo(existingGrant.getRoleName());
			} finally {
				Watchdog.instance().dontDisturb();
			}
			if (role2 == null)
			{
				log.println(" ERROR: Cannot find role "+existingGrant.getRoleName());
				return null;				
			}
			role2.setSystem(dispatcher.getName());
			if (role2.getName() == null)
				role2.setName(existingGrant.getRoleName());
			if (role2.getDescription() == null)
				role2.setDescription(role2.getName());
			if (role2.getDescription().length() > 150)
				role2.setDescription(role2.getDescription().substring(0, 150));

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
	 * @return 
	 * @throws InternalErrorException 
	 */
	protected RoleAccount grant (Account acc, RoleGrant grant, Role role) throws InternalErrorException
	{
		if (grant.getDomainValue() != null && role.getDomain() != null &&
				!role.getDomain().equals(TipusDomini.GROUPS) &&
				!role.getDomain().equals(TipusDomini.GRUPS_USUARI) &&
				!role.getDomain().equals(TipusDomini.APLICACIONS) &&
				!role.getDomain().equals(TipusDomini.APPLICATIONS) &&
				!role.getDomain().equals(TipusDomini.MEMBERSHIPS) &&
				!role.getDomain().equals(TipusDomini.GRUPS) )
		{
			// Verify domain value exists
			DomainValue dv = rolDomainService.findApplicationDomainValueByDomainNameAndDomainApplicationNameAndValue(
					role.getDomain(), role.getInformationSystemName(), grant.getDomainValue());
			if (dv == null)
			{
				dv = new DomainValue();
				dv.setExternalCodeDomain(role.getInformationSystemName());
				dv.setDescription("Autogenerated value for "+grant.getDomainValue());
				dv.setDomainName(role.getDomain());
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
		
		ra = appService.create(ra);
		Issue i = new Issue();
		i.setAccount(acc.getName()+"@"+acc.getDescription());
		i.setRoleAccount(ra);
		i.setSystem(acc.getSystem());
		i.setType("permissions-granted");
		ServiceLocator.instance().getIssueService().createInternalIssue(i);

		return ra;
	}

	/**
	 * @param role
	 * @return
	 * @throws InternalErrorException 
	 */
	protected Role createRole (Role role) throws InternalErrorException
	{
		log.append ("Creating role "+role.getName()).println();
		role.setSystem(dispatcher.getName());
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
		
		if (role.getDescription() == null || role.getDescription().trim().isEmpty())
			role.setDescription("Autogenerated role "+role.getName());
		
		boolean containsOwendRoles = role.getOwnedRoles() != null;

		// Load without dependencies. They will be loaded during the update role process
		role.setOwnedRoles(new LinkedList<RoleGrant> ());
		
		role.setOwnerGroups(new LinkedList<Group>());
		
		role.setOwnerRoles(new LinkedList<RoleGrant>());
		
		if (role.getDescription().length() > 150)
			role.setDescription(role.getDescription().substring(0, 150));
				
		return appService.create2(role);
	}

	protected Role updateRole (Role soffidRole, Role systemRole) throws InternalErrorException
	{
		try {
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
					systemRole.getDomain() != null &&
					!systemRole.getDomain().equals(soffidRole.getDomain()))
			{
				soffidRole.setDomain(systemRole.getDomain());
				anyChange = true;
			}
	
			if (systemRole.getOwnedRoles() != null && !
					systemRole.getOwnedRoles().equals(soffidRole.getOwnedRoles()))
			{
				soffidRole.setOwnedRoles(mergeOwnedRoles(soffidRole.getOwnedRoles(),systemRole.getOwnedRoles()));
				anyChange = true;
			}
			
			if (systemRole.getOwnerRoles() != null && !
					systemRole.getOwnerRoles().equals(soffidRole.getOwnerRoles()))
			{
				soffidRole.setOwnerRoles(mergeOwnerRoles(soffidRole.getOwnerRoles(), systemRole.getOwnerRoles()));
				anyChange = true;
			}
	
			if (systemRole.getOwnerGroups() != null && !
					systemRole.getOwnerGroups().equals(soffidRole.getOwnerGroups()))
			{
				soffidRole.setOwnerGroups(systemRole.getOwnerGroups());
				anyChange = true;
			}
	
			if (systemRole.getDescription() != null && 
					!systemRole.getDescription().trim().isEmpty() &&
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
				for (String att: systemRole.getAttributes().keySet())
				{
					Object v = systemRole.getAttributes().get(att);
					Object v2 = soffidRole.getAttributes().get(att);
					if (v != null &&
							!v.equals(v2))
					{
						soffidRole.getAttributes().put(att, v);
						anyChange = true;
					}
				}
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
					soffidRole = appService.update2(soffidRole);
					executeTriggers(postUpdateRole, 
							new RoleExtensibleObject(r, serverService),
							new RoleExtensibleObject(soffidRole, serverService));
				}
			}
		} catch (Exception e) {
			errors ++;
			log.println("Error loading role "+systemRole.toString());
			SoffidStackTrace.printStackTrace(e, log);
		}
		return soffidRole;
	}

	private Collection<RoleGrant> mergeOwnedRoles(Collection<RoleGrant> ownedRoles, Collection<RoleGrant> ownedRoles2) {
		if (ConfigurationCache.getProperty("soffid.reconcile.all.roles").contains(dispatcher.getName()))
			return ownedRoles2;
		
		List<RoleGrant> l = new LinkedList<>();
		l.addAll(ownedRoles2);
		for (RoleGrant grant: ownedRoles) {
			if (! grant.getSystem().equals(dispatcher.getName())) {
				boolean found = false;
				for (RoleGrant e: l) {
					if (e.getRoleName().equals(grant.getRoleName()) &&
							e.getSystem().equals(grant.getSystem()) &&
							(e.getDomainValue() == null ? grant.getDomainValue() == null: e.getDomainValue().equals(grant.getDomainValue())))
					{
						found = true;
						break;
					}
				}
				if (!found)
					l.add(grant);
			}
		}
		return l;
		
	}

	private Collection<RoleGrant> mergeOwnerRoles(Collection<RoleGrant> ownedRoles, Collection<RoleGrant> ownedRoles2) {
		if (ConfigurationCache.getProperty("soffid.reconcile.all.roles") != null &&
			ConfigurationCache.getProperty("soffid.reconcile.all.roles").contains(dispatcher.getName()))
			return ownedRoles2;

		List<RoleGrant> l = new LinkedList<>();
		l.addAll(ownedRoles2);
		for (RoleGrant grant: ownedRoles) {
			if (! grant.getOwnerSystem().equals(dispatcher.getName())) {
				boolean found = false;
				for (RoleGrant e: l) {
					if (e.getOwnerRoleName().equals(grant.getOwnerRoleName()) &&
							e.getOwnerSystem().equals(grant.getOwnerSystem()) &&
							(e.getDomainValue() == null ? grant.getOwnerRolDomainValue() == null: e.getOwnerRolDomainValue().equals(grant.getOwnerRolDomainValue())))
					{
						found = true;
						break;
					}
				}
				if (!found)
					l.add(grant);
			}
		}
		return l;
		
	}

	public abstract List<HostService> getServicesList() throws InternalErrorException, RemoteException;
	
	Map<String, String> map = null;
	public Map<String,String> getDomainToSystemMap() throws FileNotFoundException, InternalErrorException, IOException {
		if (map == null)
			map = new KerberosManager().getDomainsToSystemMap();
		return map;
	}
	
	public void loadServices() throws InternalErrorException, FileNotFoundException, IOException {
		NetworkDiscoveryService networkDiscoveryService;
		try {
			networkDiscoveryService = ServiceLocator.instance().getNetworkDiscoveryService();
		} catch (NoSuchMethodError e) {
			return ;
		}
		List<Host> hosts = networkDiscoveryService.findSystemHosts(dispatcher);
		if (! hosts.isEmpty()) {
			List<HostService> services = getServicesList();
			if (services != null) {
				log.println("Registering account protected services"); 
				for (Host host: hosts) {
					InetAddress addr = InetAddress.getByName(host.getIp());
					List<HostService> hostServices = new LinkedList<>();
					for (HostService hostService: services) {
						if (hostService.getHostName() == null)
							hostServices.add(hostService);
						else {
							for (InetAddress addr2: InetAddress.getAllByName(hostService.getHostName())) {
								if (addr2.equals(addr)) {
									hostServices.add(hostService);
									break;
								}
							}
						}
					}
					networkDiscoveryService.registerHostServices(host, dispatcher, hostServices, getDomainToSystemMap());
				}
			}
		}
	}

}

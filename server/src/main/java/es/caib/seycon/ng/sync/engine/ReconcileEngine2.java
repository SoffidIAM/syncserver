/**
 * 
 */
package es.caib.seycon.ng.sync.engine;

import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.api.AttributeVisibilityEnum;
import com.soffid.iam.api.ReconcileTrigger;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Aplicacio;
import es.caib.seycon.ng.comu.DadaUsuari;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Domini;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.PoliticaContrasenya;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolAccount;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.TipusDada;
import es.caib.seycon.ng.comu.TipusDomini;
import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.ValorDomini;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.NeedsAccountNameException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.AplicacioService;
import es.caib.seycon.ng.servei.DadesAddicionalsService;
import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.servei.DominiService;
import es.caib.seycon.ng.servei.DominiUsuariService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.engine.extobj.AccountExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.GrantExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.ObjectTranslator;
import es.caib.seycon.ng.sync.engine.extobj.RoleExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.ValueObjectMapper;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ReconcileMgr;
import es.caib.seycon.ng.sync.intf.ReconcileMgr2;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class ReconcileEngine2
{

	private ReconcileMgr2 agent;
	private AccountService accountService;
	private DadesAddicionalsService dadesAddicionalsService;
	private AplicacioService appService;
	private Dispatcher dispatcher;
	private ServerService serverService;
	private DominiUsuariService dominiService;
	private UsuariService usuariService;
	private DominiService rolDomainService;
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
	public ReconcileEngine2 (Dispatcher dispatcher, ReconcileMgr2 agent)
	{
		this.agent = agent;
		this.dispatcher = dispatcher;
		dispatcherService = ServiceLocator.instance().getDispatcherService();
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getAplicacioService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getDominiUsuariService();
		usuariService = ServiceLocator.instance().getUsuariService();
		rolDomainService = ServiceLocator.instance().getDominiService();
		dadesAddicionalsService = ServiceLocator.instance().getDadesAddicionalsService();
		log = new StringBuffer();
	}

	public ReconcileEngine2(Dispatcher dispatcher, ReconcileMgr2 agent,
			StringBuffer result) {
		this.agent = agent;
		this.dispatcher = dispatcher;
		dispatcherService = ServiceLocator.instance().getDispatcherService();
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getAplicacioService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getDominiUsuariService();
		usuariService = ServiceLocator.instance().getUsuariService();
		rolDomainService = ServiceLocator.instance().getDominiService();
		dadesAddicionalsService = ServiceLocator.instance().getDadesAddicionalsService();
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
			Account acc = accountService.findAccount(accountName, dispatcher.getCodi());
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
					acc.setDispatcher(dispatcher.getCodi());
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
							acc.setGrantedGroups(new LinkedList<Grup>());
							acc.setGrantedRoles(new LinkedList<Rol>());
							acc.setGrantedUsers(new LinkedList<Usuari>());

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
					existingAccount.setDispatcher(dispatcher.getCodi());
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
		List<DadaUsuari> soffidAttributes = accountService.getAccountAttributes(soffidAccount);
		if (systemAccount.getAttributes() != null)
		{
			for (String key: systemAccount.getAttributes().keySet())
			{
				Object value = systemAccount.getAttributes().get(key);
				DadaUsuari soffidValue = null;
				for (Iterator<DadaUsuari> it = soffidAttributes.iterator(); it.hasNext();)
				{
					DadaUsuari du = it.next();
					if (du.getCodiDada().equals (key))
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
					TipusDada type = dadesAddicionalsService.findSystemDataType(soffidAccount.getDispatcher(), key);
					if (type == null)
					{
						type = new TipusDada();
						type.setSystemName(soffidAccount.getDispatcher());
						type.setOrdre(0L);
						type.setCodi(key);
						type.setLabel(key);
						type.setOperatorVisibility(AttributeVisibilityEnum.HIDDEN);
						type.setAdminVisibility(AttributeVisibilityEnum.HIDDEN);
						type.setUserVisibility(AttributeVisibilityEnum.HIDDEN);
						type.setType(value instanceof byte[] ? TypeEnumeration.BINARY_TYPE:
							value instanceof Date || value instanceof Calendar ? TypeEnumeration.DATE_TYPE:
							TypeEnumeration.STRING_TYPE);
					}
					soffidValue = new DadaUsuari();
					soffidValue.setAccountName(soffidAccount.getName());
					soffidValue.setSystemName(soffidAccount.getDispatcher());
					soffidValue.setCodiDada(key);
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

	private void setDadaValue(DadaUsuari soffidValue, Object value) {
		if (value instanceof Date)
		{
			Calendar c = Calendar.getInstance();
			c.setTime((Date) value);
			soffidValue.setValorDadaDate(c);
		} else if (value instanceof byte[])
		{
			soffidValue.setBlobDataValue((byte[]) value);
		}
		else if (value instanceof Calendar)
		{
			soffidValue.setValorDadaDate((Calendar) value);
		}
		else 
		{
			soffidValue.setValorDada(value.toString());
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
				Rol existingRole =  null;
				try {
					existingRole = serverService.getRoleInfo(roleName, dispatcher.getCodi());
				} catch (UnknownRoleException e) {
				}
				if (existingRole == null)
				{
					Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
					Rol r;
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
								r = vom.parseRol(eo);
							else
							{
								log.append ("Role "+r.getNom()+" is not loaded due to pre-insert trigger failure\n");
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
					Rol r;
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
		List<PoliticaContrasenya> policies = new LinkedList<PoliticaContrasenya>(
						dominiService.findAllPolitiquesContrasenyaDomini(dispatcher.getDominiContrasenyes()));
		if (policies.size () == 0)
			throw new InternalErrorException (
							String.format("There is no password policy defined for system %s", 
											dispatcher.getCodi()));
							
		Collections.sort(policies, new Comparator<PoliticaContrasenya>()
		{

			public int compare (PoliticaContrasenya o1, PoliticaContrasenya o2)
			{
				if (o1.getTipus().equals(o2.getTipus()))
					if ( o1.getDuradaMaxima() == null)
						return o2.getDuradaMaxima() == null ? 0: +1;
					else if ( o1.getDuradaMaxima() < o2.getDuradaMaxima())
						return -1;
					else if ( o1.getDuradaMaxima() > o2.getDuradaMaxima())
						return +1;
					else return 0;
				else if (o1.getTipus().equals("M"))
					return -1;
				else
					return 1;
			}			
		});
		
		return policies.get(0).getTipusUsuari();
	}

	/**
	 * @param acc
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 */
	private void reconcileRoles (Account acc) throws RemoteException, InternalErrorException
	{
		Collection<RolGrant> grants = serverService.getAccountRoles(acc.getName(), acc.getDispatcher());
		List<RolGrant> accountGrants;
		Watchdog.instance().interruptMe(dispatcher.getLongTimeout());
		try
		{
			accountGrants = agent.getAccountGrants(acc.getName());
			// Remove duplicates
			for ( int i = 0; accountGrants != null && i < accountGrants.size(); )
			{	
				RolGrant first = accountGrants.get(i);
				boolean match = false;
				for (int j = i + 1 ; !match && j < accountGrants.size(); j++)
				{
					RolGrant second = accountGrants.get(j);
					if (first.getRolName().equals (second.getRolName()))
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

		for (RolGrant existingGrant: accountGrants)
		{
			if (existingGrant.getDispatcher() == null)
				existingGrant.setDispatcher(dispatcher.getCodi());
			if (existingGrant.getRolName() == null)
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
					log.append ("Grant of "+existingGrant.getRolName()+" to "+existingGrant.getOwnerAccountName()+" is not loaded due to pre-insert trigger failure\n");
				}

			}
				
			if (ok)
			{
			
				Rol role2;
				role2 = ensureRoleExist(existingGrant);
				// Look if this role is already granted
				if (role2 != null)
				{
					boolean found = false;
					for (Iterator<RolGrant> it = grants.iterator(); 
									! found && it.hasNext();)
					{
						RolGrant grant = it.next();
						if (grant.getIdRol().equals (role2.getId()))
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
						log.append ("Granting ").append (existingGrant.getRolName());
						if (existingGrant.getDomainValue() != null && existingGrant.getDomainValue().trim().length() > 0)
							log.append (" [").append (existingGrant.getDomainValue()).append("]");
						log.append (" to ").append(acc.getName()).append('\n');
						grant (acc, existingGrant, role2);
					}
				}
			}
		}

		// Now remove not present roles
		for (RolGrant grant: grants)
		{
			if (grant.getOwnerGroup() == null &&
					grant.getOwnerRol() == null &&
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
						log.append ("Grant of "+grant.getRolName()+" to "+grant.getOwnerAccountName()+" is not removed due to pre-delete trigger failure\n");
						ok = false;
					}
				}
				if (ok)
				{
					RolAccount ra = new RolAccount();
					ra.setAccountId(acc.getId());
					ra.setAccountDispatcher(acc.getDispatcher());
					ra.setAccountName(acc.getName());
					ra.setBaseDeDades(grant.getDispatcher());
					ra.setNomRol(grant.getRolName());
					ra.setId(grant.getId());
					if (grant.getDomainValue() != null)
					{
						ra.setValorDomini(new ValorDomini());
						ra.getValorDomini().setValor(grant.getDomainValue());
					}
					log.append ("Revoking ").append (grant.getRolName());
					if (grant.getDomainValue() != null && grant.getDomainValue().trim().length() > 0)
						log.append (" [").append (grant.getDomainValue()).append("]");
					log.append (" from ").append(acc.getName()).append('\n');
					appService.delete(ra);
				}
			}
		}
	}

	private Rol ensureRoleExist(RolGrant existingGrant)
			throws InternalErrorException, RemoteException {
		Rol role2;
		try
		{
			role2 = serverService.getRoleInfo(existingGrant.getRolName(), existingGrant.getDispatcher());
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
				role2 = agent.getRoleFullInfo(existingGrant.getRolName());
			} finally {
				Watchdog.instance().dontDisturb();
			}
			if (role2 == null)
				throw new InternalErrorException("Unable to grab information about role "+existingGrant.getRolName());
			role2.setBaseDeDades(dispatcher.getCodi());
			if (role2.getDescripcio().length() > 150)
				role2.setDescripcio(role2.getDescripcio().substring(0, 150));
			boolean ok = true;

			if (!preInsertRole.isEmpty())
			{
				RoleExtensibleObject eo = new RoleExtensibleObject(role2, serverService);
				if (executeTriggers(preInsertRole, null, eo))
					role2 = vom.parseRol(eo);
				else
				{
					log.append ("Role "+role2.getNom()+" is not loaded due to pre-insert trigger failure\n");
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
	private void grant (Account acc, RolGrant grant, Rol role) throws InternalErrorException
	{
		if (grant.getDomainValue() != null && role.getDomini() != null && role.getDomini().getCodiExtern() != null)
		{
			// Verify domain value exists
			ValorDomini dv = rolDomainService.findValorDominiAplicacioByNomDominiAndCodiAplicacioDominiAndValor(
					role.getDomini().getNom(), role.getCodiAplicacio(), grant.getDomainValue());
			if (dv == null)
			{
				dv = new ValorDomini();
				dv.setCodiExternDomini(role.getCodiAplicacio());
				dv.setDescripcio("Autogenerated value for "+grant.getDomainValue());
				dv.setNomDomini(role.getDomini().getNom());
				dv.setValor(grant.getDomainValue());
				rolDomainService.create(dv);
			}
			
		}
		RolAccount ra = new RolAccount();
		ra.setAccountId(acc.getId());
		ra.setAccountDispatcher(acc.getDispatcher());
		ra.setAccountName(acc.getName());
		ra.setBaseDeDades(grant.getDispatcher());
		ra.setNomRol(grant.getRolName());
		ra.setCodiAplicacio(role.getCodiAplicacio());
		ra.setValorDomini(new ValorDomini());
		ra.getValorDomini().setValor(grant.getDomainValue());
		ra.getValorDomini().setCodiExternDomini(grant.getDomainValue());
		
		appService.create(ra);
	}

	/**
	 * @param role
	 * @return
	 * @throws InternalErrorException 
	 */
	private Rol createRole (Rol role) throws InternalErrorException
	{
		log.append ("Creating role "+role.getNom()+"\n");
		role.setBaseDeDades(dispatcher.getCodi());
		
		if (role.getCodiAplicacio() == null)
			role.setCodiAplicacio(dispatcher.getCodi());
		Aplicacio app = appService.findAplicacioByCodiAplicacio(role.getCodiAplicacio());
		if (app == null)
		{
			app = new Aplicacio();
			app.setCodi(dispatcher.getCodi());
			app.setBd(dispatcher.getCodi());
			app.setGestionableWF(Boolean.FALSE);
			app.setNom(dispatcher.getDescription());
			app = appService.create(app);
		}
		if (role.getContrasenya() == null)
			role.setContrasenya(Boolean.FALSE);
		
		if (role.getDefecte() == null)
			role.setDefecte(Boolean.FALSE);
		
		if (role.getDescripcio() == null || role.getDescripcio().trim().length() == 0)
			role.setDescripcio("Autogenerated role "+role.getNom());
		
		if (role.getOwnedRoles() == null)
			role.setOwnedRoles(new LinkedList<RolGrant> ());
		
		if (role.getOwnerGroups() == null)
			role.setOwnerGroups(new LinkedList<Grup>());
		
		if (role.getOwnerRoles() == null)
			role.setOwnerRoles(new LinkedList<RolGrant>());
		
		if (role.getDomini() == null)
		{
			role.setDomini(new Domini());
		}
		
		role.getDomini().setCodiExtern(role.getCodiAplicacio());
		
		if (role.getDescripcio().length() > 150)
			role.setDescripcio(role.getDescripcio().substring(0, 150));
				
		return appService.create(role);
	}

	private Rol updateRole (Rol soffidRole, Rol systemRole) throws InternalErrorException
	{
		Rol r = new Rol(soffidRole);
		boolean anyChange = false;
		if (systemRole.getCodiAplicacio() != null &&
				!systemRole.getCodiAplicacio().equals(soffidRole.getCodiAplicacio()))
		{
			soffidRole.setCodiAplicacio(systemRole.getCodiAplicacio());
			anyChange = true;
		}

		if (systemRole.getContrasenya() != null &&
				! systemRole.getContrasenya().equals(soffidRole.getContrasenya()))
		{
			soffidRole.setContrasenya(systemRole.getContrasenya());
			anyChange =true;
		}
		
		if (systemRole.getDefecte() != null && 
				! systemRole.getDefecte().equals(soffidRole.getDefecte()))
		{
			anyChange = true;
			soffidRole.setDefecte(systemRole.getDefecte());
		}

		if (systemRole.getGestionableWF() != null && 
				! systemRole.getGestionableWF().equals(soffidRole.getGestionableWF()))
		{
			soffidRole.setGestionableWF(systemRole.getGestionableWF());
			anyChange = true;
		}

		if (systemRole.getCategory() != null && !
				systemRole.getCategory().equals(soffidRole.getCategory()))
		{
			soffidRole.setCategory(systemRole.getCategory());
			anyChange = true;
		}

		if (systemRole.getDomini() != null &&
				systemRole.getDomini().getNom() != null &&
				!systemRole.getDomini().getNom().equals(soffidRole.getDomini().getNom()))
		{
			soffidRole.setDomini(systemRole.getDomini());
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

		if (systemRole.getDescripcio() != null && 
				!systemRole.getDescripcio().equals(soffidRole.getDescripcio()))
		{
			soffidRole.setDescripcio(systemRole.getDescripcio());
			anyChange = true;
			if (soffidRole.getDescripcio().length() > 150)
				soffidRole.setDescripcio(soffidRole.getDescripcio().substring(0, 150));
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
					soffidRole = vom.parseRol(eo);
				else
				{
					ok = false;
					log.append ("Role "+r.getNom()+" is not loaded due to pre-update trigger failure\n");
				}
			}
				
			if (ok)
			{
				log.append ("Updating role "+soffidRole.getNom()+"\n");
				soffidRole = appService.update(soffidRole);
				executeTriggers(postUpdateRole, 
						new RoleExtensibleObject(r, serverService),
						new RoleExtensibleObject(soffidRole, serverService));
			}
		}
		return soffidRole;
	}

}

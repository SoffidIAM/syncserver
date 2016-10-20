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
import es.caib.seycon.ng.servei.DominiService;
import es.caib.seycon.ng.servei.DominiUsuariService;
import es.caib.seycon.ng.servei.UsuariService;
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

	/**
	 * @param dispatcher 
	 * @param agent
	 */
	public ReconcileEngine2 (Dispatcher dispatcher, ReconcileMgr2 agent)
	{
		this.agent = agent;
		this.dispatcher = dispatcher;
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
					
					acc.setLastUpdated(Calendar.getInstance());
					acc.setType(AccountType.IGNORED);
					acc.setPasswordPolicy(passwordPolicy);
					acc.setGrantedGroups(new LinkedList<Grup>());
					acc.setGrantedRoles(new LinkedList<Rol>());
					acc.setGrantedUsers(new LinkedList<Usuari>());
					try {
						log.append ("Creating account ");
						log.append(acc.getName());
						log.append ('\n');
						acc = accountService.createAccount(acc);
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
				if (existingAccount != null)
				{
					boolean isManaged = acc != null && acc.getId() != null && 
							(dispatcher.isReadOnly() || dispatcher.isAuthoritative() || AccountType.IGNORED.equals(acc.getType()));

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
					if (isManaged)
						acc.setDisabled(existingAccount.isDisabled());
					try {
						log.append ("Updating account ").append (accountName).append ('\n');
//						log.append(acc.toString()).append('\n');
						accountService.updateAccount(acc);
					} catch (AccountAlreadyExistsException e) {
						throw new InternalErrorException ("Unexpected exception", e);
					}
					reconcileAccountAttributes (acc, existingAccount);
					// Only reconcile grants on unmanaged accounts
					// or read only dispatchers
					if (isManaged)
						reconcileRoles (acc);
				}
				
			}
		}
		
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
						createRole(r);
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
			Rol role2;
			try
			{
				role2 = serverService.getRoleInfo(existingGrant.getRolName(), existingGrant.getDispatcher());
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
					role2 = createRole (role2);
				}
			}
			catch (UnknownRoleException e)
			{
				Watchdog.instance().interruptMe(dispatcher.getTimeout());
				try
				{
					role2 = agent.getRoleFullInfo(existingGrant.getRolName());
				} finally {
					Watchdog.instance().dontDisturb();
				}
				role2 = createRole (role2);
			}
			// Look if this role is already granted
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

		// Now remove not present roles
		for (RolGrant grant: grants)
		{
			if (grant.getOwnerGroup() == null &&
					grant.getOwnerRol() == null &&
					grant.getId() != null)
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
		log.append ("Updating role "+soffidRole.getNom()+"\n");
		
		if (systemRole.getCodiAplicacio() != null)
			soffidRole.setCodiAplicacio(systemRole.getCodiAplicacio());

		if (systemRole.getContrasenya() != null)
			soffidRole.setContrasenya(systemRole.getContrasenya());
		
		if (systemRole.getDefecte() != null)
			soffidRole.setDefecte(systemRole.getDefecte());
		
		if (systemRole.getGestionableWF() != null)
			soffidRole.setGestionableWF(systemRole.getGestionableWF());

		if (systemRole.getCategory() != null)
			soffidRole.setCategory(systemRole.getCategory());

		if (systemRole.getDomini() != null)
			soffidRole.setDomini(systemRole.getDomini());

		if (systemRole.getOwnedRoles() != null)
			soffidRole.setOwnedRoles(systemRole.getOwnedRoles());

		if (systemRole.getOwnerRoles() != null)
			soffidRole.setOwnerRoles(systemRole.getOwnerRoles());

		if (systemRole.getOwnerGroups() != null)
			soffidRole.setOwnerGroups(systemRole.getOwnerGroups());

		if (systemRole.getDescripcio() != null)
			soffidRole.setDescripcio(systemRole.getDescripcio());

		if (soffidRole.getDescripcio().length() > 150)
			soffidRole.setDescripcio(soffidRole.getDescripcio().substring(0, 150));
				
		return appService.update(soffidRole);
	}

}

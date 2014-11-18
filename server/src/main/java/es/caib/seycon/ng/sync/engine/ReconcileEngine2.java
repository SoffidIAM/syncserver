/**
 * 
 */
package es.caib.seycon.ng.sync.engine;

import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Aplicacio;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Domini;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.PoliticaContrasenya;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolAccount;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.TipusDomini;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.ValorDomini;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.NeedsAccountNameException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.AplicacioService;
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
	private AplicacioService appService;
	private Dispatcher dispatcher;
	private ServerService serverService;
	private DominiUsuariService dominiService;
	private UsuariService usuariService;
	private DominiService rolDomainService;

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
			Account acc = accountService.findAccount(accountName, dispatcher.getCodi());
			if (acc == null)
			{
				Account usuari = agent.getAccountInfo(accountName);
				if (usuari != null)
				{
					String desiredAccount = accountService.gessAccountName(accountName, dispatcher.getCodi());
					if (desiredAccount != null && desiredAccount.equals(accountName))
					{
						try {
							Usuari existingUser = usuariService.findUsuariByCodiUsuari(accountName);
							acc = accountService.createAccount(existingUser, dispatcher, accountName);
							boolean anyChange = false;
							if (usuari.getLastPasswordSet() != null || usuari.getLastUpdated() != null ||
									usuari.getPasswordExpiration() != null)
							{
								acc.setLastPasswordSet(usuari.getLastPasswordSet());
								acc.setLastUpdated(usuari.getLastUpdated());
								acc.setPasswordExpiration(acc.getPasswordExpiration());
								accountService.updateAccount(acc);
							}
						} catch (AccountAlreadyExistsException e) {
							throw new InternalErrorException ("Unexpected exception", e);
						} catch (NeedsAccountNameException e) {
							throw new InternalErrorException ("Unexpected exception", e);
						}
						
					}
					else
					{
						acc = new Account ();
						acc.setName(accountName);
						acc.setDispatcher(dispatcher.getCodi());
						if (usuari.getDescription() == null)
							acc.setDescription(accountName+" "+accountName);
						else
							acc.setDescription(usuari.getDescription());
						
						acc.setDisabled(false);
						acc.setLastUpdated(Calendar.getInstance());
						acc.setType(AccountType.IGNORED);
						acc.setPasswordPolicy(passwordPolicy);
						acc.setGrantedGroups(new LinkedList<Grup>());
						acc.setGrantedRoles(new LinkedList<Rol>());
						acc.setGrantedUsers(new LinkedList<Usuari>());
						try {
							acc = accountService.createAccount(acc);
						} catch (AccountAlreadyExistsException e) {
							throw new InternalErrorException ("Unexpected exception", e);
						}
					}
				}
			} else {
				Account usuari = agent.getAccountInfo(accountName);
				if (usuari != null)
				{
					if (usuari.getDescription() != null && usuari.getDescription().trim().length() > 0)
						acc.setDescription(usuari.getDescription());
					if (usuari.getLastPasswordSet() != null)
						acc.setLastPasswordSet(usuari.getLastPasswordSet());
					if (usuari.getLastUpdated() != null)
						acc.setLastUpdated(usuari.getLastUpdated());
					if (usuari.getPasswordExpiration() != null)
						acc.setPasswordExpiration(acc.getPasswordExpiration());
					try {
						accountService.updateAccount(acc);
					} catch (AccountAlreadyExistsException e) {
						throw new InternalErrorException ("Unexpected exception", e);
					}
				}
				
			}
			
			if (acc != null && acc.getId() != null && (dispatcher.isReadOnly() || AccountType.IGNORED.equals(acc.getType())))
				reconcileRoles (acc);
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
		Collection<RolGrant> grants = serverService.getAccountExplicitRoles(acc.getName(), acc.getDispatcher());
		for (RolGrant existingGrant: agent.getAccountGrants(acc.getName()))
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
					role2 = agent.getRoleFullInfo(existingGrant.getRolName());
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
				role2 = agent.getRoleFullInfo(existingGrant.getRolName());
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
				grant (acc, existingGrant, role2);
		}

		// Now remove not present roles
		for (RolGrant grant: grants)
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
			appService.delete(ra);
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

}

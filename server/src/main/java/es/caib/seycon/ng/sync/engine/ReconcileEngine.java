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
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.NeedsAccountNameException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.AplicacioService;
import es.caib.seycon.ng.servei.DominiUsuariService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.intf.ReconcileMgr;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class ReconcileEngine
{

	private ReconcileMgr agent;
	private AccountService accountService;
	private AplicacioService appService;
	private Dispatcher dispatcher;
	private ServerService serverService;
	private DominiUsuariService dominiService;
	private UsuariService usuariService;

	/**
	 * @param dispatcher 
	 * @param agent
	 */
	public ReconcileEngine (Dispatcher dispatcher, ReconcileMgr agent)
	{
		this.agent = agent;
		this.dispatcher = dispatcher;
		accountService = ServiceLocator.instance().getAccountService();
		appService = ServiceLocator.instance().getAplicacioService();
		serverService = ServiceLocator.instance().getServerService();
		dominiService = ServiceLocator.instance().getDominiUsuariService();
		usuariService = ServiceLocator.instance().getUsuariService();
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
				Usuari usuari = agent.getUserInfo(accountName);
				if (usuari != null)
				{
					String desiredAccount = accountService.gessAccountName(accountName, dispatcher.getCodi());
					if (desiredAccount != null && desiredAccount.equals(accountName))
					{
						try {
							Usuari existingUser = usuariService.findUsuariByCodiUsuari(accountName);
							acc = accountService.createAccount(existingUser, dispatcher, accountName);
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
						if (usuari.getFullName() != null)
							acc.setDescription(usuari.getFullName());
						else if (usuari.getPrimerLlinatge() != null)
						{
							if (usuari.getNom() == null)
								acc.setDescription(usuari.getPrimerLlinatge());
							else
								acc.setDescription(usuari.getNom()+" "+usuari.getPrimerLlinatge());
						}
						else
							acc.setDescription("Autocreated account "+accountName);
						
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
					if ( o1.getDuradaMaxima() < o2.getDuradaMaxima())
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
		for (Rol role: agent.getAccountRoles(acc.getName()))
		{
			if (role.getBaseDeDades() == null)
				role.setBaseDeDades(dispatcher.getCodi());
			Rol role2;
			try
			{
				role2 = serverService.getRoleInfo(role.getNom(), role.getBaseDeDades());
				if (role2 == null)
					role2 = createRole (role);
				else if (role.getDescripcio() != null && ! role2.getDescripcio().equals(role.getDescripcio()))
				{
					role2.setDescripcio(role.getDescripcio());
					if (role2.getDescripcio().length() > 150)
						role2.setDescripcio(role2.getDescripcio().substring(0, 150));
					appService.update(role2);
				}
			}
			catch (UnknownRoleException e)
			{
				role2 = createRole (role);
			}
			// Look if this role is already granted
			boolean found = false;
			for (Iterator<RolGrant> it = grants.iterator(); 
							! found && it.hasNext();)
			{
				RolGrant grant = it.next();
				if (grant.getIdRol().equals (role2.getId()))
				{
					found = true;
					it.remove ();
				}
			}
			if (!found)
				grant (acc, role2);
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
				appService.delete(ra);
			}
		}
	}

	/**
	 * @param acc
	 * @param role2
	 * @throws InternalErrorException 
	 */
	private void grant (Account acc, Rol role2) throws InternalErrorException
	{
		RolAccount ra = new RolAccount();
		ra.setAccountId(acc.getId());
		ra.setAccountDispatcher(acc.getDispatcher());
		ra.setAccountName(acc.getName());
		ra.setBaseDeDades(role2.getBaseDeDades());
		ra.setNomRol(role2.getNom());
		ra.setCodiAplicacio(role2.getCodiAplicacio());
		
		appService.create(ra);
	}

	/**
	 * @param role
	 * @return
	 * @throws InternalErrorException 
	 */
	private Rol createRole (Rol role) throws InternalErrorException
	{
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
		
		if (role.getDescripcio() == null)
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
		
		if (role.getDescripcio().length() > 150)
			role.setDescripcio(role.getDescripcio().substring(0, 150));
				
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
				Rol existingRole =  null;
				try {
					existingRole = serverService.getRoleInfo(roleName, dispatcher.getCodi());
				} catch (UnknownRoleException e) {
				}
				if (existingRole == null)
				{
					Rol r = agent.getRoleFullInfo(roleName);
					if (r != null)
						createRole(r);
				}
			}
		}
	}
}

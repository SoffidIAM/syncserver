/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.UserType;
import com.soffid.iam.reconcile.service.ReconcileService;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.service.ApplicationService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.DomainService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.comu.AccountCriteria;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public abstract class PreviewChangesEngine
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
	private DispatcherService dispatcherService;
	Long reconcileProcessId;
	protected ReconcileService reconcileService;
	
	List<String> roles = new LinkedList<String>();
	List<String> accountsList = new LinkedList<String>();
	private TaskGenerator taskGenerator;

	public PreviewChangesEngine(com.soffid.iam.api.System dispatcher, PrintWriter out) {
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

	HashMap<String,List<String[]>> accountActions = new HashMap<String, List<String[]>>();
	HashMap<String,List<String[]>> roleActions = new HashMap<String, List<String[]>>();
	HashMap<String,Integer> accountCounters = new HashMap<String, Integer>();
	HashMap<String,Integer> roleCounters = new HashMap<String, Integer>();
	/**
	 * @throws Exception 
	 * 
	 */
	public void execute () throws Exception
	{
		previewAccountChanges();
		previewRoleChanges();
		generateReport();
	}
	
	private void previewAccountChanges() throws InternalErrorException, RemoteException {
		AccountCriteria criteria = new AccountCriteria();
		criteria.setDispatcher(dispatcher.getName());
		HashSet<String> validTypes = new HashSet<String>();
		for (UserType type: ServiceLocator.instance().getUserDomainService().findAllUserType())
		{
			if (!type.isUnmanaged())
				validTypes.add(type.getCode());
		}
		for (String accountName: accountService.findAccountNames(dispatcher.getName()))
		{
			Account account = accountService.findAccount(accountName, dispatcher.getName());
			if ( account.getType() != AccountType.IGNORED && validTypes.contains(account.getPasswordPolicy()))
			{
				List<String[]> changes;
				try {
					changes = getAccountChanges (account);
				} catch (Exception e ) {
					changes = new LinkedList<String[]>();
					changes.add(new String[] {"ERROR", e.toString()});
				}
				processChanges(accountName, changes, accountActions, accountCounters);
			}
		}
	}

	private void processChanges(String accountName, List<String[]> changes, HashMap<String, List<String[]>> actions, HashMap<String, Integer> counters) {
		if (changes == null)
			return;
		Set<String> actionsSet = new HashSet<String>();
		for (String[] change: changes)
		{
			String action = change[0];
			List<String[]> m = actions.get(action);
			if (m == null)
			{
				m = new LinkedList<String[]>();
				actions.put(action, m);
			}
			String r[] = new String [3];
			r[0] = accountName;
			r[1] = change.length > 1 ? change[1]: "";
			r[2] = change.length > 2 ? change[2]: "";
			m.add(r);		
			actionsSet.add(action);
		}
		for ( String action: actionsSet)
		{
			Integer i = counters.get(action);
			if (i == null)
				counters.put(action, new Integer(1));
			else
				counters.put(action, new Integer (i.intValue()+1));
		}
	}
	
	protected abstract List<String[]> getAccountChanges(Account account) throws RemoteException, InternalErrorException;
	
	private void previewRoleChanges() throws Exception {
		AccountCriteria criteria = new AccountCriteria();
		criteria.setDispatcher(dispatcher.getName());
		for (String roleName: appService.findRoleNames(dispatcher.getName()))
		{
			Role role = appService.findRoleByNameAndSystem(roleName, dispatcher.getName());
			List<String[]> changes;
			try {
				changes = getRoleChanges (role);
			} catch (Exception e ) {
				changes = new LinkedList<String[]>();
				changes.add(new String[] {"ERROR", e.toString()});
			}
			processChanges(roleName, changes, roleActions, roleCounters);
		}
	}
	
	protected abstract List<String[]> getRoleChanges(Role role) throws RemoteException, InternalErrorException;


	private void generateReport() {
		generateReport2 ("accounts", "Accounts", accountCounters, accountActions);
		generateReport2 ("roles", "Role", roleCounters, roleActions);
	}	
		
	private void generateReport2(String name, String name2, HashMap<String, Integer> counters, 
			HashMap<String, List<String[]>> actions) {
		log.println("Changes detected for "+name);
		log.println("=============================");
		
		if (actions.isEmpty())
			log.println("NO CHANGE DETECTED");
		else
		{
			int maxlen = 0;
			LinkedList<String> actionNames = new LinkedList<String>( actions.keySet() );
			Collections.sort(actionNames);
			for (String action: counters.keySet())
			{
				if (action.length() > maxlen) maxlen = action.length();
			}
			String format = "| %" + (-maxlen) + "s | %-6d |\r\n";
			StringBuffer b = new StringBuffer();
			b.append("+-");
			for (int i = 0; i < maxlen; i++) b.append("-");
			b.append("-+--------+");
			log.println (b.toString());
			for (String action: actionNames)
			{
				log.printf(format, new Object[] { action, counters.get(action)});
			}
			log.println (b.toString());
			log.println ();
			log.println ();
			StringBuffer sb = new StringBuffer();
			while (sb.length() < maxlen) sb.append("=");
			for (String action: actionNames)
			{
				log.println (name2+": "+action);
				log.println (sb.toString());
				List<String[]> values = actions.get(action);
				int maxlen1 = 1;
				int maxlen2 = 1;
				int maxlen3 = 1;
				for ( String[] value: values)
				{
					if (value.length > 0 && value[0] != null && value[0].length() > maxlen1)
						maxlen1 = value[0].length();
					if (value.length > 1 && value[1] != null && value[1].length() > maxlen2)
						maxlen2 = value[1].length();
					if (value.length > 2 && value[2] != null && value[2].length() > maxlen3)
						maxlen3 = value[2].length();
				}
				String format2 = "   | %" + (-maxlen1) + "s | %" + (-maxlen2) + "s | %" + (-maxlen3) + "s |\r\n";
				b = new StringBuffer();
				b.append("   +-");
				for (int i = 0; i < maxlen1; i++) b.append("-");
				b.append("-+-");
				for (int i = 0; i < maxlen2; i++) b.append("-");
				b.append("-+-");
				for (int i = 0; i < maxlen3; i++) b.append("-");
				b.append("-+");
				log.println (b.toString());
				for ( String[] value: values)
				{
					log.printf(format2, new Object[] { 
						value.length > 0 && value[0] != null? value[0] : "",
						value.length > 1 && value[1] != null? value[1] : "",
						value.length > 2 && value[2] != null? value[2] : ""
					});
				}
				log.println (b.toString());
				log.println ();
			}
			
		}

	}
}


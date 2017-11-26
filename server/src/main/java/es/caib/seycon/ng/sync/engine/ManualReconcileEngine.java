package es.caib.seycon.ng.sync.engine;

import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.ProcessInstance;

import com.soffid.iam.api.AccountStatus;
import com.soffid.iam.api.ReconcileTrigger;
import com.soffid.iam.reconcile.common.AccountProposedAction;
import com.soffid.iam.reconcile.common.ProposedAction;
import com.soffid.iam.reconcile.common.ReconcileAccount;
import com.soffid.iam.reconcile.common.ReconcileAssignment;
import com.soffid.iam.reconcile.common.ReconcileRole;
import com.soffid.iam.reconcile.service.ReconcileService;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.model.TasqueEntity;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.extobj.GrantExtensibleObject;
import es.caib.seycon.ng.sync.intf.ReconcileMgr;
import es.caib.seycon.ng.sync.intf.ReconcileMgr2;

public class ManualReconcileEngine extends ReconcileEngine2 {

	Log log = LogFactory.getLog(getClass());
	private static final int MAX_LENGTH = 150;
	private static final int MAX_ROLE_CODE_LENGTH = 50;

	public ManualReconcileEngine(Dispatcher dispatcher, ReconcileMgr2 agent) {
		super(dispatcher, agent);
	}

	public ManualReconcileEngine(Dispatcher dispatcher, ReconcileMgr2 agent, StringBuffer result) {
		super(dispatcher, agent, result);
	}

	/**
	 * End reconcile process.
	 * 
	 * <p>
	 * Implements the functionality to check pending tasks.
	 * @param agent 
	 * 
	 * @param taskHandler
	 *            Task handler.
	 */
	public void endReconcile ()
		throws InternalErrorException, RemoteException
	{
		ReconcileService service = ServerServiceLocator.instance()
						.getReconcileService();

		JbpmContext ctx = DispatcherHandlerImpl.getConfig().createJbpmContext();
		ProcessInstance pi = ctx.getProcessInstance(reconcileProcessId);
		
		if (pi == null)
			throw new InternalErrorException("Wrong request ID");

		pi.signal();
		ctx.close();
	}
	
	protected void loadAccount(String passwordPolicy, List<ReconcileTrigger> preInsert,
			List<ReconcileTrigger> postInsert, String accountName, Account account)
			throws InternalErrorException, RemoteException 
	{
		ReconcileAccount reconcileAccount = null; // Reconcile accounts handler
		ReconcileAssignment reconcileAssign = null; // Reconcile assignments handler
		
		// Set user parameters
		reconcileAccount = new ReconcileAccount();
		reconcileAccount.setAccountName(account.getName());
		reconcileAccount.setDescription(account.getDescription());
		reconcileAccount.setProcessId(reconcileProcessId);
		reconcileAccount.setProposedAction(AccountProposedAction.BIND_TO_EXISTING_USER);
		reconcileAccount.setDispatcher(dispatcher.getCodi());
		reconcileAccount.setNewAccount(Boolean.TRUE);
		reconcileAccount.setDeletedAccount(Boolean.FALSE);
		reconcileAccount.setAttributes(new HashMap<String, Object>());
//		if (account.getAttributes() != null)
//			reconcileAccount.getAttributes().putAll(account.getAttributes());
		reconcileService.addUser(reconcileAccount);			
		account.setDispatcher( dispatcher.getCodi() );
		reconcileRoles (account);
	}


	protected void updateAccount(List<ReconcileTrigger> preUpdate, List<ReconcileTrigger> postUpdate,
			String accountName, Account acc, Account existingAccount) throws InternalErrorException, RemoteException 
	{
		
		log.info("Checking account "+acc.getName());
		boolean anyChange = false;
//		if (existingAccount.getDescription() != null && 
//				existingAccount.getDescription().trim().length() > 0 &&
//				!existingAccount.getDescription().equals(acc.getDescription()))
//		{
//			log.info("Description for account "+acc.getName()+" has changed");
//			anyChange = true;
//		}
		
		
//		if (existingAccount.getAttributes() != null )
//		{
//			for (String att: existingAccount.getAttributes().keySet())
//			{
//				Object v = existingAccount.getAttributes().get(att);
//				Object v2 = acc.getAttributes().get(att);
//				if (v != null &&
//						!v.equals(v2))
//				{
//					acc.getAttributes().put(att, v);
//					anyChange = true;
//				}
//			}
//		}
		
		if (AccountStatus.REMOVED.equals(acc.getStatus()) ||
				acc.isDisabled() != existingAccount.isDisabled())
		{
			log.info("Enabled state for account "+acc.getName()+" has changed");
			anyChange = true;
		}
	
		if (anyChange)
		{
			log.info("Registering account "+existingAccount.getName()+" change");
			ReconcileAccount reconcileAccount = new ReconcileAccount();
			reconcileAccount.setAccountName(existingAccount.getName());
			if (existingAccount.getDescription() == null)
				reconcileAccount.setDescription(acc.getDescription());
			else
				reconcileAccount.setDescription(existingAccount.getDescription());
			reconcileAccount.setProcessId(reconcileProcessId);
			reconcileAccount.setProposedAction(AccountProposedAction.UPDATE_ACCOUNT);
			reconcileAccount.setDispatcher(dispatcher.getCodi());
			reconcileAccount.setAttributes(acc.getAttributes());
			reconcileAccount.setNewAccount(Boolean.FALSE);
			reconcileAccount.setDeletedAccount(Boolean.FALSE);
			reconcileAccount.setActive(! existingAccount.isDisabled());
			reconcileAccount.setAttributes(new HashMap<String, Object>());
//			if (existingAccount.getAttributes() != null)
//				reconcileAccount.getAttributes().putAll(existingAccount.getAttributes());
			reconcileService.addUser(reconcileAccount);				
		}
		reconcileRoles (acc);
	}

	public Long getReconcileProcessId() {
		return reconcileProcessId;
	}

	public void setReconcileProcessId(Long reconcileProcessId) {
		this.reconcileProcessId = reconcileProcessId;
	}

	@Override
	protected void loadRole(Rol role) throws InternalErrorException {
		ReconcileRole reconRole = new ReconcileRole();
		reconRole.setRoleName(role.getNom());
		// Check role description lenght
		if (role.getDescripcio() == null || role.getDescripcio().trim().length() == 0)
		{
			reconRole.setDescription(role.getNom());
		}
		else if (role.getDescripcio().length() <= MAX_LENGTH)
			reconRole.setDescription(role.getDescripcio());
		else
			reconRole.setDescription(role.getDescripcio().substring(0,
							MAX_LENGTH));

		reconRole.setProcessId(reconcileProcessId);
		reconRole.setProposedAction(ProposedAction.LOAD);
		reconRole.setDispatcher(dispatcher.getCodi());

		reconcileService.addRole(reconRole);
	}

	@Override
	protected void unloadGrant(Account acc, RolGrant existingGrant) throws InternalErrorException {
		/*
		ReconcileAssignment reconcileAssign = new ReconcileAssignment();
		reconcileAssign.setAccountName(acc.getName());
		reconcileAssign.setAssignmentName(acc.getName() + " - "
						+ existingGrant.getRolName());
		reconcileAssign.setProcessId(reconcileProcessId);
		reconcileAssign.setProposedAction(ProposedAction.IGNORE);
		reconcileAssign.setRoleName(existingGrant.getRolName());
		reconcileAssign.setDispatcher(dispatcher.getCodi());
		if (existingGrant.getDomainValue() != null)
			reconcileAssign.setDomainValue(existingGrant.getDomainValue());
		reconcileService.addAssignment(reconcileAssign);
		*/
	}

	@Override
	protected void loadGrant(Account acc, RolGrant existingGrant, Collection<RolGrant> grants)
			throws InternalErrorException, RemoteException {
		boolean found = false;
		for (Iterator<RolGrant> it = grants.iterator(); 
						! found && it.hasNext();)
		{
			RolGrant grant = it.next();
			if (grant.getRolName().equals (existingGrant.getRolName()) &&
					grant.getDispatcher().equals(dispatcher.getCodi()))
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
			ReconcileAssignment reconcileAssign = new ReconcileAssignment();
			reconcileAssign.setAccountName(acc.getName());
			reconcileAssign.setAssignmentName(acc.getName() + " - "
							+ existingGrant.getRolName());
			reconcileAssign.setProcessId(reconcileProcessId);
			reconcileAssign.setProposedAction(ProposedAction.LOAD);
			reconcileAssign.setRoleName(existingGrant.getRolName());
			reconcileAssign.setDispatcher(dispatcher.getCodi());
			if (existingGrant.getDomainValue() != null)
				reconcileAssign.setDomainValue(existingGrant.getDomainValue());
			reconcileService.addAssignment(reconcileAssign);
		}
	}

	@Override
	protected Rol updateRole(Rol soffidRole, Rol systemRole) throws InternalErrorException {
		// Nothing to do
		return null;
	}

	@Override
	public void reconcile() throws RemoteException, InternalErrorException {
		super.reconcile();
		endReconcile();
	}

}

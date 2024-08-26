package com.soffid.iam.sync.service.impl;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleAccount;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;
import com.soffid.iam.service.ApplicationService;
import com.soffid.iam.sync.agent.AgentInterface;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.DispatcherHandlerImpl;
import com.soffid.iam.sync.engine.InterfaceWrapper;
import com.soffid.iam.sync.intf.ExtensibleObjectMgr;
import com.soffid.iam.sync.intf.ReconcileMgr;
import com.soffid.iam.sync.intf.ReconcileMgr2;
import com.soffid.iam.sync.intf.RoleMgr;
import com.soffid.iam.sync.intf.UserMgr;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;


public class TemporaryPermissions {
	Log log = LogFactory.getLog(getClass());
	
	public List<String> assignWindows(String host, String accountName, String accountSystem,
			List<String> permissions) throws InternalErrorException {
		List<String> assignedPermissions = new LinkedList<>();
		DispatcherHandler d = ServiceLocator.instance().getTaskGenerator().getDispatcher(accountSystem);
		try {
			if (d != null) {
				Object agent = d.connect(false, false);
				if (agent != null) {
					ReconcileMgr rmgr = InterfaceWrapper.getReconcileMgr(agent);
					ReconcileMgr2 rmgr2 = InterfaceWrapper.getReconcileMgr2(agent);
					ExtensibleObjectMgr eomgr = InterfaceWrapper.getExtensibleObjectMgr(agent);
					if (rmgr != null) {
						List<Role> grants = rmgr.getAccountRoles(accountName);
						for (String permission: permissions) {
							if (! isIncludedInRoles(permission, grants)) {
								Map<String, Object> m = new HashMap<>();
								m.put("user", accountName);
								m.put("group", permission);
								try {
									eomgr.invoke("add-group", host, m );
									assignedPermissions.add(permission);
								} catch (Exception e) {
									log.warn("Error assigning permission "+permission+" to "+accountName+" on "+host, e);
								}
							}
						}
					} else if (rmgr2 != null) {
						List<RoleGrant> grants = rmgr2.getAccountGrants(accountName);
						for (String permission: permissions) {
							if (! isIncludedInGrants(permission, grants)) {
								Map<String, Object> m = new HashMap<>();
								m.put("user", accountName);
								m.put("group", permission);
								try {
									eomgr.invoke("add-group", host, m );
									assignedPermissions.add(permission);
								} catch (Exception e) {
									log.warn("Error assigning permission "+permission+" to "+accountName+" on "+host, e);
								}
							}
						}
					}
					if (agent instanceof AgentInterface)
						((AgentInterface) agent).close();
					else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
						((es.caib.seycon.ng.sync.agent.AgentInterface) agent).close();
				}
			}
		} catch (Exception e) {
			log.warn("Error assigning permissions to "+accountName+" on "+host, e);
		}
		return assignedPermissions;
	}

	public void removeWindows(String host, String accountName, String accountSystem,
			List<String> permissions) throws InternalErrorException {
		DispatcherHandler d = ServiceLocator.instance().getTaskGenerator().getDispatcher(accountSystem);
		try {
			if (d != null) {
				Object agent = d.connect(false, false);
				if (agent != null) {
					ExtensibleObjectMgr eomgr = InterfaceWrapper.getExtensibleObjectMgr(agent);
					for (String permission: permissions) {
						Map<String, Object> m = new HashMap<>();
						m.put("user", accountName);
						m.put("group", permission);
						try {
							eomgr.invoke("delete-group", host, m );
						} catch (Exception e) {
							log.warn("Error removing permission "+permission+" from "+accountName+" on "+host, e);
						}
					}
					if (agent instanceof AgentInterface)
						((AgentInterface) agent).close();
					else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
						((es.caib.seycon.ng.sync.agent.AgentInterface) agent).close();
				}
			}
		} catch (Exception e) {
			log.warn("Error assigning permissions to "+accountName+" on "+host, e);
		}
	}
	
	public void removeClassic(String host, String accountName, String accountSystem,
			List<String> permissions) throws InternalErrorException {
		final ServiceLocator serviceLocator = ServiceLocator.instance();
		List<String> assignedPermissions = new LinkedList<>();
		DispatcherHandler d = serviceLocator.getTaskGenerator().getDispatcher(accountSystem);
		try {
			if (d != null) {
				Account account = serviceLocator.getAccountService().findAccount(accountName, accountSystem);
				if (account != null && !account.isDisabled() && account.getType() != AccountType.IGNORED) {
					Object agent = d.connect(false, false);
					if (agent != null) {
						UserMgr umgr = InterfaceWrapper.getUserMgr(agent);
						RoleMgr rmgr2 = InterfaceWrapper.getRoleMgr(agent);
						final ApplicationService applicationService = serviceLocator.getApplicationService();
						Collection<RoleAccount> grants = applicationService
								.findRoleAccountByAccount(account.getId());
						boolean any = false;
						for (RoleAccount grant: grants) {
							Role r = applicationService.findRoleById(grant.getRoleId());
							if (r != null) {
								serviceLocator.getAsyncRunnerService().runNewTransaction(
										() -> {
									applicationService.delete(grant);
									return null;
								});
								any = true;
								rmgr2.updateRole(r);
							}
						}
						if (any)
						{
							if (account.getType() == AccountType.USER) {
								User user = serviceLocator.getUserService()
										.findUserByUserName(account.getOwnerUsers().iterator().next());
								umgr.updateUser(account, user);
							} else {
								umgr.updateUser(account);
							}
						}
					}
					if (agent instanceof AgentInterface)
						((AgentInterface) agent).close();
					else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
						((es.caib.seycon.ng.sync.agent.AgentInterface) agent).close();
				}
			}
		} catch (Exception e) {
			log.warn("Error removing permissions to "+accountName+" on "+host, e);
		}
	}

	private boolean isIncludedInGrants(String permission, Collection<RoleGrant> grants) {
		for (RoleGrant grant: grants ) {
			if (grant.getRoleName().equals(permission))
				return true;
		}
		return false;
	}

	private boolean isIncludedInRoles(String permission, Collection<Role> grants) {
		for (Role grant: grants ) {
			if (grant.getName().equals(permission))
				return true;
		}
		return false;
	}

	public List<String> assignClassic(String host, String accountName, String accountSystem,
			List<String> permissions) throws InternalErrorException {
		final ServiceLocator serviceLocator = ServiceLocator.instance();
		List<String> assignedPermissions = new LinkedList<>();
		DispatcherHandler d = serviceLocator.getTaskGenerator().getDispatcher(accountSystem);
		try {
			if (d != null) {
				Account account = serviceLocator.getAccountService().findAccount(accountName, accountSystem);
				if (account != null && !account.isDisabled() && account.getType() != AccountType.IGNORED) {
					Object agent = d.connect(false, false);
					if (agent != null) {
						UserMgr umgr = InterfaceWrapper.getUserMgr(agent);
						RoleMgr rmgr2 = InterfaceWrapper.getRoleMgr(agent);
						final ApplicationService applicationService = serviceLocator.getApplicationService();
						Collection<RoleGrant> grants = applicationService
								.findEffectiveRoleGrantByAccount(account.getId());
						boolean any = false;
						for (String permission: permissions) {
							if (! isIncludedInGrants(permission, grants)) {
								Role r = applicationService.findRoleByNameAndSystem(permission, accountSystem);
								if (r != null) {
									assignedPermissions.add(permission);
									serviceLocator.getAsyncRunnerService().runNewTransaction(
											() -> {
										RoleAccount ra = new RoleAccount();
										ra.setAccountId(account.getId());
										ra.setAccountName(account.getName());
										ra.setAccountSystem(accountSystem);
										ra.setRoleName(r.getName());
										ra.setRoleId(r.getId());
										ra.setSystem(accountSystem);
										ra.setEndDate(new Date(System.currentTimeMillis() + 24 * 60 * 60_000)); // Max 24 hours
										applicationService.create(ra);
										return null;
									});
									any = true;
									rmgr2.updateRole(r);
								}
							}
						}
						if (any)
						{
							if (account.getType() == AccountType.USER) {
								User user = serviceLocator.getUserService()
										.findUserByUserName(account.getOwnerUsers().iterator().next());
								umgr.updateUser(account, user);
							} else {
								umgr.updateUser(account);
							}
						}
					}
					if (agent instanceof AgentInterface)
						((AgentInterface) agent).close();
					else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
						((es.caib.seycon.ng.sync.agent.AgentInterface) agent).close();
				}
			}
		} catch (Exception e) {
			log.warn("Error assigning permissions to "+accountName+" on "+accountSystem, e);
		}
		return assignedPermissions;
	}

}

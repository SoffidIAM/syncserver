package com.soffid.iam.sync.engine;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.AttributeTranslation;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.GroupUser;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.HostService;
import com.soffid.iam.api.MailList;
import com.soffid.iam.api.Network;
import com.soffid.iam.api.PasswordDomain;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.PrinterUser;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleAccount;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.System;
import com.soffid.iam.api.SystemAccessControl;
import com.soffid.iam.api.Task;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.UserData;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.sync.agent.Plugin;
import com.soffid.iam.sync.engine.extobj.ExtensibleObjectFinder;
import com.soffid.iam.sync.intf.AccessControlMgr;
import com.soffid.iam.sync.intf.AccessLogMgr;
import com.soffid.iam.sync.intf.AuthoritativeChange;
import com.soffid.iam.sync.intf.CustomObjectMgr;
import com.soffid.iam.sync.intf.CustomTaskMgr;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObjectMgr;
import com.soffid.iam.sync.intf.GroupMgr;
import com.soffid.iam.sync.intf.HostMgr;
import com.soffid.iam.sync.intf.KerberosAgent;
import com.soffid.iam.sync.intf.KerberosPrincipalInfo;
import com.soffid.iam.sync.intf.LogEntry;
import com.soffid.iam.sync.intf.MailAliasMgr;
import com.soffid.iam.sync.intf.NetworkMgr;
import com.soffid.iam.sync.intf.ReconcileMgr;
import com.soffid.iam.sync.intf.ReconcileMgr2;
import com.soffid.iam.sync.intf.RoleMgr;
import com.soffid.iam.sync.intf.ServiceMgr;
import com.soffid.iam.sync.intf.SharedFolderMgr;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.LlistaCorreu;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolAccount;
import es.caib.seycon.ng.comu.Tasca;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.InvalidPasswordException;
import es.caib.seycon.ng.exception.ServerRedirectException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownNetworkException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.AuthoritativeChangeIdentifier;
import es.caib.seycon.ng.sync.intf.AuthoritativeIdentitySource;
import es.caib.seycon.ng.sync.intf.AuthoritativeIdentitySource2;
import es.caib.seycon.ng.sync.intf.ExtensibleObjectMapping;
import es.caib.seycon.ng.sync.intf.UserMgr;
import oracle.net.aso.m;

public class InterfaceWrapper {
	
	public static com.soffid.iam.sync.intf.AuthoritativeIdentitySource2 getAuthoritativeIdentitySource2 (Object obj)
	{
		if (obj instanceof com.soffid.iam.sync.intf.AuthoritativeIdentitySource2)
			return (com.soffid.iam.sync.intf.AuthoritativeIdentitySource2) obj;
		else if (obj instanceof AuthoritativeIdentitySource2)
		{
			final AuthoritativeIdentitySource2 autsrc = (AuthoritativeIdentitySource2) obj;
			return  new com.soffid.iam.sync.intf.AuthoritativeIdentitySource2() {
				
				public boolean hasMoreData() throws InternalErrorException {
					return autsrc.hasMoreData();
				}
				
				public String getNextChange() throws InternalErrorException {
					return autsrc.getNextChange();
				}
				
				public Collection<com.soffid.iam.sync.intf.AuthoritativeChange> getChanges(
						String lastChange) throws InternalErrorException {
					return com.soffid.iam.sync.intf.AuthoritativeChange.toAuthoritativeChangeList(
							autsrc.getChanges(lastChange));
				}
			};
		}
		else
			return null;
	}
	
	public static com.soffid.iam.sync.intf.UserMgr getUserMgr (Object obj)
	{
		if (obj instanceof com.soffid.iam.sync.intf.UserMgr)
			return (com.soffid.iam.sync.intf.UserMgr) obj;
		else if (obj instanceof UserMgr)
		{
			final UserMgr usrmgr = (UserMgr) obj;
			return new com.soffid.iam.sync.intf.UserMgr() {
				
				public boolean validateUserPassword(String userName, com.soffid.iam.api.Password password)
						throws RemoteException, InternalErrorException {
					return usrmgr.validateUserPassword(userName, Password.toPassword(password));
				}
				
				public void updateUserPassword(String userName, User userData,
						com.soffid.iam.api.Password password, boolean mustchange) throws RemoteException,
						InternalErrorException {
					usrmgr.updateUserPassword(userName, Usuari.toUsuari(userData), Password.toPassword(password), mustchange);
				}
				
				public void updateUser(com.soffid.iam.api.Account account)
						throws RemoteException, InternalErrorException {
					usrmgr.updateUser(account.getName(), account.getDescription()); 
				}
				
				public void updateUser(com.soffid.iam.api.Account account, User user)
						throws RemoteException, InternalErrorException {
					usrmgr.updateUser(account.getName(), Usuari.toUsuari(user));
				}
				
				public void removeUser(String userName) throws RemoteException,
						InternalErrorException {
					usrmgr.removeUser(userName);
				}
			};
		}
		else
			return null;
	}

	public static com.soffid.iam.sync.intf.AuthoritativeIdentitySource getAuthoritativeIdentitySource (Object obj)
	{
		if (obj instanceof com.soffid.iam.sync.intf.AuthoritativeIdentitySource)
			return (com.soffid.iam.sync.intf.AuthoritativeIdentitySource) obj;
		else if (obj instanceof AuthoritativeIdentitySource)
		{
			final AuthoritativeIdentitySource autsrc = (AuthoritativeIdentitySource) obj;
			return  new com.soffid.iam.sync.intf.AuthoritativeIdentitySource() {
				
				public Collection<com.soffid.iam.sync.intf.AuthoritativeChange> getChanges()
						throws InternalErrorException {
					return com.soffid.iam.sync.intf.AuthoritativeChange.toAuthoritativeChangeList(
							autsrc.getChanges());
				}

				public void commitChange(
						com.soffid.iam.sync.intf.AuthoritativeChangeIdentifier id)
						throws InternalErrorException {
					autsrc.commitChange(AuthoritativeChangeIdentifier.toAuthoritativeChangeIdentifier(id));
				}
			};
		}
		else
			return null;
	}

	public static AccessControlMgr getAccessControlMgr(Object obj) {
		if (obj instanceof AccessControlMgr)
			return (AccessControlMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.AccessControlMgr)
		{
			final es.caib.seycon.ng.sync.intf.AccessControlMgr agent = 
					(es.caib.seycon.ng.sync.intf.AccessControlMgr) obj;
			return new AccessControlMgr() {
				
				public void updateAccessControl() throws RemoteException,
						InternalErrorException {
					agent.updateAccessControl();
				}
			};
		}
		else
			return null;
	}

	public static MailAliasMgr getMailAliasMgr(Object obj) {
		if (obj instanceof MailAliasMgr)
			return (MailAliasMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.MailAliasMgr)
		{
			final es.caib.seycon.ng.sync.intf.MailAliasMgr agent = 
					(es.caib.seycon.ng.sync.intf.MailAliasMgr) obj;
			return new MailAliasMgr() {

				public void updateUserAlias(String alias, User user)
						throws InternalErrorException {
					agent.updateUserAlias(alias, Usuari.toUsuari(user));
				}

				public void removeUserAlias(String alias)
						throws InternalErrorException {
					agent.removeUserAlias(alias);
				}

				public void updateListAlias(MailList list)
						throws InternalErrorException {
					agent.updateListAlias(LlistaCorreu.toLlistaCorreu(list));
				}

				public void removeListAlias(String alias)
						throws InternalErrorException {
					String split[] = alias.split("@");
					if (split.length == 2)
						agent.removeListAlias(split[0], split[1]);
				}
			};
		}
		else
			return null;
	}

	public static NetworkMgr getNetworkMgr(Object obj) {
		if (obj instanceof NetworkMgr)
			return (NetworkMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.NetworkMgr)
		{
			final es.caib.seycon.ng.sync.intf.NetworkMgr agent = 
					(es.caib.seycon.ng.sync.intf.NetworkMgr) obj;
			return new NetworkMgr() {

				public void updateNetworks() throws RemoteException,
						InternalErrorException {
					agent.updateNetworks();
				}

			};
		}
		else
			return null;
	}

	public static HostMgr getHostMgr(Object obj) {
		if (obj instanceof HostMgr)
			return (HostMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.HostMgr)
		{
			final es.caib.seycon.ng.sync.intf.HostMgr agent = 
					(es.caib.seycon.ng.sync.intf.HostMgr) obj;
			return new HostMgr() {

				public void updateHost(Host host) throws RemoteException,
						InternalErrorException {
					agent.updateHost(Maquina.toMaquina(host));
				}

				public void removeHost(String name) throws RemoteException,
						InternalErrorException {
					agent.removeHost(name);
				}

			};
		}
		else
			return null;
	}

	public static RoleMgr getRoleMgr(Object obj) {
		if (obj instanceof RoleMgr)
			return (RoleMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.RoleMgr)
		{
			final es.caib.seycon.ng.sync.intf.RoleMgr agent = 
					(es.caib.seycon.ng.sync.intf.RoleMgr) obj;
			return new RoleMgr() {

				public void updateRole(Role rol) throws RemoteException,
						InternalErrorException {
					agent.updateRole(Rol.toRol(rol));
				}

				public void removeRole(String rolName, String dispatcher)
						throws RemoteException, InternalErrorException {
					agent.removeRole(rolName, dispatcher);
				}

			};
		}
		else
			return null;
	}

	public static GroupMgr getGroupMgr(Object obj) {
		if (obj instanceof GroupMgr)
			return (GroupMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.GroupMgr)
		{
			final es.caib.seycon.ng.sync.intf.GroupMgr agent = 
					(es.caib.seycon.ng.sync.intf.GroupMgr) obj;
			return new GroupMgr() {

				public void updateGroup(Group grup) throws RemoteException,
						InternalErrorException {
					agent.updateGroup(grup.getName(), Grup.toGrup(grup));
					
				}

				public void removeGroup(String key) throws RemoteException,
						InternalErrorException {
					agent.removeGroup(key);
				}
			};
		}
		else
			return null;
	}

	public static SharedFolderMgr getSharedFolderMgr(Object obj) {
		if (obj instanceof SharedFolderMgr)
			return (SharedFolderMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.SharedFolderMgr)
		{
			final es.caib.seycon.ng.sync.intf.SharedFolderMgr agent = 
					(es.caib.seycon.ng.sync.intf.SharedFolderMgr) obj;
			return new SharedFolderMgr() {

				public void createUserFolder(User user) throws RemoteException,
						InternalErrorException {
					agent.createFolder(user.getUserName(), es.caib.seycon.ng.sync.intf.SharedFolderMgr.userFolderType);
				}

				public void createGroupFolder(Group group)
						throws RemoteException, InternalErrorException {
					agent.createFolder(group.getName(), es.caib.seycon.ng.sync.intf.SharedFolderMgr.groupFolderType);
				}
			};
		}
		else
			return null;
	}

	public static AccessLogMgr getAccessLogMgr(Object obj) {
		if (obj instanceof AccessLogMgr)
			return (AccessLogMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.AccessLogMgr)
		{
			final es.caib.seycon.ng.sync.intf.AccessLogMgr agent = 
					(es.caib.seycon.ng.sync.intf.AccessLogMgr) obj;
			return new AccessLogMgr() {

				public Collection<? extends es.caib.seycon.ng.sync.intf.LogEntry> getLogFromDate(
						Date since) throws RemoteException,
						InternalErrorException {
					return agent.getLogFromDate(since);
				}
			};
		}
		else
			return null;
	}

	public static KerberosAgent getKerberosAgent(Object obj) {
		if (obj instanceof KerberosAgent)
			return (KerberosAgent) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.KerberosAgent)
		{
			final es.caib.seycon.ng.sync.intf.KerberosAgent agent = 
					(es.caib.seycon.ng.sync.intf.KerberosAgent) obj;
			return new KerberosAgent() {

				public KerberosPrincipalInfo createServerPrincipal(String server)
						throws InternalErrorException {
					return agent.createServerPrincipal(server);
				}

				public String getRealmName() throws InternalErrorException {
					return agent.getRealmName();
				}

				public String[] getRealmServers() throws InternalErrorException {
					return agent.getRealmServers();
				}

				public String parseKerberosToken(String serverPrincipal, byte[] keytab, byte[] token)
						throws InternalErrorException {
					return agent.parseKerberosToken(serverPrincipal, keytab, token);
				}

				public String findPrincipalAccount(String principal) throws InternalErrorException {
					return agent.findPrincipalAccount(principal);
				}

				@Override
				public String[] getDomainNames() throws InternalErrorException {
					return agent.getDomainNames();
				}

			};
		}
		else
			return null;
	}

	public static ReconcileMgr getReconcileMgr(Object obj) {
		if (obj instanceof ReconcileMgr)
			return (ReconcileMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.ReconcileMgr)
		{
			final es.caib.seycon.ng.sync.intf.ReconcileMgr agent = 
					(es.caib.seycon.ng.sync.intf.ReconcileMgr) obj;
			return new ReconcileMgr() {

				public List<String> getAccountsList() throws RemoteException,
						InternalErrorException {
					return agent.getAccountsList();
				}

				public User getUserInfo(String userAccount)
						throws RemoteException, InternalErrorException {
					return User.toUser(agent.getUserInfo(userAccount));
				}

				public List<String> getRolesList() throws RemoteException,
						InternalErrorException {
					return agent.getRolesList();
				}

				public Role getRoleFullInfo(String roleName)
						throws RemoteException, InternalErrorException {
					return Role.toRole(agent.getRoleFullInfo(roleName));
				}

				public List<Role> getAccountRoles(String userAccount)
						throws RemoteException, InternalErrorException {
					return Role.toRoleList(agent.getAccountRoles(userAccount));
				}

				public List<String[]> getAccountChangesToApply(Account account)
						throws RemoteException, InternalErrorException {
					return agent.getAccountChangesToApply(es.caib.seycon.ng.comu.Account.toAccount(account));
				}

				public List<String[]> getRoleChangesToApply(Role role) throws RemoteException, InternalErrorException {
					return agent.getRoleChangesToApply(Rol.toRol(role));
				}
			};
		}
		else
			return null;
	}

	public static ReconcileMgr2 getReconcileMgr2(Object obj) {
		if (obj instanceof ReconcileMgr2)
			return (ReconcileMgr2) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.ReconcileMgr2)
		{
			final es.caib.seycon.ng.sync.intf.ReconcileMgr2 agent = 
					(es.caib.seycon.ng.sync.intf.ReconcileMgr2) obj;
			return new ReconcileMgr2() {

				public List<String> getAccountsList() throws RemoteException,
						InternalErrorException {
					return agent.getAccountsList();
				}

				public List<String> getRolesList() throws RemoteException,
						InternalErrorException {
					return agent.getRolesList();
				}

				public Role getRoleFullInfo(String roleName)
						throws RemoteException, InternalErrorException {
					return Role.toRole(agent.getRoleFullInfo(roleName));
				}

				public Account getAccountInfo(String userAccount)
						throws RemoteException, InternalErrorException {
					return Account.toAccount(agent.getAccountInfo(userAccount));
				}

				public List<RoleGrant> getAccountGrants(String userAccount)
						throws RemoteException, InternalErrorException {
					return RoleGrant.toRoleGrantList(agent.getAccountGrants(userAccount));
				}
				public List<String[]> getAccountChangesToApply(Account account)
						throws RemoteException, InternalErrorException {
					return agent.getAccountChangesToApply(es.caib.seycon.ng.comu.Account.toAccount(account));
				}

				public List<String[]> getRoleChangesToApply(Role role) throws RemoteException, InternalErrorException {
					return agent.getRoleChangesToApply(Rol.toRol(role));
				}

				public List<HostService> getHostServices() throws RemoteException, InternalErrorException {
					return agent.getHostServices();
				}
			};
		}
		else
			return null;
	}

	public static ServerService getServerService(
			es.caib.seycon.ng.sync.servei.ServerService obj) {

		final es.caib.seycon.ng.sync.servei.ServerService agent = obj;
			return new ServerService() {
				public void cancelTask(long taskid, String hash)
						throws InternalErrorException, InternalErrorException {
					agent.cancelTask(taskid, hash);
				}
				public void changePassword(String account, String dispatcherId,
						com.soffid.iam.api.Password p, boolean mustChange)
						throws InternalErrorException, InternalErrorException,
						BadPasswordException, InternalErrorException {
					agent.changePassword(account, dispatcherId, Password.toPassword(p), mustChange);
				}

				public void changePasswordSync(String account,
						String dispatcherId, com.soffid.iam.api.Password p,
						boolean mustChange) throws InternalErrorException,
						InternalErrorException, BadPasswordException,
						InternalErrorException {
					agent.changePasswordSync(account, dispatcherId, Password.toPassword(p), mustChange);
				}

				public void clientAgentStarted(String AgentName)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					agent.clientAgentStarted(AgentName);
				}

				public com.soffid.iam.api.Password generateFakePassword(
						String account, String dispatcherId)
						throws InternalErrorException, InternalErrorException {
					return Password.toPassword(agent.generateFakePassword(account, dispatcherId));
				}

				public com.soffid.iam.api.Password generateFakePassword(
						String passwordDomain) throws InternalErrorException,
						InternalErrorException {
					return Password.toPassword(agent.generateFakePassword(passwordDomain));
				}

				public Collection<RoleGrant> getAccountExplicitRoles(
						String account, String dispatcherId)
						throws InternalErrorException, InternalErrorException {
					return RoleGrant.toRoleGrantList(agent.getAccountExplicitRoles(account, dispatcherId));
				}

				public Account getAccountInfo(String accountName,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException {
					return Account.toAccount(agent.getAccountInfo(accountName, dispatcherId));
				}

				public com.soffid.iam.api.Password getAccountPassword(
						String account, String dispatcher)
						throws InternalErrorException, InternalErrorException {
					return Password.toPassword(agent.getAccountPassword(account, dispatcher));
				}

				public Collection<RoleGrant> getAccountRoles(String account,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException {
					return RoleGrant.toRoleGrantList(agent.getAccountRoles(account, dispatcherId));
				}

				public byte[] getAddonJar(String addon)
						throws InternalErrorException, InternalErrorException {
					return agent.getAddonJar(addon);
				}

				public List<String> getAddonList()
						throws InternalErrorException, InternalErrorException {
					return agent.getAddonList();
				}

				public String getConfig(String param)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					return agent.getConfig(param);
				}

				public String getDefaultDispatcher()
						throws InternalErrorException, InternalErrorException {
					return agent.getDefaultDispatcher();
				}

				public SystemAccessControl getDispatcherAccessControl(
						Long dispatcherId) throws InternalErrorException,
						InternalErrorException {
					return SystemAccessControl.toSystemAccessControl(agent.getDispatcherAccessControl(dispatcherId));
				}

				public System getDispatcherInfo(String codi)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					return System.toSystem(agent.getDispatcherInfo(codi));
				}

				public Collection<PasswordDomain> getExpiredPasswordDomains(
						User usuari) throws InternalErrorException,
						InternalErrorException {
					return PasswordDomain.toPasswordDomainList(agent.getExpiredPasswordDomains(Usuari.toUsuari(usuari)));
				}
				
				public Collection<Group> getGroupChildren(long groupId,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException, InternalErrorException {
					return Group.toGroupList(agent.getGroupChildren(groupId, dispatcherId));
				}

				public Collection<RoleGrant> getGroupExplicitRoles(long groupId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					return RoleGrant.toRoleGrantList(agent.getGroupExplicitRoles(groupId));
				}

				public Group getGroupInfo(String codi, String dispatcherId)
						throws InternalErrorException, InternalErrorException,
						UnknownGroupException, InternalErrorException {
					return Group.toGroup(agent.getGroupInfo(codi, dispatcherId));
				}

				public Collection<User> getGroupUsers(long groupId,
						boolean nomesUsuarisActius, String dispatcherId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownGroupException {
					return User.toUserList(agent.getGroupUsers(groupId, nomesUsuarisActius, dispatcherId));
				}

				public Host getHostInfo(String hostName)
						throws InternalErrorException, InternalErrorException,
						UnknownHostException, InternalErrorException {
					return Host.toHost(agent.getHostInfo(hostName));
				}

				public Host getHostInfoByIP(String ip)
						throws InternalErrorException, InternalErrorException,
						UnknownHostException, InternalErrorException {
					return Host.toHost(agent.getHostInfoByIP(ip));
				}

				public Collection<Host> getHostsFromNetwork(long networkId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownNetworkException {
					return Host.toHostList(agent.getHostsFromNetwork(networkId));
				}

				public MailList getMailList(String list, String domain)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownMailListException {
					return MailList.toMailList(agent.getMailList(list, domain));
				}

				public Collection<Object> getMailListMembers(String mail,
						String domainName) throws InternalErrorException,
						InternalErrorException, InternalErrorException,
						UnknownMailListException {
					LinkedList<Object> result = new LinkedList<Object>();
					for (Object obj: agent.getMailListMembers(mail, domainName))
					{
						if (obj instanceof Usuari)
							result.add (User.toUser((Usuari) obj));
						else 
							result.add(obj);
					}
					return result;
				}

				public void getMainJar() throws InternalErrorException,
						InternalErrorException {
					agent.getMainJar();
				}

				public Properties getMyConfig() throws InternalErrorException,
						InternalErrorException, ServerRedirectException {
					return agent.getMyConfig();
				}

				public Network getNetworkInfo(String network)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownNetworkException {
					return Network.toNetwork(agent.getNetworkInfo(network));
				}

				public Collection<Network> getNetworksList()
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					return Network.toNetworkList(agent.getNetworksList());
				}

				public com.soffid.iam.api.Password getOrGenerateUserPassword(
						String account, String dispatcherId)
						throws InternalErrorException, InternalErrorException {
					return Password.toPassword(agent.getOrGenerateUserPassword(account, dispatcherId));
				}

				public Plugin getPlugin(String className)
						throws InternalErrorException, InternalErrorException {
					return Plugin.toPlugin(agent.getPlugin(className));
				}

				public byte[] getPluginJar(String classname)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					return agent.getPluginJar(classname);
				}

				public Collection<Account> getRoleAccounts(long roleId,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException, InternalErrorException,
						UnknownRoleException {
					return Account.toAccountList(agent.getRoleAccounts(roleId, dispatcherId));
				}

				public Collection<Account> getRoleActiveAccounts(long roleId,
						String dispatcher) throws InternalErrorException,
						InternalErrorException, InternalErrorException,
						UnknownRoleException {
					return Account.toAccountList(agent.getRoleActiveAccounts(roleId, dispatcher));
				}

				public Collection<RoleGrant> getRoleExplicitRoles(long roleId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownRoleException {
					return RoleGrant.toRoleGrantList(agent.getRoleExplicitRoles(roleId));
				}

				public Role getRoleInfo(String role, String bd)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownRoleException {
					return Role.toRole(agent.getRoleInfo(role, bd));
				}

				public Collection<UserAccount> getUserAccounts(long userId,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException {
					return UserAccount.toUserAccountList(agent.getUserAccounts(userId, dispatcherId));
				}

				public UserData getUserData(long userId, String data)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return UserData.toUserData(agent.getUserData(userId, data));
				}

				public Collection<UserData> getUserData(long userId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return UserData.toUserDataList(agent.getUserData(userId));
				}

				public Collection<RoleGrant> getUserExplicitRoles(long userId,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException, InternalErrorException,
						UnknownUserException {
					return RoleGrant.toRoleGrantList(agent.getUserExplicitRoles(userId, dispatcherId));
				}

				public Collection<Group> getUserGroups(Long userId)
						throws InternalErrorException, InternalErrorException {
					return Group.toGroupList(agent.getUserGroups(userId));
				}

				public Collection<Group> getUserGroups(String accountName,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException, InternalErrorException,
						UnknownUserException {
					return Group.toGroupList(agent.getUserGroups(accountName, dispatcherId));
				}

				public Collection<Group> getUserGroupsHierarchy(
						String accountName, String dispatcherId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return Group.toGroupList(agent.getUserGroupsHierarchy(accountName, dispatcherId));
				}

				public Collection<Group> getUserGroupsHierarchy(Long userId)
						throws InternalErrorException, InternalErrorException {
					return Group.toGroupList(agent.getUserGroupsHierarchy(userId));
				}

				public User getUserInfo(long userId)
						throws InternalErrorException, InternalErrorException {
					return User.toUser(agent.getUserInfo(userId));
				}

				public User getUserInfo(String account, String dispatcherId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return User.toUser(agent.getUserInfo(account, dispatcherId));
				}

				public User getUserInfo(X509Certificate[] certs)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException {
					return User.toUser(agent.getUserInfo(certs));
				}

				public byte[] getUserMazingerRules(long userId, String version)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return agent.getUserMazingerRules(userId, version);
				}

				public PasswordPolicy getUserPolicy(String account,
						String dispatcherId) throws InternalErrorException,
						InternalErrorException {
					return PasswordPolicy.toPasswordPolicy(agent.getUserPolicy(account, dispatcherId));
				}

				public Collection<PrinterUser> getUserPrinters(Long userId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return PrinterUser.toPrinterUserList(agent.getUserPrinters(userId));
				}

				public Collection<RoleGrant> getUserRoles(long userId,
						String dispatcherid) throws InternalErrorException,
						InternalErrorException, InternalErrorException,
						UnknownUserException {
					return RoleGrant.toRoleGrantList(agent.getUserRoles(userId, dispatcherid));
				}

				public Collection<Secret> getUserSecrets(long userId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException {
					return Secret.toSecretList(agent.getUserSecrets(userId));
				}

				public boolean hasSupportAccessHost(long hostId, long userId)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, UnknownUserException,
						UnknownHostException {
					return agent.hasSupportAccessHost(hostId, userId);
				}

				public Map propagateOBUser(User usuari)
						throws InternalErrorException, InternalErrorException {
					return agent.propagateOBUser(Usuari.toUsuari(usuari));
				}

				public String reverseTranslate(String domain, String column2)
						throws InternalErrorException {
					return agent.reverseTranslate(domain, column2);
				}

				public Collection<AttributeTranslation> reverseTranslate2(
						String domain, String column2)
						throws InternalErrorException {
					return agent.reverseTranslate2(domain, column2);
				}

				public String translate(String domain, String column1)
						throws InternalErrorException {
					return agent.translate(domain, column1);
				}

				public Collection<AttributeTranslation> translate2(
						String domain, String column1)
						throws InternalErrorException {
					return agent.translate2(domain, column1);
				}

				public boolean updateExpiredPasswords(User usuari,
						boolean externalAuth) throws InternalErrorException,
						InternalErrorException {
					return agent.updateExpiredPasswords(Usuari.toUsuari(usuari), externalAuth);
				}

				public PasswordValidation validatePassword(String account,
						String dispatcherId, com.soffid.iam.api.Password p)
						throws InternalErrorException, InternalErrorException,
						InternalErrorException, InvalidPasswordException,
						UnknownUserException {
					return PasswordValidation.valueOf( agent.validatePassword(account, dispatcherId, Password.toPassword(p)).toString());
				}

				public CustomObject getCustomObject(String type, String name) throws InternalErrorException {
					return agent.getCustomObject(type, name);
				}

				public Map<String, Object> getUserAttributes(long userId) throws InternalErrorException,
						InternalErrorException, InternalErrorException, UnknownUserException {
					return agent.getUserAttributes(userId);
				}
				public Collection<Map<String, Object>> invoke(String agentName, String verb, String command,
						Map<String, Object> params) throws InternalErrorException, InternalErrorException {
					return agent.invoke (agentName, verb, command, params);
				}
				public Server findRemoteServerByUrl(String url) throws InternalErrorException {
					return  Server.toServer( agent.findRemoteServerByUrl(url) );
				}
				public Account parseKerberosToken(String domain, String serviceName, byte[] keytab, byte[] token)
						throws InternalErrorException, InternalErrorException {
					return Account.toAccount( agent.parseKerberosToken(domain, serviceName, keytab, token) );
				}
				public void processAuthoritativeChange(AuthoritativeChange change, boolean remove)
						throws InternalErrorException {
					agent.processAuthoritativeChange(es.caib.seycon.ng.sync.intf.AuthoritativeChange.toAuthoritativeChange(change), remove);
					
				}
				public void reconcileAccount(String system, String account) throws InternalErrorException {
					agent.reconcileAccount(system, account);
				}
				public Collection<System> getServices() throws InternalErrorException {
					Collection<Dispatcher> d = agent.getServices();
					return System.toSystemList(d);
				}
				
				public Collection<GroupUser> getUserMemberships(String accountName, String dispatcherId) 
						throws InternalErrorException, InternalErrorException, InternalErrorException,
						UnknownUserException {
					return GroupUser.toGroupUserList( agent.getUserMemberships(accountName, dispatcherId));
				}
				@Override
				public void reconcileAccount(Account account, List<RoleAccount> grants) throws InternalErrorException {
					agent.reconcileAccount(es.caib.seycon.ng.comu.Account.toAccount(account), RolAccount.toRolAccountList(grants));
				}
				@Override
				public void addCertificate(X509Certificate cert) throws InternalErrorException {
					agent.addCertificate(cert);
				}
			};
	}

	public static ExtensibleObjectFinder getExtensibleObjectFinder(
			es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder obj) {
		final es.caib.seycon.ng.sync.engine.extobj.ExtensibleObjectFinder agent = obj;
		return new ExtensibleObjectFinder() {
			
			public ExtensibleObject find(ExtensibleObject pattern) throws Exception {
				return agent.find( es.caib.seycon.ng.sync.intf.ExtensibleObject.toExtensibleObject(pattern));
			}

			public Collection<Map<String, Object>> invoke(String verb, String command, Map<String, Object> params)
					throws InternalErrorException {
				return agent.invoke(verb, command, params);
			}
		};
	}

	public static CustomObjectMgr getCustomObjectMgr(Object agent) {
		return (CustomObjectMgr) agent;
	}

	public static ExtensibleObjectMgr getExtensibleObjectMgr(Object obj) {
		if (obj instanceof ExtensibleObjectMgr)
			return (ExtensibleObjectMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.ExtensibleObjectMgr)
		{
			final es.caib.seycon.ng.sync.intf.ExtensibleObjectMgr agent = 
					(es.caib.seycon.ng.sync.intf.ExtensibleObjectMgr) obj;
			return new ExtensibleObjectMgr() {

				public void configureMappings(Collection<com.soffid.iam.sync.intf.ExtensibleObjectMapping> objects)
						throws RemoteException, InternalErrorException {
					agent.configureMappings(ExtensibleObjectMapping.toExtensibleObjectMappingList(objects));
					
				}

				public ExtensibleObject getNativeObject(SoffidObjectType type, String object1, String object2) throws RemoteException, InternalErrorException {
					Class<?> cl = agent.getClass();
					while (cl != null) {
						java.lang.System.out.println(cl.getName());
						for (Method m: cl.getMethods()) {
							java.lang.System.out.println(m.toString());
						}
						cl = cl.getSuperclass();
					}
					ExtensibleObject o = es.caib.seycon.ng.sync.intf.ExtensibleObject.toExtensibleObject( agent.getNativeObject(
							es.caib.seycon.ng.comu.SoffidObjectType.fromString(type.getValue()), 
							object1, object2));
					return o;
				}

				public ExtensibleObject getSoffidObject(SoffidObjectType type, String object1, String object2) throws RemoteException, InternalErrorException {
					ExtensibleObject o = es.caib.seycon.ng.sync.intf.ExtensibleObject.toExtensibleObject( agent.getSoffidObject(
							es.caib.seycon.ng.comu.SoffidObjectType.fromString(type.getValue()), 
							object1, object2));
					return o;
				}

				public Collection<Map<String, Object>> invoke(String verb, String command, Map<String, Object> params)
						throws RemoteException, InternalErrorException {
					return agent.invoke(verb, command, params);
				}

				@Override
				public void updateExtensibleObject(ExtensibleObject obj)
						throws RemoteException, InternalErrorException {
					agent.updateExtensibleObject(  obj);
				}

				@Override
				public void removeExtensibleObject(ExtensibleObject obj)
						throws RemoteException, InternalErrorException {
					agent.removeExtensibleObject(  obj);
				}

			};
		}
		else
			return null;
	}
	
	public static CustomTaskMgr getCustomTaskMgr (Object obj) {
		if (obj instanceof CustomTaskMgr)
			return (CustomTaskMgr) obj;
		else if ( obj instanceof es.caib.seycon.ng.sync.intf.CustomTaskMgr)
		{
			final es.caib.seycon.ng.sync.intf.CustomTaskMgr agent = 
					(es.caib.seycon.ng.sync.intf.CustomTaskMgr) obj;
			return new CustomTaskMgr() {

				public void processTask(Task task) throws RemoteException, InternalErrorException {
					agent.processTask( Tasca.toTasca(task));
				}

			};
		}
		else
			return null;
		
	}

	public static ServiceMgr getServiceMgr(Object obj) {
		 return obj instanceof ServiceMgr ? (ServiceMgr) obj: null;	
	}
}

package com.soffid.iam.sync.service;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.AttributeTranslation;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.MailList;
import com.soffid.iam.api.Network;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordDomain;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.PrinterUser;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SystemAccessControl;
import com.soffid.iam.api.Task;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.UserData;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.AccessControlEntity;
import com.soffid.iam.model.AccessControlEntityDao;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.AgentDescriptorEntity;
import com.soffid.iam.model.AuthorizationEntity;
import com.soffid.iam.model.AuthorizationEntityDao;
import com.soffid.iam.model.ConfigEntity;
import com.soffid.iam.model.EmailListContainerEntity;
import com.soffid.iam.model.EmailListEntity;
import com.soffid.iam.model.EmailListEntityDao;
import com.soffid.iam.model.EntryPointEntity;
import com.soffid.iam.model.EntryPointEntityDao;
import com.soffid.iam.model.ExternEmailEntity;
import com.soffid.iam.model.GroupEntity;
import com.soffid.iam.model.GroupEntityDao;
import com.soffid.iam.model.HostEntity;
import com.soffid.iam.model.NetworkAuthorizationEntity;
import com.soffid.iam.model.NetworkEntity;
import com.soffid.iam.model.NetworkEntityDao;
import com.soffid.iam.model.Parameter;
import com.soffid.iam.model.PasswordDomainEntity;
import com.soffid.iam.model.PasswordPolicyEntity;
import com.soffid.iam.model.RoleDependencyEntityDao;
import com.soffid.iam.model.RoleEntity;
import com.soffid.iam.model.RoleEntityDao;
import com.soffid.iam.model.RoleGroupEntity;
import com.soffid.iam.model.RoleGroupEntityDao;
import com.soffid.iam.model.SystemEntity;
import com.soffid.iam.model.SystemEntityDao;
import com.soffid.iam.model.UserAccountEntity;
import com.soffid.iam.model.UserDataEntity;
import com.soffid.iam.model.UserDataEntityDao;
import com.soffid.iam.model.UserDomainEntityDao;
import com.soffid.iam.model.UserEmailEntity;
import com.soffid.iam.model.UserEntity;
import com.soffid.iam.model.UserEntityDao;
import com.soffid.iam.model.UserGroupEntity;
import com.soffid.iam.model.UserGroupEntityDao;
import com.soffid.iam.model.UserPrinterEntityDao;
import com.soffid.iam.service.CertificateValidationService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.agent.Plugin;
import com.soffid.iam.sync.bootstrap.ConfigurationManager;
import com.soffid.iam.sync.bootstrap.JarExtractor;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.sync.service.ServerServiceBase;
import com.soffid.iam.sync.service.server.Compile;
import com.soffid.iam.sync.service.server.Compile2;
import com.soffid.iam.sync.service.server.Compile3;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.ServerRedirectException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownNetworkException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.utils.Security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

public class ServerServiceImpl extends ServerServiceBase {
    Logger log = Log.getLogger("ServerServiceImpl"); //$NON-NLS-1$

    @Override
    protected User handleGetUserInfo(String user, String dispatcherId)
            throws Exception {
        UserDomainEntityDao duDao = getUserDomainEntityDao();
        UserEntityDao dao = getUserEntityDao();

        UserEntity entity = null;

        if (dispatcherId == null) {
            entity = dao.findByUserName(user);
            if (entity == null)
                throw new UnknownUserException(user);
        } else {
        	String codi = null;
            
        	AccountEntity acc = getAccountEntityDao().findByNameAndSystem(user, dispatcherId);
        	
        	if (acc == null)
        		throw new UnknownUserException();
        	
   			if (acc.getType().equals(AccountType.USER) )
   			{
   				for (UserAccountEntity uac: acc.getUsers())
   				{
   					entity = uac.getUser();
   				}
   			}
   			if (entity == null)
   				throw new UnknownUserException();
        }
		return getUserEntityDao().toUser(entity);
    }

    private com.soffid.iam.api.System getSystem(String dispatcherId) throws InternalErrorException {
    	DispatcherHandler handler = getTaskGenerator().getDispatcher(dispatcherId);
    	if (handler == null)
    		return null;
    	else
    		return handler.getSystem();
    	
	}



    @Override
    protected Collection<User> handleGetGroupUsers(long groupId, boolean nomesUsersActius,
            String dispatcherId) throws Exception {
        com.soffid.iam.api.System dispatcher = null;
        
        if (dispatcherId != null) {
        	dispatcher = getSystem(dispatcherId);
        }

        GroupEntityDao daoGroup = getGroupEntityDao();
        UserEntityDao daoUser = getUserEntityDao();
        UserGroupEntityDao daoUserGroup = getUserGroupEntityDao();
        GroupEntity entity = daoGroup.load(groupId);
        if (entity == null)
            throw new UnknownGroupException(Long.toString(groupId));

        Collection<User> result = new LinkedList<User>();
        for (Iterator<UserEntity> it = daoUser.findByPrimaryGroup(entity.getName()).iterator(); it.hasNext(); ) {
            UserEntity usuariEntity = it.next();
            if (!nomesUsersActius || "S".equals(usuariEntity.getActive())) {
                if (dispatcher == null || getDispatcherService().isUserAllowed(dispatcher, usuariEntity.getUserName())) result.add(daoUser.toUser(usuariEntity));
            }
        }

        for (Iterator<UserGroupEntity> it = daoUserGroup.findByGroupName(entity.getName()).iterator(); it.hasNext(); ) {
            UserGroupEntity ugEntity = it.next();
            if (!nomesUsersActius || "S".equals(ugEntity.getUser().getActive())) {
                if (dispatcher == null || getDispatcherService().isUserAllowed(dispatcher, ugEntity.getUser().getUserName())) result.add(daoUser.toUser(ugEntity.getUser()));
            }
        }

        return result;
    }


    @Override
    protected Collection<Account> handleGetRoleAccounts(long roleId, String dispatcherId)
            throws Exception {
    	List<Account> acc = new LinkedList<Account>();
    	Collection<RoleGrant> rgs = getApplicationService().findEffectiveRoleGrantsByRoleId(roleId);
    	Date now = new Date();
    	for (RoleGrant rg : rgs) {
            if ((rg.getStartDate() == null || now.after(rg.getStartDate())) && (rg.getEndDate() == null || now.before(rg.getEndDate()))) {
                AccountEntity account = getAccountEntityDao().findByNameAndSystem(rg.getOwnerAccountName(), rg.getOwnerSystem());
                if (account.getUsers().isEmpty()) acc.add(getAccountEntityDao().toAccount(account)); else for (UserAccountEntity uae : account.getUsers()) acc.add(getUserAccountEntityDao().toUserAccount(uae));
            }
        }
    	return acc;
    }

    @Override
    protected Collection<Account> handleGetRoleActiveAccounts(long roleId, String dispatcherId)
            throws Exception {
    	List<Account> acc = new LinkedList<Account>();
    	Collection<RoleGrant> rgs = getApplicationService().findEffectiveRoleGrantsByRoleId(roleId);
    	for (RoleGrant rg : rgs) {
            AccountEntity account = getAccountEntityDao().findByNameAndSystem(rg.getOwnerAccountName(), rg.getOwnerSystem());
            if (!account.isDisabled()) {
                if (account.getUsers().isEmpty()) acc.add(getAccountEntityDao().toAccount(account)); else for (UserAccountEntity uae : account.getUsers()) acc.add(getUserAccountEntityDao().toUserAccount(uae));
            }
        }
    	return acc;
    }

    @Override
    protected Collection<RoleGrant> handleGetRoleExplicitRoles(long roleId) throws Exception {
        RoleEntityDao rolDao = getRoleEntityDao();
        RoleDependencyEntityDao rarDao = getRoleDependencyEntityDao();

        RoleEntity rol = rolDao.load(roleId);
        if (rol == null)
            throw new UnknownRoleException();

        return rarDao.toRoleGrantList(rol.getContainedRole());
    }

    @Override
    protected Collection<Group> handleGetUserGroups(String accountName, String dispatcherId) throws Exception {
        HashMap<String, GroupEntity> grups = getUserGroupsMap(accountName, dispatcherId);
        return getGroupEntityDao().toGroupList(grups.values());
    }

	private HashMap<String, GroupEntity> getUserGroupsMap(String accountName, String dispatcherId) throws UnknownUserException, InternalErrorException {
		UserEntityDao dao = getUserEntityDao();
    	GroupEntityDao grupDao = getGroupEntityDao();
        HashMap<String, GroupEntity> grups = new HashMap<String, GroupEntity>();

        if (dispatcherId == null)
        {
        	UserEntity user = dao.findByUserName(accountName);
        	if (!grups.containsKey(user.getPrimaryGroup().getName()))
        		grups.put(user.getPrimaryGroup().getName(), user.getPrimaryGroup());
	        for (Iterator<UserGroupEntity> it = user.getSecondaryGroups().iterator(); it.hasNext(); ) {
                UserGroupEntity uge = it.next();
                if (!grups.containsKey(uge.getGroup().getName())) grups.put(uge.getGroup().getName(), uge.getGroup());
            }
        }
        else
        {
	        AccountEntity account = getAccountEntityDao().findByNameAndSystem(accountName, dispatcherId); 
	        if (account == null)
	            throw new UnknownUserException(accountName+"/"+dispatcherId); //$NON-NLS-1$
	
	        if (account.getType().equals (AccountType.USER))
	        {
	        	com.soffid.iam.api.System dispatcher = getSystem (dispatcherId);
		        for (UserAccountEntity ua : account.getUsers()) {
                    UserEntity user = ua.getUser();
                    if (getDispatcherService().isGroupAllowed(dispatcher, user.getPrimaryGroup().getName())) {
                        if (!grups.containsKey(user.getPrimaryGroup().getName())) grups.put(user.getPrimaryGroup().getName(), user.getPrimaryGroup());
                    }
                    for (Iterator<UserGroupEntity> it = user.getSecondaryGroups().iterator(); it.hasNext(); ) {
                        UserGroupEntity uge = it.next();
                        if (getDispatcherService().isGroupAllowed(dispatcher, uge.getGroup().getName())) if (!grups.containsKey(uge.getGroup().getName())) grups.put(uge.getGroup().getName(), uge.getGroup());
                    }
                }
	        }
        }
		return grups;
	}

	private void testInclusion(com.soffid.iam.api.System dispatcher, UserEntity entity) throws InternalErrorException, UnknownUserException {
		if (dispatcher != null && !getDispatcherService().isUserAllowed(dispatcher, entity.getUserName()))
        	throw new UnknownUserException();
	}

    @Override
    protected Collection<Group> handleGetUserGroupsHierarchy(String accountName, String dispatcherId)
            throws Exception {
        HashMap<String, GroupEntity> grups = getUserGroupsMap(accountName, dispatcherId);
        LinkedList<Group> values = new LinkedList<Group>();
        HashSet<String> keys = new HashSet<String>(grups.keySet());
        GroupEntityDao grupDao = getGroupEntityDao();
        for (GroupEntity grup : grups.values()) {
            while (grup != null && !keys.contains(grup.getName())) {
                keys.add(grup.getName());
                values.add(grupDao.toGroup(grup));
                grup = grup.getParent();
            }
            ;
        }

        return values;
    }


    @Override
    protected Collection<RoleGrant> handleGetUserRoles(long userId, String dispatcherid)
            throws Exception {
    	UserEntity user = getUserEntityDao().load(new Long(userId));
    	List<AccountEntity> accounts = null;
    	if (dispatcherid == null)
    	{
    		accounts = new LinkedList<AccountEntity>();
    		for (UserAccountEntity ua: user.getAccounts())
    		{
    			if (ua.getAccount().getType().equals(AccountType.USER))
    				accounts.add (ua.getAccount());
    		}
    	}
    	else
    		accounts = getAccountEntityDao().findByUserAndSystem(user.getUserName(), dispatcherid);
    	
    	Collection<RoleGrant> grants = new LinkedList<RoleGrant>();
		Date now = new Date();
    	for (AccountEntity account: accounts)
    	{
    		Collection<RoleGrant> partialGrants = getApplicationService().findRoleGrantByAccount(account.getId());
    		for (RoleGrant rg: partialGrants)
    		{
   				grants.add (rg);
    		}
    	}
        return grants;
    }

    @Override
    protected Collection<RoleGrant> handleGetGroupExplicitRoles(long groupId) throws Exception {
        GroupEntityDao grupDao = getGroupEntityDao();
        GroupEntity grup = grupDao.load(groupId);
        RoleGroupEntityDao rgDao = getRoleGroupEntityDao();
        Collection<RoleGroupEntity> rols = grup.getAllowedRolesToGroup();
        return rgDao.toRoleGrantList(rols);
    }

    @Override
    protected Collection<RoleGrant> handleGetUserExplicitRoles(long userId, String dispatcher)
            throws Exception {
    	Collection<RoleGrant> rg = getApplicationService().findEffectiveRoleGrantByUser(userId);
    	if (dispatcher != null)
    	{
    		for (Iterator<RoleGrant> it = rg.iterator(); it.hasNext();)
    		{
    			RoleGrant grant = it.next();
    			if (! dispatcher.equals (grant.getSystem()))
    				it.remove();
    		}
    	}
        return rg;
    }

    @Override
    protected UserData handleGetUserData(long userId, String data) throws Exception {
        UserDataEntityDao dao = getUserDataEntityDao();

        UserEntity usuari = getUserEntityDao().load(userId);

        UserDataEntity dataEntity = dao.findByDataType(usuari.getUserName(), data);
        if (dataEntity == null)
            return null;
        else
            return dao.toUserData(dataEntity);

    }

    @Override
    protected Collection<PrinterUser> handleGetUserPrinters(Long userId) throws Exception {
        UserEntity user = getUserEntityDao().load(userId);
        if (user == null)
            throw new UnknownUserException(userId.toString());
        UserPrinterEntityDao dao = getUserPrinterEntityDao();
        return dao.toPrinterUserList(user.getPrinters());
    }

    @Override
    protected Host handleGetHostInfo(String hostName) throws Exception {
        HostEntity host = getHostEntityDao().findByName(hostName);
        if (host == null)
            throw new UnknownHostException(hostName);
        return getHostEntityDao().toHost(host);
    }

    @Override
    protected Host handleGetHostInfoByIP(String ip) throws Exception {
        HostEntity host = getHostEntityDao().findByIP(ip);
        if (host == null)
            throw new UnknownHostException(ip);
        return getHostEntityDao().toHost(host);
    }

    @Override
    protected Network handleGetNetworkInfo(String network) throws Exception {
        NetworkEntity xarxa = getNetworkEntityDao().findByName(network);
        if (xarxa == null)
            throw new UnknownNetworkException(network);
        return getNetworkEntityDao().toNetwork(xarxa);
    }

    @Override
    protected Collection<Group> handleGetGroupChildren(long groupId, String dispatcherId)
            throws Exception {
        com.soffid.iam.api.System dispatcher = null;
        if (dispatcherId != null)
            dispatcher = getSystem(dispatcherId);
        
        DispatcherService disSvc = getDispatcherService();

        GroupEntityDao dao = getGroupEntityDao();
        GroupEntity entity = dao.load(groupId);
        if (entity == null)
            throw new UnknownGroupException(Long.toString(groupId));
        LinkedList<Group> grups = new LinkedList<Group>();
        for (Iterator<GroupEntity> it = entity.getChildrens().iterator(); it.hasNext(); ) {
            GroupEntity ge = it.next();
            if (dispatcher == null || disSvc.isGroupAllowed(dispatcher, ge.getName())) grups.add(dao.toGroup(ge));
        }
        return grups;
    }

    @Override
    protected Role handleGetRoleInfo(String role, String bd) throws Exception {
        RoleEntityDao dao = getRoleEntityDao();
        RoleEntity rolEntity = dao.findByNameAndSystem(role, bd);
        if (rolEntity != null)  
            return dao.toRole(rolEntity);
        else
            return null;
    }

    @Override
    protected Collection<Network> handleGetNetworksList() throws Exception {
        NetworkEntityDao dao = getNetworkEntityDao();
        return dao.toNetworkList(dao.loadAll());
    }

    @Override
    protected void handleClientAgentStarted(String agentName) throws Exception {
        for (Iterator<DispatcherHandler> it = getTaskGenerator().getDispatchers().iterator(); it
                .hasNext();) {
            DispatcherHandler d = it.next();
            if (d != null)
            {
                try {
                	if ( ! d.isConnected())
                		d.reconfigure();
                	else
                	{
                    	URL url = new URL (d.getSystem().getUrl());
                    	if (url.getHost().equals(agentName))
                    	{
                    		d.reconfigure();
                    	}
                	}
                } catch (Exception e) 
                {
                	
                }
            }
        }
    }

    @Override
    protected Collection<Host> handleGetHostsFromNetwork(long networkId) throws Exception {
        NetworkEntity xarxa = getNetworkEntityDao().load(networkId);
        if (xarxa == null)
            throw new UnknownNetworkException(Long.toString(networkId));
        return getHostEntityDao().toHostList(xarxa.getHosts());
    }

    @Override
    protected String handleGetConfig(String param) throws Exception {
        Invoker invoker = Invoker.getInvoker();
        NetworkEntity xarxa = null;

        if (invoker != null) {
            NetworkEntityDao dao = getNetworkEntityDao();
            InetAddress addr = invoker.getAddr();
            byte b[] = addr.getAddress();
            for (int bc = b.length - 1; xarxa == null && bc >= 0; bc--) {
                byte mascara = (byte) 255;
                for (int bits = 0; xarxa == null && bits < 8; bits++) {
                    mascara = (byte) (mascara << 1);
                    b[bc] = (byte) (b[bc] & mascara);
                    InetAddress addr2 = InetAddress.getByAddress(b);
                    String addrText = addr2.getHostAddress();
                    xarxa = dao.findByAddress(addrText);
                }
            }
        }
        ConfigEntity config;
        if (xarxa == null)
            config = getConfigEntityDao().findByCodeAndNetworkCode(param, null);
        else {
            config = getConfigEntityDao().findByCodeAndNetworkCode(param, xarxa.getName());
            if (config == null)
                config = getConfigEntityDao().findByCodeAndNetworkCode(param, null);
        }

        if (config == null)
            return null;
        else
            return config.getValue();

    }

    @Override
    protected MailList handleGetMailList(String list, String domain) throws Exception {
        EmailListEntityDao dao = getEmailListEntityDao();
        EmailListEntity entity = dao.findByNameAndDomain(list, domain);
        if (entity != null) {
            return dao.toMailList(entity);
        }
        
            UserEntity usuari = getUserForMailList(list, domain);
            MailList llista = new MailList();
            llista.setId(null);
            llista.setDomainCode(domain);
            llista.setName(list);
            llista.setUsersList(usuari.getUserName());
            llista.setDescription(usuari.getFirstName() + " " + usuari.getLastName() + " " + usuari.getMiddleName()); //$NON-NLS-1$ //$NON-NLS-2$
            return llista;
    }

    private UserEntity getUserForMailList(String list, String domain) throws UnknownMailListException {
        List<UserEntity> usuaris;
        if (domain == null)
            usuaris = getUserEntityDao().query("select usuari "
            		+ "from com.soffid.iam.model.UserEntity as usuari "
            		+ "where usuari.shortName=:nomCurt and usuari.mailDomain is null", 
            		new Parameter[]{new Parameter("nomCurt", list)});
        else
            usuaris = getUserEntityDao().query("select usuari "
            		+ "from com.soffid.iam.model.UserEntity as usuari "
            		+ "join usuari.mailDomain as domini with domini.name=:domini "
            		+ "where usuari.shortName=:nomCurt", 
            		new Parameter[]{new Parameter("domini", domain), 
            				new Parameter("nomCurt", list)});
        if (usuaris == null || usuaris.isEmpty())
            throw new UnknownMailListException(list + "@" + domain); //$NON-NLS-1$
        UserEntity usuari = usuaris.get(0);
        return usuari;
    }

    @Override
    protected Collection<Object> handleGetMailListMembers(String list, String domain)
            throws Exception {
        LinkedList<Object> members = new LinkedList<Object>();
        EmailListEntityDao dao = getEmailListEntityDao();
        EmailListEntity entity = dao.findByNameAndDomain(list, domain);
        if (entity != null)
        {
    
            for (Iterator<UserEmailEntity> it = entity.getUserMailLists().iterator(); it.hasNext(); ) {
                UserEmailEntity lcu = it.next();
                members.add(getUserEntityDao().toUser(lcu.getUser()));
            }
    
            for (Iterator<ExternEmailEntity> it = entity.getExternals().iterator(); it.hasNext(); ) {
                ExternEmailEntity lcu = it.next();
                members.add(lcu.getAddress());
            }
    
            for (Iterator<EmailListContainerEntity> it = entity.getMailListContent().iterator(); it.hasNext(); ) {
                EmailListContainerEntity lcu = it.next();
                members.add(dao.toMailList(lcu.getPertains()));
            }
            return members;
        }
        UserEntity usuari = getUserForMailList(list, domain);
        members.add(getUserEntityDao().toUser(usuari));
        return members;
    }

    @Override
    protected com.soffid.iam.api.System handleGetDispatcherInfo(String codi) throws Exception {
        SystemEntityDao dao = getSystemEntityDao();
        return dao.toSystem(dao.findByName(codi));
    }

    private boolean hasAuthorization(Collection<RoleGrant> roles, String authorization)
            throws InternalErrorException, UnknownUserException {
        AuthorizationEntityDao autDao = getAuthorizationEntityDao();
        for (Iterator<AuthorizationEntity> it = autDao.findByAuthorization(authorization).iterator(); it.hasNext(); ) {
            AuthorizationEntity aut = it.next();
            for (Iterator<RoleGrant> itGrant = roles.iterator(); itGrant.hasNext(); ) {
                RoleGrant grant = itGrant.next();
                if (grant.getRoleId().equals(aut.getRole().getId())) return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleHasSupportAccessHost(long hostId, long userId) throws Exception {
        // Comprovar si to el roll de suport a totes les maquines
        Collection<RoleGrant> roles = getUserRoles(userId, null);
        if (hasAuthorization(roles, Security.AUTO_HOST_ALL_SUPPORT_VNC))
            return true;

        // Comprovar si existeix una ACL per a ell
        boolean found = true;
        UserEntity usuariEntity = getUserEntityDao().load(userId);
        Collection<Group> grups = getUserGroups(usuariEntity.getUserName(), null);
        grups.addAll(getUserGroupsHierarchy(usuariEntity.getUserName(), null));
        HostEntity maq = getHostEntityDao().load(hostId);
        NetworkEntity xarxa = maq.getNetwork();
        for (Iterator<NetworkAuthorizationEntity> it = xarxa.getAuthorizations().iterator(); !found && it.hasNext(); ) {
            NetworkAuthorizationEntity ace = it.next();
            if (ace.getLevel() >= 1 && Pattern.matches(ace.getHostsName(), maq.getName())) {
                if (ace.getRole() != null) {
                    for (Iterator<RoleGrant> itGrant = roles.iterator(); !found && itGrant.hasNext(); ) {
                        RoleGrant grant = itGrant.next();
                        if (grant.getRoleId().equals(ace.getRole().getId())) found = true;
                    }
                }
                if (ace.getUser() != null && ace.getUser().getId().longValue() == userId) found = true;
                if (ace.getGroup() != null) {
                    for (Iterator<Group> itGroup = grups.iterator(); itGroup.hasNext(); ) {
                        Group grup = itGroup.next();
                        if (grup.getId().equals(ace.getGroup().getId())) found = true;
                    }
                }
            }
        }
        return found;
    }

    @Override
    protected byte[] handleGetPluginJar(String classname) throws Exception {
        AgentDescriptorEntity entity = getAgentDescriptorEntityDao().findByClass(classname);
        if (entity == null) throw new InternalErrorException(Messages.getString("ServerServiceImpl.pluginNotFound")+classname); //$NON-NLS-1$
        if (entity.getModule() == null && entity.getPlugin() != null)
        	return entity.getPlugin().getContent();
        else
        	return entity.getModule().getContents();
    }

    @Override
    protected User handleGetUserInfo(X509Certificate certs[]) throws Exception {
        UserService usuariService = ServerServiceLocator.instance().getUserService();
        CertificateValidationService certificateService = ServerServiceLocator.instance().getCertificateValidationService();
        
        ArrayList<X509Certificate> certList = new ArrayList<X509Certificate>(certs.length);
        for (X509Certificate cert: certs) certList.add(cert);
        
        if (!certificateService.validateCertificate(certList))
        {
        	throw new InternalErrorException (String.format(Messages.getString("ServerServiceImpl.invalidCertificate"), certs[0].getSubjectX500Principal().getName())); //$NON-NLS-1$
        }
        
        User usuari = certificateService.getCertificateUser(certList);
        if (usuari != null)
        	return usuari;
        
        String codi = usuariService.addUser(Arrays.asList(certs) , "E"); //$NON-NLS-1$

        UserEntity entity = getUserEntityDao().findByUserName(codi);

        return getUserEntityDao().toUser(entity);
    }

    @Override
    protected PasswordValidation handleValidatePassword(String account, String dispatcher, Password p)
            throws Exception {
        UserEntity userEntity;
        PasswordDomainEntity dc;
        if (dispatcher == null) {
        	userEntity = getUserEntityDao().findByUserName(account);
        	dc = getSystemEntityDao().findByName(getDefaultDispatcher()).getPasswordDomain();
            return getInternalPasswordService().checkPassword(userEntity, dc, p, true, true);
        } else {
        	AccountEntity acc = getAccountEntityDao().findByNameAndSystem(account, dispatcher);
        	if (acc == null)
        		return PasswordValidation.PASSWORD_WRONG;
        	
            return getInternalPasswordService().
            		checkAccountPassword(acc, p, true, true);
        }
    }

    @Override
    protected void handleChangePassword(String user, String dispatcher, Password p, boolean mustChange)
            throws Exception {
        UserEntity userEntity;
        PasswordDomainEntity dc;
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        AccountEntity acc = getAccountEntityDao().findByNameAndSystem(user, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format("Uknown user %s/%s", user, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae : acc.getUsers()) {
                getInternalPasswordService().storeAndForwardPassword(uae.getUser(), acc.getSystem().getPasswordDomain(), p, mustChange);
            }
        } else {
            getInternalPasswordService().storeAndForwardAccountPassword(acc, p, mustChange, null);
        }
    }

    @Override
    protected void handleChangePasswordSync(String user, String dispatcher, Password p, boolean mustChange)
            throws Exception {
        UserEntity userEntity;
        PasswordDomainEntity dc;
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        AccountEntity acc = getAccountEntityDao().findByNameAndSystem(user, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format("Uknown user %s/%s", user, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae : acc.getUsers()) {
                getInternalPasswordService().storeAndSynchronizePassword(uae.getUser(), acc.getSystem().getPasswordDomain(), p, mustChange);
            }
        } else {
            getInternalPasswordService().storeAndSynchronizeAccountPassword(acc, p, mustChange, null);
        }
    }

    @Override
    protected byte[] handleGetUserMazingerRules(long userId, String version) throws Exception {
        UserEntity user = getUserEntityDao().load(userId);
        if (user == null)
            throw new UnknownUserException(Long.toString(userId));

        String res = getUserPueXMLDescriptors(user);

        Compile c;
        if ("xml".equals(version)) //$NON-NLS-1$
            return res.getBytes("UTF-8"); //$NON-NLS-1$
        else if (version == null || "1".equals(version)) //$NON-NLS-1$
            c = new Compile();
        else if ("2".equals(version)) //$NON-NLS-1$
            c = new Compile2();
        else
            c = new Compile3();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            InputStream in = new ByteArrayInputStream(res.getBytes("UTF-8")); //$NON-NLS-1$
            c.parse(in, out, false);
        } catch (Throwable th) {
            log.warn(Messages.getString("ServerServiceImpl.compilationError"), th); //$NON-NLS-1$
            throw new InternalErrorException(th.getMessage());
        }
        return out.toByteArray();
    }

    public String getUserPueXMLDescriptors(UserEntity user) throws InternalErrorException, UnknownUserException {
        StringBuffer xmlPUE = new StringBuffer(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Mazinger>"); //$NON-NLS-1$
        HashSet<Long> duplicates = new HashSet<Long>();
        EntryPointEntityDao dao = getEntryPointEntityDao();
        // Punts d'entrada publics
        addPuntsEntrada(xmlPUE, duplicates, dao.query(
                "select punt from com.soffid.iam.model.EntryPointEntity as punt " //$NON-NLS-1$
                        + "where punt.publicAccess='S' and punt.xmlEntryPoint is not null", //$NON-NLS-1$
                new Parameter[0]));

        // Punts d'entrada associats a l'usuari
        addPuntsEntrada(xmlPUE, duplicates, dao.query(
                "select punt from com.soffid.iam.model.EntryPointEntity as punt " //$NON-NLS-1$
                        + "join punt.authorizedUsers as autoritzacio " //$NON-NLS-1$
                        + "where autoritzacio.userId=:user and punt.xmlEntryPoint is not null", //$NON-NLS-1$
                new Parameter[] { new Parameter("user", user.getId()) })); //$NON-NLS-1$

        // Punts d'entrada dels grups
        for (Iterator<Group> it = getUserGroupsHierarchy(user.getUserName(), null).iterator(); it.hasNext(); ) {
            Group grup = it.next();
            addPuntsEntrada(xmlPUE, duplicates, dao.query(
            		"select punt from com.soffid.iam.model.EntryPointEntity AS punt "
            		+ "join punt.authorizedGroups AS autGroup "
            		+ "where autGroup.groupId = :grup and "
            		+ "punt.xmlEntryPoint is not null", 
            		new Parameter[]{
            				new Parameter("grup", grup.getId())}));
        }

        // Punts d'entrada dels rols
        for (Iterator<RoleGrant> it = getUserRoles(user.getId(), null).iterator(); it.hasNext();) {
            RoleGrant grant = it.next();
            addPuntsEntrada(xmlPUE, duplicates, dao.query(
                    "select punt " + //$NON-NLS-1$
                    "from com.soffid.iam.model.EntryPointEntity as punt " //$NON-NLS-1$
                            + "join punt.authorizedRoles as autRol " //$NON-NLS-1$
                            + "where autRol.roleId=:rol and punt.xmlEntryPoint is not null", //$NON-NLS-1$
                    new Parameter[] { new Parameter("rol", grant.getRoleId()) })); //$NON-NLS-1$
        }

        xmlPUE.append("</Mazinger>");// finalitzem el document //$NON-NLS-1$

        return xmlPUE.toString();

    }

    private void addPuntsEntrada(StringBuffer xmlPUE, HashSet<Long> duplicates, List<EntryPointEntity> query) {
        for (Iterator<EntryPointEntity> it = query.iterator(); it.hasNext(); ) {
            EntryPointEntity punt = it.next();
            if (!duplicates.contains(punt.getId())) {
                duplicates.add(punt.getId());
                String xml = punt.getXmlEntryPoint();
                String comentari = "<!-- " + (punt.getCode() == null ? punt.getName() : punt.getCode() + " - " + punt.getName()) + " -->";
                final String regexInici = "<Mazinger[\\s]*>";
                final String regexFi = "</Mazinger[\\s]*>[\\s]*";
                final String regexBuit = "[\\s]*<[\\s]*Mazinger[\\s]*/[\\s]*>[\\s]*";
                if (xml == null || xml.length() == 0 || xml.matches(regexBuit)) {
                } else {
                    try {
                        Pattern pi = Pattern.compile(regexInici);
                        Pattern pf = Pattern.compile(regexFi);
                        Matcher mi = pi.matcher(xml);
                        int inici = 0;
                        int fi = xml.length();
                        if (mi.find()) {
                            inici = mi.end();
                        }
                        Matcher mf = pf.matcher(xml);
                        while (mf.find()) {
                            fi = mf.start();
                        }
                        if ((inici < 0) || (fi < inici) || (fi < inici)) {
                            xmlPUE.append(comentari + Messages.getString("ServerServiceImpl.error1"));
                        } else {
                            xmlPUE.append(comentari);
                            xmlPUE.append(xml, inici, fi);
                        }
                    } catch (Throwable th) {
                        xmlPUE.append(comentari + Messages.getString("ServerServiceImpl.error2"));
                    }
                }
            }
        }
    }

    @Override
    protected Collection<Secret> handleGetUserSecrets(long userId) throws Exception {
        UserEntity user = getUserEntityDao().load(userId);
        SecretStoreService sss = getSecretStoreService();
        User usuari = getUserEntityDao().toUser(user);
        return sss.getAllSecrets(usuari);
    }

	@Override
    protected Group handleGetGroupInfo(String codi, String dispatcherId) throws Exception {
        com.soffid.iam.api.System dispatcher = getSystem(dispatcherId);

        if (! getDispatcherService().isGroupAllowed(dispatcher, codi))
            throw new es.caib.seycon.ng.exception.UnknownGroupException(codi);
        GroupEntity grup = getGroupEntityDao().findByName(codi);
        if (grup == null)
            throw new es.caib.seycon.ng.exception.UnknownGroupException(codi);

        return getGroupEntityDao().toGroup(grup);
    }

    @Override
    protected Collection<UserData> handleGetUserData(long userId) throws Exception {
        UserDataEntityDao dao = getUserDataEntityDao();

        UserEntity usuari = getUserEntityDao().load(userId);
        return dao.toUserDataList(usuari.getUserData());

    }

    @Override
    protected void handleCancelTask(long taskid) throws Exception {
        getTaskQueue().cancelTask(taskid);
    }

    @Override
    protected Password handleGenerateFakePassword(String account, String dispatcher) throws Exception {
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        AccountEntity acc = getAccountEntityDao().findByNameAndSystem(account, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format(Messages.getString("ServerServiceImpl.unknownUser"), account, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae : acc.getUsers()) {
                return getInternalPasswordService().generateFakePassword(uae.getUser(), acc.getSystem().getPasswordDomain());
            }
            throw new InternalErrorException(String.format(Messages.getString("ServerServiceImpl.unknownUser"), account, dispatcher)); //$NON-NLS-1$
        } else {
            return getInternalPasswordService().generateFakeAccountPassword(acc);
        }

    }


    @Override
    protected SystemAccessControl handleGetDispatcherAccessControl(Long dispatcherId)
            throws Exception {
        SystemEntity dispatcher = getSystemEntityDao().load(dispatcherId.longValue());
        if (dispatcher == null)
            throw new InternalErrorException(Messages.getString("ServerServiceImpl.dispatcherNotFound")); //$NON-NLS-1$

        SystemAccessControl dispatcherInfo = new SystemAccessControl();
        dispatcherInfo.setSystem(dispatcher.getName());
        dispatcherInfo.setEnabled(new Boolean("S".equals(dispatcher.getEnableAccessControl()))); 
        Collection<AccessControlEntity> acl = dispatcher.getAccessControls();
        AccessControlEntityDao aclDao = getAccessControlEntityDao();
        dispatcherInfo.getControlAcces().addAll(aclDao.toAccessControlList(acl));

        return dispatcherInfo;
    }

    @Override
    protected Password handleGetAccountPassword(String userId, String dispatcherId) throws Exception {
    	AccountEntity acc = getAccountEntityDao().findByNameAndSystem(userId, dispatcherId);
    	if (acc == null)
    		return null;
    	
    	Password p = getSecretStoreService().getPassword(acc.getId());
    	if (p == null && acc.getType().equals(AccountType.USER))
    	{
    		for (UserAccountEntity uae : acc.getUsers()) {
                User usuari = getUserEntityDao().toUser(uae.getUser());
                p = getSecretStoreService().getSecret(usuari, "dompass/" + acc.getSystem().getPasswordDomain().getId());
            }
    	}
    		
    	return p;
    }

    @Override
    protected Password handleGenerateFakePassword(String passDomain) throws Exception {
        PasswordDomainEntity dc = getPasswordDomainEntityDao().findByName(passDomain);

        return getInternalPasswordService().generateFakePassword(null, dc);
    }

    @Override
    protected PasswordPolicy handleGetUserPolicy(String account, String dispatcher)
            throws Exception {
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        SystemEntity dispatcherEntity = getSystemEntityDao().findByName(dispatcher);

        AccountEntity acc = getAccountEntityDao().findByNameAndSystem(account, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format(Messages.getString("ServerServiceImpl.unknownAccount"), account, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae : acc.getUsers()) {
                for (PasswordPolicyEntity pc : dispatcherEntity.getPasswordDomain().getPasswordPolicies()) {
                    if (pc.getUserType().getName().equals(uae.getUser().getUserType().getName())) return getPasswordPolicyEntityDao().toPasswordPolicy(pc);
                }
            }
        } else {
            for (PasswordPolicyEntity pc : dispatcherEntity.getPasswordDomain().getPasswordPolicies()) {
                if (pc.getUserType().getName().equals(acc.getPasswordPolicy().getName())) return getPasswordPolicyEntityDao().toPasswordPolicy(pc);
            }
        }
        return null;
    }

	@Override
	protected Password handleGetOrGenerateUserPassword(String account,
			String dispatcherId) throws Exception {

		
        Password secret = getAccountPassword(account, dispatcherId);
        if (secret == null) {
            AccountEntity acc = getAccountEntityDao().findByNameAndSystem(account, dispatcherId);
            if (acc.getType().equals(AccountType.USER))
            {
            	for (UserAccountEntity uae : acc.getUsers()) {
                    PasswordDomainEntity dce = acc.getSystem().getPasswordDomain();
                    secret = getInternalPasswordService().generateNewPassword(uae.getUser(), dce, false);
                    getInternalPasswordService().storePassword(uae.getUser(), dce, secret, true);
                    String secretName = "dompass/" + dce.getId();
                    User u = getUserEntityDao().toUser(uae.getUser());
                    getSecretStoreService().putSecret(u, secretName, secret);
                }
            } else {
           		secret = getInternalPasswordService().generateNewAccountPassword(acc, false);
            	// getInternalPasswordService().storeAccountPassword(acc, secret, true, null);
           	
            	getSecretStoreService().setPassword(acc.getId(), secret);
            }
        }
        return secret;
	}


	@Override
	protected User handleGetUserInfo(long userId) throws Exception {
		return getUserEntityDao().toUser(getUserEntityDao().load(new Long(userId)));
	}

	@Override
	protected String handleGetDefaultDispatcher() throws Exception {
		return getInternalPasswordService().getDefaultDispatcher();
	}

	@Override
	protected Collection<RoleGrant> handleGetAccountRoles(String account,
			String dispatcherId) throws Exception {
        AccountEntity accountEntity = getAccountEntityDao().findByNameAndSystem(account, dispatcherId);
        		

        return getApplicationService().findEffectiveRoleGrantByAccount(accountEntity.getId());
	}

	@Override
	protected Collection<RoleGrant> handleGetAccountExplicitRoles(String account,
			String dispatcherId) throws Exception {
        AccountEntity accountEntity = getAccountEntityDao().findByNameAndSystem(account, dispatcherId);
		

        return getApplicationService().findRoleGrantByAccount(accountEntity.getId());
	}

	@Override
	protected Collection<UserAccount> handleGetUserAccounts(long userId,
			String dispatcherId) throws Exception {
		UserEntity usuari = getUserEntityDao().load(new Long(userId));
		Collection<UserAccount> accounts = new LinkedList<UserAccount>();
		List<AccountEntity> accountList = getAccountEntityDao().findByUserAndSystem(usuari.getUserName(), dispatcherId);
		for (AccountEntity ae: accountList)
		{
			if (ae.getType().equals (AccountType.USER))
				for (UserAccountEntity uae: ae.getUsers())
					accounts.add (getUserAccountEntityDao().toUserAccount(uae));
		}

		return accounts;
		
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetUserGroups(java.lang.Long)
	 */
	@Override
	protected Collection<Group> handleGetUserGroups (Long userId) throws Exception
	{
    	UserEntity user = getUserEntityDao().load(userId);

    	HashMap<String, GroupEntity> grups = new HashMap<String, GroupEntity>();
    	
    	if (!grups.containsKey(user.getPrimaryGroup().getName()))
    		grups.put(user.getPrimaryGroup().getName(), user.getPrimaryGroup());
        for (Iterator<UserGroupEntity> it = user.getSecondaryGroups().iterator(); it.hasNext(); ) {
            UserGroupEntity uge = it.next();
            if (!grups.containsKey(uge.getGroup().getName())) grups.put(uge.getGroup().getName(), uge.getGroup());
        }
        
        return getGroupEntityDao().toGroupList(grups.values());
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetUserGroupsHierarchy(java.lang.Long)
	 */
	@Override
	protected Collection<Group> handleGetUserGroupsHierarchy (Long userId)
					throws Exception
	{
    	UserEntity user = getUserEntityDao().load(userId);

    	HashMap<String, GroupEntity> grups = new HashMap<String, GroupEntity>();
    	
    	if (!grups.containsKey(user.getPrimaryGroup().getName()))
    		grups.put(user.getPrimaryGroup().getName(), user.getPrimaryGroup());
        for (Iterator<UserGroupEntity> it = user.getSecondaryGroups().iterator(); it.hasNext(); ) {
            UserGroupEntity uge = it.next();
            if (!grups.containsKey(uge.getGroup().getName())) grups.put(uge.getGroup().getName(), uge.getGroup());
        }

        LinkedList<Group> values = new LinkedList<Group>();
        HashSet<String> keys = new HashSet<String>(grups.keySet());
        GroupEntityDao grupDao = getGroupEntityDao();
        for (GroupEntity grup : grups.values()) {
            while (grup != null && !keys.contains(grup.getName())) {
                keys.add(grup.getName());
                values.add(grupDao.toGroup(grup));
                grup = grup.getParent();
            }
            ;
        }

        return getGroupEntityDao().toGroupList(grups.values());
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetPlugin(java.lang.String)
	 */
	@Override
	protected Plugin handleGetPlugin (String className) throws Exception
	{
        AgentDescriptorEntity entity = getAgentDescriptorEntityDao().findByClass(className);
        if (entity == null) throw new InternalErrorException(Messages.getString("ServerServiceImpl.pluginNotFound")+className); //$NON-NLS-1$
        Plugin p = new Plugin();
        if (entity.getModule() == null && entity.getPlugin() != null)
        {
        	p.setContent(entity.getPlugin().getContent());
        	p.setName(entity.getPlugin().getName());
        	p.setVersion(entity.getPlugin().getVersion());
        }
        else
        {
        	p.setContent(entity.getModule().getContents());
        	p.setVersion(entity.getModule().getPlugin().getVersion());
        	p.setName(entity.getModule().getName());
        }
        return p;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handlePropagateOBUser(es.caib.seycon.ng.comu.User)
	 */
	@Override
	protected Map handlePropagateOBUser (User usuari) throws Exception
	{
		Task tasca = new Task();
		tasca.setTransaction(TaskHandler.UPDATE_USER);
		tasca.setUser(usuari.getUserName());
		TaskHandler th = new TaskHandler();
		th.setTask(tasca);
		return getTaskQueue().processOBTask(th);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleUpdateExpiredPasswords(es.caib.seycon.ng.comu.User, boolean)
	 */
	@Override
	protected boolean handleUpdateExpiredPasswords (User usuari, boolean externalAuth)
					throws Exception
	{
		UserEntity usuariEntity = getUserEntityDao().load(usuari.getId());
		if (usuariEntity != null)
		{
			return getInternalPasswordService().updateExpiredPasswords(usuariEntity, externalAuth);
		}
		else
			return false;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetExpiredPasswordDomains(es.caib.seycon.ng.comu.User)
	 */
	@Override
	protected Collection<PasswordDomain> handleGetExpiredPasswordDomains (
					User usuari) throws Exception
	{
		UserEntity usuariEntity = getUserEntityDao().load(usuari.getId());
		if (usuariEntity != null)
		{
			Collection<PasswordDomainEntity> list = getInternalPasswordService().enumExpiredPasswords(usuariEntity); 
			return getPasswordDomainEntityDao().toPasswordDomainList(list);
		}
		else
			return Collections.EMPTY_LIST;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetMyConfig()
	 */
	@Override
	protected Properties handleGetMyConfig () throws Exception
	{
		ConfigurationManager cfgManager = new ConfigurationManager();
		String account = Security.getCurrentAccount();
		return cfgManager.getProperties(account);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetMainJar()
	 */
	@Override
	protected void handleGetMainJar () throws Exception
	{
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetAddonList()
	 */
	@Override
	protected List<String> handleGetAddonList () throws Exception
	{
		return new JarExtractor().getActiveModules();
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetAddonJar(es.caib.seycon.ng.comu.ServerPluginModule)
	 */
	@Override
	protected byte[] handleGetAddonJar (String addon) throws Exception
	{
		throw new UnsupportedOperationException();
	}

	@Override
	protected String handleTranslate(String domain, String column1)
			throws Exception {
		Collection<AttributeTranslation> list = getAttributeTranslationService().findByColumn1(domain, column1);
		if (list.isEmpty())
			return null;
		else if (list.size() > 1)
			throw new IllegalArgumentException(String.format("More than one translation available for value %s on domain %s",
					column1, domain));
		else
			return list.iterator().next().getColumn2();
	}

	@Override
	protected String handleReverseTranslate(String domain, String column2)
			throws Exception {
		Collection<AttributeTranslation> list = getAttributeTranslationService().findByColumn2(domain, column2);
		if (list.isEmpty())
			return null;
		else if (list.size() > 1)
			throw new IllegalArgumentException(String.format("More than one translation available for value %s on domain %s",
					column2, domain));
		else
			return list.iterator().next().getColumn1();
	}

	@Override
	protected Collection<AttributeTranslation> handleTranslate2(String domain,
			String column1) throws Exception {
		return getAttributeTranslationService().findByColumn1(domain, column1);
	}

	@Override
	protected Collection<AttributeTranslation> handleReverseTranslate2(
			String domain, String column2) throws Exception {
		return getAttributeTranslationService().findByColumn2(domain, column2);
	}

	@Override
	protected Account handleGetAccountInfo(String accountName,
			String dispatcherId) throws Exception {
		return getAccountService().findAccount(accountName, dispatcherId);
	}
}

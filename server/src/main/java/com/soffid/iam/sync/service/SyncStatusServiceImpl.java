package com.soffid.iam.sync.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.LogFactory;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.AgentStatusInfo;
import com.soffid.iam.api.Configuration;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.GroupUser;
import com.soffid.iam.api.MailList;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.SyncServerInfo;
import com.soffid.iam.api.System;
import com.soffid.iam.api.Task;
import com.soffid.iam.api.User;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.AccountEntityDao;
import com.soffid.iam.model.UserAccountEntity;
import com.soffid.iam.model.UserEntityDao;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.agent.AgentManager;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.Engine;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.TaskHandlerLog;
import com.soffid.iam.sync.engine.cron.TaskScheduler;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.db.WrappedConnection;
import com.soffid.iam.sync.engine.extobj.AccountExtensibleObject;
import com.soffid.iam.sync.engine.extobj.CustomExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GrantExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GroupExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GroupUserExtensibleObject;
import com.soffid.iam.sync.engine.extobj.MailListExtensibleObject;
import com.soffid.iam.sync.engine.extobj.MembershipExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.RoleExtensibleObject;
import com.soffid.iam.sync.engine.extobj.UserExtensibleObject;
import com.soffid.iam.sync.engine.intf.DebugTaskResults;
import com.soffid.iam.sync.engine.intf.GetObjectResults;
import com.soffid.iam.sync.engine.pool.AbstractPool;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.utils.ConfigurationCache;

import es.caib.seycon.ng.comu.AccountAccessLevelEnum;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.utils.Security;

public abstract class SyncStatusServiceImpl extends SyncStatusServiceBase {
	org.apache.commons.logging.Log log = LogFactory.getLog(getClass());
	
    @Override
    protected Collection<AgentStatusInfo> handleGetSyncAgentsInfo(String tenant) throws Exception {
    	Security.nestedLogin(tenant, Security.getCurrentAccount(), Security.ALL_PERMISSIONS);
    	try
    	{
	        List<AgentStatusInfo> agentsServer = new LinkedList<AgentStatusInfo>();
	        TaskQueue tq = getTaskQueue ();
	
	        for (DispatcherHandler taskDispatcher : getTaskGenerator().getDispatchers()) {
	            if (taskDispatcher.isActive()) {
	                AgentStatusInfo agent = new AgentStatusInfo();
	
	                agent.setUrl(taskDispatcher.getSystem().getUrl());
	                agent.setAgentName(taskDispatcher.getSystem().getName());
	                agent.setPendingTasks(tq.countTasks(taskDispatcher));
	                if (!taskDispatcher.isConnected()) {
	                    agent.setStatus(Messages.getString("SyncStatusServiceImpl.Disconnected")); //$NON-NLS-1$
	                    if (taskDispatcher.getConnectException() != null) {
	                        ByteArrayOutputStream out = new ByteArrayOutputStream();
	                        PrintStream print = new PrintStream(out);
	                        SoffidStackTrace.printStackTrace(taskDispatcher.getConnectException(), print);
	                        print.close();
	                        agent.setStatus(Messages.getString("SyncStatusServiceImpl.Disconnected")); //$NON-NLS-1$
	                        // Afegim error
	                        agent.setStatusMessage(taskDispatcher.getConnectException().toString());
	                        agent.setStackTrace(new String(out.toByteArray()));
	                    }
	                } else {
	                    agent.setStatus(Messages.getString("SyncStatusServiceImpl.Connected")); //$NON-NLS-1$
	                    agent.setStatusMessage(null);
	                }
	
	                agent.setClassName(taskDispatcher.getSystem().getClassName());
	                agent.setVersion(taskDispatcher.getAgentVersion());
	                // Obtenim la caducitat del certificat
	                /*
	                 * if (mostraCertificats) { if ( taskDispatcher.isConnected()) {
	                 * try { String s_notValidAfter = new SimpleDateFormat(
	                 * "dd-MMM-yyyy HH:mm:ss").format(taskDispatcher
	                 * .getCertificateNotValidAfter()); toReturn.append("<td>" +
	                 * s_notValidAfter + "</td>"); } catch (Throwable th)
	                 * {toReturn.append("<td> </td>");} } else
	                 * toReturn.append("<td> </td>"); }
	                 */
	                agentsServer.add(agent);
	            }
	        }
	
	        return agentsServer;
    	} finally {
    		Security.nestedLogoff();
    	}
    }

    @Override
    protected SyncServerInfo handleGetSyncServerStatus(String tenant) throws Exception {
    	Security.nestedLogin(tenant, Security.getCurrentAccount(), Security.ALL_PERMISSIONS);
    	try
    	{
	        // NOTA: Els canvis s'han de fer també a l'altre mètode
	
	        String url = Config.getConfig().getURL().getServerURL().toString();
	        String versio = Config.getConfig().getVersion();
	        Calendar dataActualServer = Calendar.getInstance();
	
	        String estatConnexioAgents = getAgentConnectionStatus();
	        String estat = (getTaskGenerator() == null || getTaskGenerator().getDispatchers() == null
	        		|| getTaskGenerator().getDispatchers().size() == 0) ? "Starting" //$NON-NLS-1$
	                : (!getAgentConnectionStatus().startsWith("OK")) ? "Qualque Agent desconnectat" //$NON-NLS-1$ //$NON-NLS-2$
	                        : "OK"; //$NON-NLS-1$
	
	        // Llevem la part d'ok del número d'agents
	        estatConnexioAgents = !estatConnexioAgents.startsWith("OK") ? estatConnexioAgents //$NON-NLS-1$
	                : estatConnexioAgents.substring(3);
	
	        // Si encara no s'ha iniciat posem starting al número d'agents
	        String numAgents = "Starting".equals(estat) ? "Starting" : estatConnexioAgents; // connectats //$NON-NLS-1$ //$NON-NLS-2$
	                                                                                        // /
	                                                                                        // desconnects
	        // i a les tasques pendents (es calculen al seu - bbdd)
	        String numTasquesPendents = "Starting".equals(estat) ? "Starting" : "";// + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	                                                                               // getNumTasquesPendents();
	
	        SyncServerInfo info = new SyncServerInfo();
	        info.setUrl(url);
	        info.setDescription("Sync server");
	        info.setVersion(versio);
	        info.setStatus(estat);
	        info.setConnectedAgents(getConnectedAgents());
	        info.setNumberOfAgents(getTotalAgents());
	        info.setCurrentServerDate(dataActualServer);
	        return info;
    	} finally {
    		Security.nestedLogoff();
    	}
    }

    private int getConnectedAgents() throws IOException, InternalErrorException {
        int workingAgents = 0;
        int disconnectedAgents = 0;
        int totalAgents = 0;

        for (DispatcherHandler taskDispatcher : getTaskGenerator().getDispatchers()) {
            if (taskDispatcher != null && taskDispatcher.isActive()) {
                totalAgents++;
                if (!taskDispatcher.isConnected()) {
                    disconnectedAgents++;
                } else {
                    workingAgents++;
                }
            } else
                disconnectedAgents++;// està ok??
        }
        return workingAgents;
    }

    private int getTotalAgents() throws IOException, InternalErrorException {
    	return getDispatcherService().findDispatchersByFilter(null, null, null, null, null, Boolean.TRUE).size();
    }


    private String getAgentConnectionStatus() throws IOException, InternalErrorException {
        int workingAgents = 0;
        int disconnectedAgents = 0;
        int totalAgents = 0;

        for (DispatcherHandler taskDispatcher : getTaskGenerator().getDispatchers()) {
            if (taskDispatcher != null && taskDispatcher.isActive()) {
                totalAgents++;
                if (!taskDispatcher.isConnected()) {
                    disconnectedAgents++;
                } else {
                    workingAgents++;
                }
            } else
                disconnectedAgents++;// està ok??
        }
        String estat = (workingAgents + " / " + totalAgents); //$NON-NLS-1$

        // OK = {disconnectedAgents==0} ?? o (workingAgents == totalAgents)
        return (workingAgents == totalAgents) ? "OK " + totalAgents : estat; //$NON-NLS-1$
    }

    @Override
    protected SyncServerInfo handleGetSyncServerInfo(String tenant) throws Exception {

    	Security.nestedLogin(tenant, Security.getCurrentAccount(), Security.ALL_PERMISSIONS);
    	try
    	{
	
	        String url = Config.getConfig().getURL().getServerURL().toString();
	        String versio = Config.getConfig().getVersion();
	        String sso = getSSOThreadStatus();
	        String jetty = getJettyThreadStatus();
	        String ssoDaemon = getSSODaemonThreadStatus();
	        String taskGenerator = getTaskGeneratorThreadStatus();
	        Calendar caducitatRootCertificate = Calendar.getInstance();
	        Date rootCertDate = getRootCertificateNotValidAfter();
	        if (rootCertDate != null)
	            caducitatRootCertificate.setTime(rootCertDate);
	        else
	            rootCertDate = null;
	        Calendar caducitatMainCertificate = Calendar.getInstance();
	        Date serverCertDate = getServerCertificateNotValidAfter();
	        if (serverCertDate != null)
	            caducitatMainCertificate.setTime(serverCertDate);
	        else
	            serverCertDate = null;
	        Calendar dataActualServer = Calendar.getInstance();
	        String databaseConnections = getDBConnectionStatus();
	
	        String estatConnexioAgents = getAgentConnectionStatus();
	        String estat = (getTaskGenerator() == null || getTaskGenerator().getDispatchers() == null) ? "Starting" //$NON-NLS-1$
	                : (!estatConnexioAgents.startsWith("OK")) ? "Warnings" : "OK"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	
	        // Llevem la part d'ok del número d'agents
	        estatConnexioAgents = !estatConnexioAgents.startsWith("OK") ? estatConnexioAgents //$NON-NLS-1$
	                : estatConnexioAgents.substring(3);
	
	        // Si encara no s'ha iniciat posem starting al número d'agents
	        String numAgents = "Starting".equals(estat) ? "Starting" : estatConnexioAgents; // connectats //$NON-NLS-1$ //$NON-NLS-2$
	                                                                                        // /
	                                                                                        // desconnects
	        // i a les tasques pendents (es calculen al seu - bbdd)
	        String numTasquesPendents = "Starting".equals(estat) ? "Starting" : "";// + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	                                                                               // getNumTasquesPendents();
	
	        SyncServerInfo info = new SyncServerInfo();
	        info.setUrl(url);
	        info.setDescription("Sync server");
	        info.setVersion(versio);
	        info.setStatus(estat);
	        info.setConnectedAgents(getConnectedAgents());
	        info.setNumberOfAgents(getTotalAgents());
	        info.setSso(sso);
	        info.setJetty(jetty);
	        info.setSsoDaemon(ssoDaemon);
	        info.setTaskGenerator(taskGenerator);
	        info.setExpirationMainCertificate(caducitatMainCertificate);
	        info.setExpirationRootCertificate(caducitatRootCertificate);
	        info.setCurrentServerDate(dataActualServer);
	        info.setDatabaseConnections(databaseConnections);
	        return info;
    	} finally {
    		Security.nestedLogoff();
    	}
    }

    private boolean threadRunning(Thread thread) {
        return thread != null && thread.getState() != Thread.State.TERMINATED
                && thread.getState() != Thread.State.NEW;
    }

    private String getSSOThreadStatus() {
        Thread SSOServer = SoffidApplication.getSso();
        if (threadRunning(SSOServer)) {
            return Messages.getString("SyncStatusServiceImpl.SSORunning"); //$NON-NLS-1$
        }
        return Messages.getString("SyncStatusServiceImpl.SSOFailed"); //$NON-NLS-1$
    }

    private String getJettyThreadStatus() {
        JettyServer jetty = SoffidApplication.getJetty();
        if (jetty != null) {
            if (jetty.isRunning()) {
                return Messages.getString("SyncStatusServiceImpl.JettyRunning"); //$NON-NLS-1$
            }
        }
        return Messages.getString("SyncStatusServiceImpl.JettyFailed"); //$NON-NLS-1$
    }

    private String getSSODaemonThreadStatus() {
        Thread ssoDaemon = SoffidApplication.getSsoDaemon();
        if (threadRunning(ssoDaemon)) {
            return Messages.getString("SyncStatusServiceImpl.SSODaemonRunning"); //$NON-NLS-1$
        }
        return Messages.getString("SyncStatusServiceImpl.SSODaemonFailed"); //$NON-NLS-1$
    }

    private String getTaskGeneratorThreadStatus() {
        Engine engine = Engine.getEngine();
        if (threadRunning(engine)) {
            return Messages.getString("SyncStatusServiceImpl.TaskGeneratorRunning"); //$NON-NLS-1$
        }
        return Messages.getString("SyncStatusServiceImpl.TaskGeneratorFailed"); //$NON-NLS-1$
    }

    private Date getRootCertificateNotValidAfter() {
        try {
            File f = SeyconKeyStore.getRootKeyStoreFile();
            if (f.canRead()) {
                KeyStore s = SeyconKeyStore.loadKeyStore(f);
                X509Certificate cert = (X509Certificate) s.getCertificate(SeyconKeyStore.ROOT_KEY);
                Date notAfter = cert.getNotAfter();
                if (notAfter != null)
                    return notAfter;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Date getServerCertificateNotValidAfter() {
        try {
            File f = SeyconKeyStore.getKeyStoreFile();
            if (f.canRead()) {
                KeyStore s = SeyconKeyStore.loadKeyStore(f);
                X509Certificate cert = (X509Certificate) s.getCertificate(SeyconKeyStore.MY_KEY);
                Date notAfter = cert.getNotAfter();
                if (notAfter != null)
                    return notAfter;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Override
    protected String handleGetDBConnectionStatus() throws Exception {
        try {
            AbstractPool<WrappedConnection> pool = ConnectionPool.getPool();
            //return Messages.getString("SyncStatusServiceImpl.Using") + pool.getNumberOfLockedConnections() + Messages.getString("SyncStatusServiceImpl.De") //$NON-NLS-1$ //$NON-NLS-2$
              //      + pool.getNumberOfConnections() + Messages.getString("SyncStatusServiceImpl.Allocated") + pool.getPoolSize() + " max)"; //$NON-NLS-1$ //$NON-NLS-2$
            StringBuffer buffer = new StringBuffer();
            buffer.append(String.format(Messages.getString("SyncStatusServiceImpl.Using"), 
            				new Object [] {pool.getNumberOfLockedConnections(), pool.getNumberOfConnections(), 
            				pool.getMaxSize()}));
            return buffer.toString();
        } catch (Exception e) {
            return Messages.getString("SyncStatusServiceImpl.DataBasePoolFailed") + e.getMessage(); //$NON-NLS-1$
        }
    }

    @Override
    protected Collection<SyncServerInfo> handleGetServerAgentHostsURL() throws Exception {
        HashMap<String, SyncServerInfo> hosts = new HashMap<String, SyncServerInfo>();
        Config config = Config.getConfig();
        String servidors[] = config.getServerList().split("[, ]+");
        for (int i = 0; i < servidors.length; i++) {
            String host = new URLManager(servidors[i]).getAgentURL().getHost();
            if (!hosts.containsKey(host))
                hosts.put(host, new SyncServerInfo(host, host));
        }

        for (DispatcherHandler td : getTaskGenerator().getDispatchers()) {
            if (td.getSystem().getUrl() != null) {
                String host = new URLManager(td.getSystem().getUrl()).getAgentURL().getHost();
                if (!hosts.containsKey(host) && !host.equals("local")) { //$NON-NLS-1$
                    hosts.put(host, new SyncServerInfo(host, host));
                }
            }
        }
        ArrayList<SyncServerInfo> result = new ArrayList<SyncServerInfo>(hosts.values());
        // Afegim una entrada per al principal al final
        result.add(new SyncServerInfo("agents", Messages.getString("SyncStatusServiceImpl.AllAgents"))); //$NON-NLS-1$ //$NON-NLS-2$

        return result;
    }

    @Override
    protected String handleResetAllServer() throws Exception {
        SoffidApplication.shutDown();
        return null;
    }

    @Override
    protected String handleResetServerAgents(String server) throws Exception {
    	log.info("Reseting agent "+server);
        StringBuffer res = new StringBuffer(""); //$NON-NLS-1$
        try {
            RemoteServiceLocator rsl = new RemoteServiceLocator(server);
            AgentManager agentMgr = rsl.getAgentManager();
            agentMgr.reset();
            res.append(Messages.getString("SyncStatusServiceImpl.System")+server+Messages.getString("SyncStatusServiceImpl.IsRestarting")); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            res.append(Messages.getString("SyncStatusServiceImpl.ErrorRestarting") + server + ": " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$

        }
        return res.toString();
    }

	public Password handleGetAccountPassword (String user, Long accountId)
			throws InternalErrorException
	{
		return handleGetAccountPassword(user, accountId, AccountAccessLevelEnum.ACCESS_OWNER);
	}
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SyncStatusService#getAccountPassword(java.lang.String, es.caib.seycon.ng.comu.Account)
	 */
	public Password handleGetAccountPassword (String user, Long accountId, AccountAccessLevelEnum level)
					throws InternalErrorException
	{
		AccountService svc = getAccountService();
		AccountEntityDao accountDao = getAccountEntityDao();
		UserEntityDao usuariDao = getUserEntityDao();
		
		AccountEntity accEntity = accountDao.load(accountId);
		Account account = accountDao.toAccount(accEntity);
		if (account == null)
			throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.AccountNotFound"), accountId)); //$NON-NLS-1$
		
		if (account.getType().equals(AccountType.USER))
		{
			boolean found = false;
			for (UserAccountEntity ua : accEntity.getUsers()) {
                if (ua.getUser().getUserName().equals(user)) found = true;
            }
			if (! found )
				throw new SecurityException(Messages.getString("SyncStatusServiceImpl.NotAuthorized")); //$NON-NLS-1$
		} 
		else if (account.getType().equals(AccountType.PRIVILEGED))
		{
			boolean found = false;
			boolean caducada = false;
			boolean ownedByOther = false;
			String otherOwner = null;
			for (UserAccountEntity ua : accEntity.getUsers()) {
                if (!ua.getUser().getUserName().equals(user) && ua.getUntilDate() != null && new Date().before(ua.getUntilDate())) {
                    ownedByOther = true;
                    otherOwner = ua.getUser().getUserName();
                } else if (ua.getUser().getUserName().equals(user) && ua.getUntilDate() != null && new Date().before(ua.getUntilDate())) {
                    found = true;
                } else if (ua.getUser().getUserName().equals(user) && ua.getUntilDate() != null && new Date().after(ua.getUntilDate())) {
                    found = true;
                    caducada = true;
                }
            }
			if (found && caducada)
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.PasswordExpired"))); //$NON-NLS-1$
			else if (! found && ownedByOther)
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.ownedByOther"), otherOwner)); //$NON-NLS-1$
			else if (! found)
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.NotAuthorized"))); //$NON-NLS-1$
		} 
		else  
		{
			Collection<String> owners = svc.getAccountUsers(account, level);
			if (! owners.contains(user))
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.NotAuthorized"))); //$NON-NLS-1$
		}

		Password p = getSecretStoreService().getPassword(accountId);
		if (p == null && account.getType().equals(AccountType.USER))
		{
			User u = new User();
			for (UserAccountEntity ua : accEntity.getUsers()) {
                u = getUserEntityDao().toUser(ua.getUser());
            }
        	p = getSecretStoreService().getSecret(u, "dompass/" + accEntity.getSystem().getPasswordDomain().getId()); //$NON-NLS-1$
		}
       	return p;
		
	}

	@Override
	public Password handleGetAccountSshKey (String user, Long accountId)
			throws InternalErrorException
	{
		return handleGetAccountSshKey(user, accountId, AccountAccessLevelEnum.ACCESS_OWNER);
	}
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SyncStatusService#getAccountPassword(java.lang.String, es.caib.seycon.ng.comu.Account)
	 */
	@Override
	public Password handleGetAccountSshKey (String user, Long accountId, AccountAccessLevelEnum level)
					throws InternalErrorException
	{
		AccountService svc = getAccountService();
		AccountEntityDao accountDao = getAccountEntityDao();
		UserEntityDao usuariDao = getUserEntityDao();
		
		AccountEntity accEntity = accountDao.load(accountId);
		Account account = accountDao.toAccount(accEntity);
		if (account == null)
			throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.AccountNotFound"), accountId)); //$NON-NLS-1$
		
		if (account.getType().equals(AccountType.USER))
		{
			boolean found = false;
			for (UserAccountEntity ua : accEntity.getUsers()) {
                if (ua.getUser().getUserName().equals(user)) found = true;
            }
			if (! found )
				throw new SecurityException(Messages.getString("SyncStatusServiceImpl.NotAuthorized")); //$NON-NLS-1$
		} 
		else if (account.getType().equals(AccountType.PRIVILEGED))
		{
			boolean found = false;
			boolean caducada = false;
			boolean ownedByOther = false;
			String otherOwner = null;
			for (UserAccountEntity ua : accEntity.getUsers()) {
                if (!ua.getUser().getUserName().equals(user) && ua.getUntilDate() != null && new Date().before(ua.getUntilDate())) {
                    ownedByOther = true;
                    otherOwner = ua.getUser().getUserName();
                } else if (ua.getUser().getUserName().equals(user) && ua.getUntilDate() != null && new Date().before(ua.getUntilDate())) {
                    found = true;
                } else if (ua.getUser().getUserName().equals(user) && ua.getUntilDate() != null && new Date().after(ua.getUntilDate())) {
                    found = true;
                    caducada = true;
                }
            }
			if (found && caducada)
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.PasswordExpired"))); //$NON-NLS-1$
			else if (! found && ownedByOther)
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.ownedByOther"), otherOwner)); //$NON-NLS-1$
			else if (! found)
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.NotAuthorized"))); //$NON-NLS-1$
		} 
		else  
		{
			Collection<String> owners = svc.getAccountUsers(account, level);
			if (! owners.contains(user))
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.NotAuthorized"))); //$NON-NLS-1$
		}

		Password p = getSecretStoreService().getSshPrivateKey(accountId);
       	return p;
		
	}
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SyncStatusService#getMazingerRules(java.lang.String)
	 */
	public byte[] handleGetMazingerRules (String user) throws InternalErrorException
	{
        try
		{
			User usuari = getServerService().getUserInfo(user, null);
			return getServerService().getUserMazingerRules(usuari.getId(), "xml"); //$NON-NLS-1$
		}
		catch (UnknownUserException e)
		{
			throw new InternalErrorException(Messages.getString("SyncStatusServiceImpl.ErrorSSORules"), e); //$NON-NLS-1$
		}
	}

	@Override
	protected void handleReconfigureDispatchers() throws Exception {
		new Thread () {
			@Override
			public void run() {
				try {
					Thread.sleep(3000); // Wait 3 seconds for console transation to complete
					getTaskGenerator().updateAgents();
					for ( Configuration cfg:  com.soffid.iam.ServiceLocator.instance().getConfigurationService().findConfigurationByFilter("%", null, null, null))
					{
						if (cfg.getNetworkCode() == null || cfg.getNetworkCode().isEmpty())
							ConfigurationCache.setProperty(cfg.getCode(), cfg.getValue());
					}
				} catch (Throwable e) {
					LogFactory.getLog(SyncStatusServiceImpl.class).warn("Error updating configuration", e);
				}
			}
		}.start();
	}

	@Override
	protected Map<String, Object> handleTestObjectMapping(
			Map<String, String> sentences, String dispatcher,
			SoffidObjectType type, String object1, String object2)
			throws Exception {

		ExtensibleObject source = null;
		source = generateSourceObject(dispatcher, type, object1, object2);
		System d = getServerService().getDispatcherInfo(dispatcher);
		Map<String, Object> result = new HashMap<String, Object>();

		for (String att: sentences.keySet())
		{
			String expr = sentences.get(att);
			ObjectTranslator ot = new ObjectTranslator(d);
			Object v;
			try {
				v = ot.eval(expr, source);
			} catch (Exception e) {
				v = e;
			}
			result.put(att, v);
		}
		return result;
	}

	private ExtensibleObject generateSourceObject(String dispatcher, SoffidObjectType type,
			String object1, String object2) throws InternalErrorException {
		ExtensibleObject source = null;
		if (type.equals(SoffidObjectType.OBJECT_ACCOUNT))
		{
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			if (acc == null)
			{
				acc = new Account();
				acc.setName(object1);
				acc.setSystem(dispatcher);
				acc.setDisabled(true);
			}
			source = new AccountExtensibleObject(acc, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_GROUP) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_GROUP))
		{
			Group g = null;
			try {
				g = getServerService().getGroupInfo(object2, dispatcher);
			} catch (UnknownGroupException e) {
				g = new Group();
				g.setName(object2);
			}
			User u;
			try {
				u = getServerService().getUserInfo(object1, dispatcher);
				Collection<GroupUser> userGroups = getServerService().getUserMemberships(object1, dispatcher);
				if (userGroups != null) {
					for ( GroupUser ug: userGroups) {
						if (ug.getGroup().equals(object2)) 
							source = new GroupUserExtensibleObject(ug, dispatcher, getServerService());
					}
				}
			} catch (UnknownUserException e) {
				u = new User();
				u.setUserName(object1);
				u.setActive(false);
			}
			
			if (source == null) {
				Account acc = getServerService().getAccountInfo(object1, dispatcher);
				source = new MembershipExtensibleObject(acc, u, g, getServerService());
			}
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_ROLES) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_ROLE) ||
				type.equals(SoffidObjectType.OBJECT_GRANT))
		{
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			RoleGrant grant = null;
			if (type.equals(SoffidObjectType.OBJECT_GRANT))
			{
				try {
					for ( Group group: getServerService().getUserGroups(object1, dispatcher))
					{
						if (group.getName().equals (object2))
						{
							grant = new RoleGrant();
							grant.setSystem(dispatcher);
							grant.setEnabled(true);
							grant.setOwnerSystem(dispatcher);
							grant.setRoleName(group.getName());
							grant.setUser(null);
						}
					}
				} catch (UnknownUserException e) {
				}
			}
			Collection<RoleGrant> list;
			if (type.equals(SoffidObjectType.OBJECT_GRANTED_ROLE))
				list = getServerService().getAccountExplicitRoles(object1, dispatcher);
			else
				list = getServerService().getAccountRoles(object1, dispatcher);
			for (RoleGrant grant2: list)
			{
				if (grant2.getRoleName().equals (object2))
					grant = grant2;
			}
			if (grant == null)
			{
				grant = new RoleGrant();
				grant.setSystem(dispatcher);
				grant.setEnabled(true);
				grant.setOwnerSystem(dispatcher);
				grant.setRoleName(object2);
			}
			source = new GrantExtensibleObject(grant, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_MAIL_LIST))
		{
			MailList ml = null;
			try {
				ml = getServerService().getMailList(object1, object2);
			} catch (UnknownMailListException e) {
			}
			if (ml == null)
			{
				ml = new MailList();
				ml.setDomainCode(object2);
				ml.setName(object1);
			}
			source = new MailListExtensibleObject(ml, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_ROLE))
		{
			Role role = null;
			try {
				role = getServerService().getRoleInfo(object1, dispatcher);
			} catch (UnknownRoleException e) {
			}
			if (role == null)
			{
				role = new Role();
			}
			source = new RoleExtensibleObject(role, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_USER))
		{
			User u;
			try {
				u = getServerService().getUserInfo(object1, dispatcher);
			} catch (UnknownUserException e) {
				u = new User();
				u.setUserName(object1);
				u.setActive(false);
			}
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			if (acc == null)
			{
				acc = new Account();
				acc.setName(object1);
				acc.setSystem(dispatcher);
				acc.setDisabled(true);
			}
			source = new UserExtensibleObject(acc, u, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_GROUP))
		{
			try {
				Group g = getServerService().getGroupInfo(object1, dispatcher);
				source = new GroupExtensibleObject(g, dispatcher, getServerService());
			} catch (UnknownGroupException e) {
				source = new ExtensibleObject();
			}
		}
		else if (type.equals(SoffidObjectType.OBJECT_CUSTOM))
		{
			CustomObject co = getServerService().getCustomObject(object1, object2);
			source = new CustomExtensibleObject(co, getServerService());
		} else {
			source = new ExtensibleObject();
		}
		
		return source;
	}

	@Override
	public DebugTaskResults handleTestPropagateObject(String dispatcher,
			SoffidObjectType type, String object1, String object2)
			throws InternalErrorException, InternalErrorException {
		TaskHandler task = generateObjectTask(dispatcher, type, object1, object2);
		getTaskGenerator().updateAgents();
		Map<String, DebugTaskResults> map = getTaskQueue().debugTask(task);
		return map.get(dispatcher);
	}

	private TaskHandler generateObjectTask(String dispatcher, SoffidObjectType type,
			String object1, String object2) throws InternalErrorException {
		Task tasca = new Task();
		tasca.setTaskDate(Calendar.getInstance());
		tasca.setSystemName(dispatcher);
		tasca.setExpirationDate(Calendar.getInstance());
		tasca.getExpirationDate().add(Calendar.MINUTE, 5);

		TaskHandler th = new TaskHandler();
		th.setTask(tasca);
		th.setTenant(Security.getCurrentTenantName());
		if (type.equals(SoffidObjectType.OBJECT_ACCOUNT))
		{
			tasca.setTransaction(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUser(object1);
			tasca.setDatabase(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_GROUP) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_GROUP))
		{
			tasca.setTransaction(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUser(object1);
			tasca.setDatabase(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_ROLES) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_ROLE) ||
				type.equals(SoffidObjectType.OBJECT_GRANT))
		{
			tasca.setTransaction(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUser(object1);
			tasca.setDatabase(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_MAIL_LIST))
		{
			tasca.setTransaction(TaskHandler.UPDATE_LIST_ALIAS);
			tasca.setAlias(object1);
			tasca.setMailDomain(object2);
		}
		else if (type.equals(SoffidObjectType.OBJECT_ROLE))
		{
			tasca.setTransaction(TaskHandler.UPDATE_ROLE);
			tasca.setRole(object1);
			tasca.setDatabase(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_USER))
		{
			AccountEntity acc = getAccountEntityDao().findByNameAndSystem(object1, dispatcher);
			if (acc == null) {
				tasca.setTransaction(TaskHandler.UPDATE_USER);
				tasca.setUser(object1);
			} else {
				tasca.setTransaction(TaskHandler.UPDATE_ACCOUNT);
				tasca.setUser(object1);
				tasca.setDatabase(dispatcher);
			}
		}
		else if (type.equals(SoffidObjectType.OBJECT_GROUP))
		{
			tasca.setTransaction(TaskHandler.UPDATE_GROUP);
			tasca.setGroup(object1);
		}
		else if (type.equals(SoffidObjectType.OBJECT_CUSTOM))
		{
			tasca.setTransaction(TaskHandler.UPDATE_OBJECT);
			tasca.setCustomObjectName(object2);
			tasca.setCustomObjectType(object1);
		}

		return th;
	}

	@Override
	protected void handleBoostTask(long taskId) throws Exception {
		TaskHandler th = getTaskQueue().findTaskHandlerById(taskId);
		if (th != null)
		{
			for ( TaskHandlerLog log: th.getLogs())
			{
				if (log != null && !log.isComplete())
				{
					log.setNext(java.lang.System.currentTimeMillis());
				}
			}
			th.getTask().setTaskDate(Calendar.getInstance());
			getTaskQueue().pushTaskToPersist(th);
			
		}
	}

	@Override
	protected void handleCancelTask(long taskId) throws Exception {
		getTaskQueue().cancelTask(taskId, Long.toString(taskId));
	}

	@Override
	protected void handleStartScheduledTask(ScheduledTask t) throws Exception {
    	TaskScheduler ts = TaskScheduler.getScheduler();
    	
    	ScheduledTask task = ts.findTask(t.getId());
    	if (task != null)
    	{
    		if (task.isActive())
    			throw new InternalErrorException ("This task is aleady running");
   			ts.runNow(task, null, false);
    	}
	}

	@Override
	protected GetObjectResults handleGetNativeObject(String systemName, SoffidObjectType type, String object1,
			String object2) throws Exception {

		getTaskGenerator().updateAgents();

		DispatcherHandler handler = getTaskGenerator().getDispatcher(systemName);
		if (handler == null || !handler.isActive())
			return null;


		return handler.getNativeObject(systemName, type, object1, object2);
	}

	@Override
	protected GetObjectResults handleGetSoffidObject(String systemName, SoffidObjectType type, String object1,
			String object2) throws Exception {

		getTaskGenerator().updateAgents();

		DispatcherHandler handler = getTaskGenerator().getDispatcher(systemName);
		if (handler == null || !handler.isActive())
			return null;

		return handler.getSoffidObject(systemName, type, object1, object2);
	}

	public String[] handleTailServerLog(String server) throws Exception {
        RemoteServiceLocator rsl = new RemoteServiceLocator(server);
        AgentManager agentMgr = rsl.getAgentManager();
        return agentMgr.tailServerLog();
	}

	@Override
	protected GetObjectResults handleReconcile(String system, String accountName) throws Exception {
		GetObjectResults r = new GetObjectResults();
		r.setStatus("Error");
		r.setObject(new HashMap<String, Object>());
		DispatcherHandler handler = getTaskGenerator().getDispatcher(system);
		if (handler == null || !handler.isActive())
		{
			r.setLog("System is offline");
		}
		else
		{
			StringWriter w = new StringWriter();
			PrintWriter out = new PrintWriter(w);
			try {
				handler.doReconcile(accountName, out, true);
				out.flush();
				r.setStatus("Success");
				r.setLog(w.toString());
			} catch (Exception e) {
				out.flush();
				r.setStatus("Error");
				r.setLog(w.toString()+"\n"+
						SoffidStackTrace.getStackTrace(e));
			}
		}
		
		return r;

		
	}

	@Override
	protected PasswordValidation handleCheckPasswordSynchronizationStatus(String accountName, String serverName) throws Exception {
		DispatcherHandler d = getTaskGenerator().getDispatcher(serverName);
		return d.checkPasswordSynchronizationStatus(accountName);
	}

	@Override
	protected void handleSetAccountPassword(String accountName, String serverName, Password password, boolean mustChange) throws Exception {
		AccountEntity account = getAccountEntityDao().findByNameAndSystem(accountName, serverName);
		if ( account != null)
		{
			getInternalPasswordService().storeAndSynchronizeAccountPassword(account, password, mustChange, null);
		} else {
			throw new InternalErrorException ("Cannot find account "+accountName+" at "+serverName);
		}
	}
	
	@Override
	protected void handleCheckConnectivity(java.lang.String dispatcher) throws Exception {
		getTaskGenerator().updateAgents();
		DispatcherHandler handler = getTaskGenerator().getDispatcher(dispatcher);
		if (handler == null)
			throw new InternalErrorException ("System "+dispatcher+" is not enabled yet");
		if (! handler.isConnected())
		{
			handler.connect(true, false);
		}
	}

	@Override
	protected Collection<Map<String,Object>> handleInvoke(java.lang.String dispatcher, java.lang.String verb, java.lang.String object, java.util.Map<java.lang.String,java.lang.Object> attributes) throws Exception {
		return getServerService().invoke(dispatcher, verb, object, attributes);
	}

	@Override
	protected void handleSetAccountSshPrivateKey(java.lang.String accountName, java.lang.String serverName, com.soffid.iam.api.Password privateKey) throws Exception {
		AccountEntity account = getAccountEntityDao().findByNameAndSystem(accountName, serverName);
		if ( account != null)
		{
			getSecretStoreService().setSshPrivateKey(account.getId(), privateKey);
		} else {
			throw new InternalErrorException ("Cannot find account "+accountName+" at "+serverName);
		}
	}
	
}

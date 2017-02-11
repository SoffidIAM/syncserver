package es.caib.seycon.ng.sync.servei;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
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
import java.util.Vector;

import javax.servlet.ServletException;

import org.apache.commons.logging.LogFactory;

import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.AgentStatusInfo;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.LlistaCorreu;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SeyconServerInfo;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Tasca;
import es.caib.seycon.ng.comu.UserAccount;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.model.AccountEntity;
import es.caib.seycon.ng.model.AccountEntityDao;
import es.caib.seycon.ng.model.DispatcherEntity;
import es.caib.seycon.ng.model.UserAccountEntity;
import es.caib.seycon.ng.model.UsuariEntityDao;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.sync.SeyconApplication;
import es.caib.seycon.ng.sync.agent.AgentManager;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.Engine;
import es.caib.seycon.ng.sync.engine.TaskHandler;
import es.caib.seycon.ng.sync.engine.TaskHandlerLog;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.extobj.AccountExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.GrantExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.MailListExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.MembershipExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.ObjectTranslator;
import es.caib.seycon.ng.sync.engine.extobj.RoleExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.UserExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.jetty.JettyServer;
import es.caib.seycon.ng.sync.web.esso.GetSecretsServlet;
import es.caib.seycon.ng.utils.ExceptionTranslator;
import es.caib.seycon.ssl.SeyconKeyStore;

public class SyncStatusServiceImpl extends SyncStatusServiceBase {
	@Override
    protected Collection<AgentStatusInfo> handleGetSeyconAgentsInfo() throws Exception {
        List<AgentStatusInfo> agentsServer = new LinkedList<AgentStatusInfo>();
        TaskQueue tq = getTaskQueue ();

        for (DispatcherHandler taskDispatcher : getTaskGenerator().getDispatchers()) {
            if (taskDispatcher.isActive()) {
                AgentStatusInfo agent = new AgentStatusInfo();

                agent.setUrl(taskDispatcher.getDispatcher().getUrl());
                agent.setNomAgent(taskDispatcher.getDispatcher().getCodi());
                agent.setTasquesPendents(tq.countTasks(taskDispatcher));
                if (!taskDispatcher.isConnected()) {
                    agent.setEstat(Messages.getString("SyncStatusServiceImpl.Disconnected")); //$NON-NLS-1$
                    if (taskDispatcher.getConnectException() != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        PrintStream print = new PrintStream(out);
                        SoffidStackTrace.printStackTrace(taskDispatcher.getConnectException(), print);
                        print.close();
                        agent.setEstat(Messages.getString("SyncStatusServiceImpl.Disconnected")); //$NON-NLS-1$
                        // Afegim error
                        agent.setMsgEstat(new String(out.toByteArray()));
                    }
                } else {
                    agent.setEstat(Messages.getString("SyncStatusServiceImpl.Connected")); //$NON-NLS-1$
                }

                agent.setNomClasse(taskDispatcher.getDispatcher().getNomCla());
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
    }

    @Override
    protected SeyconServerInfo handleGetSeyconServerStatus() throws Exception {
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

        // url, versio, estat,
        // numAgents, numTasquesPendents, sso, jetty, ssoDaemon,
        // taskGenerator, caducitatRootCertificate,
        // caducitatMainCertificate, dataActualServer, databaseConnections)
        SeyconServerInfo info = new SeyconServerInfo(url, "Sync server", versio, estat, numAgents, //$NON-NLS-1$
                "" + numTasquesPendents, "", "", "", "", null, null, dataActualServer, ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

        return info;
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
    protected SeyconServerInfo handleGetSeyconServerInfo() throws Exception {
        // NOTA: Els canvis s'han de fer també a l'altre mètode

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
                : (!estatConnexioAgents.startsWith("OK")) ? "Qualque Agent desconnectat" : "OK"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

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

        SeyconServerInfo info = new SeyconServerInfo(url, "Sync server", versio, estat, numAgents, //$NON-NLS-1$
                "" + numTasquesPendents, sso, jetty, ssoDaemon, taskGenerator, //$NON-NLS-1$
                caducitatRootCertificate, caducitatMainCertificate, dataActualServer,
                databaseConnections);

        return info;
    }

    private boolean threadRunning(Thread thread) {
        return thread != null && thread.getState() != Thread.State.TERMINATED
                && thread.getState() != Thread.State.NEW;
    }

    private String getSSOThreadStatus() {
        Thread SSOServer = SeyconApplication.getSso();
        if (threadRunning(SSOServer)) {
            return Messages.getString("SyncStatusServiceImpl.SSORunning"); //$NON-NLS-1$
        }
        return Messages.getString("SyncStatusServiceImpl.SSOFailed"); //$NON-NLS-1$
    }

    private String getJettyThreadStatus() {
        JettyServer jetty = SeyconApplication.getJetty();
        if (jetty != null) {
            if (jetty.isRunning()) {
                return Messages.getString("SyncStatusServiceImpl.JettyRunning"); //$NON-NLS-1$
            }
        }
        return Messages.getString("SyncStatusServiceImpl.JettyFailed"); //$NON-NLS-1$
    }

    private String getSSODaemonThreadStatus() {
        Thread ssoDaemon = SeyconApplication.getSsoDaemon();
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
            ConnectionPool pool = ConnectionPool.getPool();
            //return Messages.getString("SyncStatusServiceImpl.Using") + pool.getNumberOfLockedConnections() + Messages.getString("SyncStatusServiceImpl.De") //$NON-NLS-1$ //$NON-NLS-2$
              //      + pool.getNumberOfConnections() + Messages.getString("SyncStatusServiceImpl.Allocated") + pool.getPoolSize() + " max)"; //$NON-NLS-1$ //$NON-NLS-2$
            StringBuffer buffer = new StringBuffer();
            if (pool.isOfflineMode())
            	buffer.append ("Main database is OFFLINE\n");
            buffer.append(String.format(Messages.getString("SyncStatusServiceImpl.Using"), 
            				new Object [] {pool.getNumberOfLockedConnections(), pool.getNumberOfConnections(), 
            				pool.getNumberOfConnections()}));
            return buffer.toString();
        } catch (Exception e) {
            return Messages.getString("SyncStatusServiceImpl.DataBasePoolFailed") + e.getMessage(); //$NON-NLS-1$
        }
    }

    @Override
    protected Collection<SeyconServerInfo> handleGetServerAgentHostsURL() throws Exception {
        HashMap<String, SeyconServerInfo> hosts = new HashMap<String, SeyconServerInfo>();
        Config config = Config.getConfig();
        String servidors[] = config.getSeyconServerHostList();
        for (int i = 0; i < servidors.length; i++) {
            String host = new URLManager(servidors[i]).getAgentURL().getHost();
            if (!hosts.containsKey(host))
                hosts.put(host, new SeyconServerInfo(host, host));
        }

        for (DispatcherHandler td : getTaskGenerator().getDispatchers()) {
            if (td.getDispatcher().getUrl() != null) {
                String host = new URLManager(td.getDispatcher().getUrl()).getAgentURL().getHost();
                if (!hosts.containsKey(host) && !host.equals("local")) { //$NON-NLS-1$
                    hosts.put(host, new SeyconServerInfo(host, host));
                }
            }
        }
        ArrayList<SeyconServerInfo> result = new ArrayList<SeyconServerInfo>(hosts.values());
        // Afegim una entrada per al principal al final
        result.add(new SeyconServerInfo("agents", Messages.getString("SyncStatusServiceImpl.AllAgents"))); //$NON-NLS-1$ //$NON-NLS-2$

        return result;
    }

    @Override
    protected String handleResetAllServer() throws Exception {
        StringBuffer res = new StringBuffer();
        Vector<String> v = new Vector<String>();
        for (DispatcherHandler td : getTaskGenerator().getDispatchers()) {
            if (td.isConnected()) {
                URL url = new URLManager(td.getDispatcher().getUrl()).getAgentURL();
                String host = url.getHost();
                if (!v.contains(host) && !host.equals("local")) { //$NON-NLS-1$
                    v.add(host);
                    res.append(Messages.getString("SyncStatusServiceImpl.Restarting") + host + "...\n"); //$NON-NLS-1$ //$NON-NLS-2$
                    if (!host.equals("local") && !host.equals(Config.getConfig().getHostName())) { //$NON-NLS-1$
                        try {
                            RemoteServiceLocator rsl = new RemoteServiceLocator(td.getDispatcher()
                                    .getUrl());
                            AgentManager agentMgr = rsl.getAgentManager();
                            agentMgr.reset();
                            res.append(Messages.getString("SyncStatusServiceImpl.Restarted")); //$NON-NLS-1$
                        } catch (Exception e) {
                            res.append(Messages.getString("SyncStatusServiceImpl.ErrorRestarting") + host + ": " + e.toString() + "\n\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                        }
                    }
                }
            }
        }
        return res.toString();
    }

    @Override
    protected String handleResetServerAgents(String server) throws Exception {
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

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SyncStatusService#getAccountPassword(java.lang.String, es.caib.seycon.ng.comu.Account)
	 */
	public Password handleGetAccountPassword (String user, Long accountId)
					throws InternalErrorException
	{
		AccountService svc = getAccountService();
		AccountEntityDao accountDao = getAccountEntityDao();
		UsuariEntityDao usuariDao = getUsuariEntityDao();
		
		AccountEntity accEntity = accountDao.load(accountId);
		Account account = accountDao.toAccount(accEntity);
		if (account == null)
			throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.AccountNotFound"), accountId)); //$NON-NLS-1$
		
		if (account.getType().equals(AccountType.USER))
		{
			boolean found = false;
			for (UserAccountEntity ua: accEntity.getUsers())
			{
				if (ua.getUser().getCodi().equals(user))
					found = true;
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
			for (UserAccountEntity ua: accEntity.getUsers())
			{
				if (!ua.getUser().getCodi().equals(user) &&
						ua.getUntilDate() != null &&
						new Date().before(ua.getUntilDate()))
				{
					ownedByOther = true;
					otherOwner = ua.getUser().getCodi();
				}
				else if (ua.getUser().getCodi().equals(user) && 
						ua.getUntilDate() != null &&
						new Date().before(ua.getUntilDate()))
				{
					found = true;
				}
				else if (ua.getUser().getCodi().equals(user) && 
						ua.getUntilDate() != null &&
						new Date().after(ua.getUntilDate()))
				{
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
			Collection<String> owners = svc.getAccountUsers(account);
			if (! owners.contains(user))
				throw new SecurityException(String.format(Messages.getString("SyncStatusServiceImpl.NotAuthorized"))); //$NON-NLS-1$
		}

		Password p = getSecretStoreService().getPassword(accountId);
		if (p == null && account.getType().equals(AccountType.USER))
		{
			Usuari u = new Usuari();
			for (UserAccountEntity ua: accEntity.getUsers())
			{
				u = getUsuariEntityDao().toUsuari(ua.getUser());
			}
        	p = getSecretStoreService().getSecret(u, "dompass/"+accEntity.getDispatcher().getDomini().getId()); //$NON-NLS-1$
		}
       	return p;
		
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SyncStatusService#getMazingerRules(java.lang.String)
	 */
	public byte[] handleGetMazingerRules (String user) throws InternalErrorException
	{
        try
		{
			Usuari usuari = getServerService().getUserInfo(user, null);
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
		Dispatcher d = getServerService().getDispatcherInfo(dispatcher);
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
		ExtensibleObject source;
		if (type.equals(SoffidObjectType.OBJECT_ACCOUNT))
		{
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			if (acc == null)
			{
				acc = new Account();
				acc.setName(object1);
				acc.setDispatcher(dispatcher);
				acc.setDisabled(true);
			}
			source = new AccountExtensibleObject(acc, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_GROUP) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_GROUP))
		{
			Grup g = null;
			try {
				g = getServerService().getGroupInfo(object2, dispatcher);
			} catch (UnknownGroupException e) {
				g = new Grup();
				g.setCodi(object2);
			}
			Usuari u;
			try {
				u = getServerService().getUserInfo(object1, dispatcher);
			} catch (UnknownUserException e) {
				u = new Usuari();
				u.setCodi(object1);
				u.setActiu(false);
			}
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			source = new MembershipExtensibleObject(acc, u, g, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_ROLES) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_ROLE) ||
				type.equals(SoffidObjectType.OBJECT_GRANT))
		{
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			RolGrant grant = null;
			if (type.equals(SoffidObjectType.OBJECT_GRANT))
			{
				try {
					for ( Grup group: getServerService().getUserGroups(object1, dispatcher))
					{
						if (group.getCodi().equals (object2))
						{
							grant = new RolGrant();
							grant.setDispatcher(dispatcher);
							grant.setEnabled(true);
							grant.setOwnerDispatcher(dispatcher);
							grant.setRolName(group.getCodi());
							grant.setUser(null);
						}
					}
				} catch (UnknownUserException e) {
				}
			}
			Collection<RolGrant> list;
			if (type.equals(SoffidObjectType.OBJECT_GRANTED_ROLE))
				list = getServerService().getAccountExplicitRoles(object1, dispatcher);
			else
				list = getServerService().getAccountRoles(object1, dispatcher);
			for (RolGrant grant2: list)
			{
				if (grant2.getRolName().equals (object2))
					grant = grant2;
			}
			if (grant == null)
			{
				grant = new RolGrant();
				grant.setDispatcher(dispatcher);
				grant.setEnabled(true);
				grant.setOwnerDispatcher(dispatcher);
				grant.setRolName(object2);
			}
			source = new GrantExtensibleObject(grant, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_MAIL_LIST))
		{
			LlistaCorreu ml = null;
			try {
				ml = getServerService().getMailList(object1, object2);
			} catch (UnknownMailListException e) {
			}
			if (ml == null)
			{
				ml = new LlistaCorreu();
				ml.setCodiDomini(object2);
				ml.setNom(object1);
			}
			source = new MailListExtensibleObject(ml, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_ROLE))
		{
			Rol role = null;
			try {
				role = getServerService().getRoleInfo(object1, dispatcher);
			} catch (UnknownRoleException e) {
			}
			if (role == null)
			{
				role = new Rol();
			}
			source = new RoleExtensibleObject(role, getServerService());
		}
		else if (type.equals(SoffidObjectType.OBJECT_USER))
		{
			Usuari u;
			try {
				u = getServerService().getUserInfo(object1, dispatcher);
			} catch (UnknownUserException e) {
				u = new Usuari();
				u.setCodi(object1);
				u.setActiu(false);
			}
			Account acc = getServerService().getAccountInfo(object1, dispatcher);
			if (acc == null)
			{
				acc = new Account();
				acc.setName(object1);
				acc.setDispatcher(dispatcher);
				acc.setDisabled(true);
			}
			source = new UserExtensibleObject(acc, u, getServerService());
		} else {
			source = new ExtensibleObject();
		}
		
		return source;
	}

	@Override
	public Exception handleTestPropagateObject(String dispatcher,
			SoffidObjectType type, String object1, String object2)
			throws InternalErrorException, InternalErrorException {
		TaskHandler task = generateObjectTask(dispatcher, type, object1, object2);
		Map map = getTaskQueue().processOBTask(task);
		return (Exception) map.get(dispatcher);
	}

	private TaskHandler generateObjectTask(String dispatcher, SoffidObjectType type,
			String object1, String object2) throws InternalErrorException {
		Tasca tasca = new Tasca();
		tasca.setDataTasca(Calendar.getInstance());
		tasca.setCoddis(dispatcher);
		tasca.setExpirationDate(Calendar.getInstance());
		tasca.getExpirationDate().add(Calendar.MINUTE, 5);
		
		TaskHandler th = new TaskHandler();
		th.setTask(tasca);
		if (type.equals(SoffidObjectType.OBJECT_ACCOUNT))
		{
			tasca.setTransa(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUsuari(object1);
			tasca.setBd(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_GROUP) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_GROUP))
		{
			tasca.setTransa(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUsuari(object1);
			tasca.setBd(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_ALL_GRANTED_ROLES) ||
				type.equals(SoffidObjectType.OBJECT_GRANTED_ROLE) ||
				type.equals(SoffidObjectType.OBJECT_GRANT))
		{
			tasca.setTransa(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUsuari(object1);
			tasca.setBd(dispatcher);
		}
		else if (type.equals(SoffidObjectType.OBJECT_MAIL_LIST))
		{
			tasca.setTransa(TaskHandler.UPDATE_LIST_ALIAS);
			tasca.setAlies(object1);
			tasca.setDomcor(object2);
		}
		else if (type.equals(SoffidObjectType.OBJECT_ROLE))
		{
			tasca.setTransa(TaskHandler.UPDATE_ROLE);
			tasca.setRole(object1);
			tasca.setBd(object2);
		}
		else if (type.equals(SoffidObjectType.OBJECT_USER))
		{
			tasca.setTransa(TaskHandler.UPDATE_ACCOUNT);
			tasca.setUsuari(object1);
			tasca.setBd(dispatcher);
		}
		
		return th;
	}

	@Override
	protected void handleBoostTask(long taskId) throws Exception {
		TaskHandler th = getTaskQueue().findTaskHandlerById(taskId);
		for ( TaskHandlerLog log: th.getLogs())
		{
			if (!log.isComplete())
			{
				log.setNext(System.currentTimeMillis());
			}
		}
		th.getTask().setDataTasca(Calendar.getInstance());
	}

	@Override
	protected void handleCancelTask(long taskId) throws Exception {
		getTaskQueue().cancelTask(taskId);
	}
}

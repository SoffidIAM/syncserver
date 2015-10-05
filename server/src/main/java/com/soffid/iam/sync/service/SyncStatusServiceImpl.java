package com.soffid.iam.sync.service;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.AgentStatusInfo;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.SyncServerInfo;
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
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.SyncStatusServiceBase;
import com.soffid.iam.sync.service.TaskQueue;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownUserException;

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
import java.util.Vector;

import javax.servlet.ServletException;

import org.apache.commons.logging.LogFactory;

public class SyncStatusServiceImpl extends SyncStatusServiceBase {
    @Override
    protected Collection<AgentStatusInfo> handleGetSyncAgentsInfo() throws Exception {
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
                    agent.setStatusMessage("NULL");
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
    }

    @Override
    protected SyncServerInfo handleGetSyncServerStatus() throws Exception {
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
        SyncServerInfo info = new SyncServerInfo(url, "Sync server", versio, estat, numAgents, //$NON-NLS-1$
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
    protected SyncServerInfo handleGetSyncServerInfo() throws Exception {
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

        SyncServerInfo info = new SyncServerInfo(url, "Sync server", versio, estat, numAgents, //$NON-NLS-1$
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
            ConnectionPool pool = ConnectionPool.getPool();
            //return Messages.getString("SyncStatusServiceImpl.Using") + pool.getNumberOfLockedConnections() + Messages.getString("SyncStatusServiceImpl.De") //$NON-NLS-1$ //$NON-NLS-2$
              //      + pool.getNumberOfConnections() + Messages.getString("SyncStatusServiceImpl.Allocated") + pool.getPoolSize() + " max)"; //$NON-NLS-1$ //$NON-NLS-2$
            StringBuffer buffer = new StringBuffer();
            buffer.append(String.format(Messages.getString("SyncStatusServiceImpl.Using"), 
            				new Object [] {pool.getNumberOfLockedConnections(), pool.getNumberOfConnections(), 
            				pool.getNumberOfConnections()}));
            return buffer.toString();
        } catch (Exception e) {
            return Messages.getString("SyncStatusServiceImpl.DataBasePoolFailed") + e.getMessage(); //$NON-NLS-1$
        }
    }

    @Override
    protected Collection<SyncServerInfo> handleGetServerAgentHostsURL() throws Exception {
        HashMap<String, SyncServerInfo> hosts = new HashMap<String, SyncServerInfo>();
        Config config = Config.getConfig();
        String servidors[] = config.getSeyconServerHostList();
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
        StringBuffer res = new StringBuffer();
        Vector<String> v = new Vector<String>();
        for (DispatcherHandler td : getTaskGenerator().getDispatchers()) {
            if (td.isConnected()) {
                URL url = new URLManager(td.getSystem().getUrl()).getAgentURL();
                String host = url.getHost();
                if (!v.contains(host) && !host.equals("local")) { //$NON-NLS-1$
                    v.add(host);
                    res.append(Messages.getString("SyncStatusServiceImpl.Restarting") + host + "...\n"); //$NON-NLS-1$ //$NON-NLS-2$
                    if (!host.equals("local") && !host.equals(Config.getConfig().getHostName())) { //$NON-NLS-1$
                        try {
                            RemoteServiceLocator rsl = new RemoteServiceLocator(td.getSystem()
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
			Collection<String> owners = svc.getAccountUsers(account);
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
				} catch (Throwable e) {
					LogFactory.getLog(SyncStatusServiceImpl.class).warn("Error updating configuration", e);
				}
			}
		}.start();
	}

}

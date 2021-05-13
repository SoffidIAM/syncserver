package com.soffid.iam.sync.engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.HostService;
import com.soffid.iam.api.MailList;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordDomain;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.ReconcileTrigger;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.UserType;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.AuditEntity;
import com.soffid.iam.model.AuditEntityDao;
import com.soffid.iam.model.TaskEntity;
import com.soffid.iam.model.TaskEntityDao;
import com.soffid.iam.model.TenantEntity;
import com.soffid.iam.model.TenantEntityDao;
import com.soffid.iam.reconcile.common.AccountProposedAction;
import com.soffid.iam.reconcile.common.ProposedAction;
import com.soffid.iam.reconcile.common.ReconcileAccount;
import com.soffid.iam.reconcile.common.ReconcileAssignment;
import com.soffid.iam.reconcile.common.ReconcileRole;
import com.soffid.iam.reconcile.service.ReconcileService;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.InternalPasswordService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.agent.AgentInterface;
import com.soffid.iam.sync.agent.AgentManager;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.intf.DebugTaskResults;
import com.soffid.iam.sync.engine.intf.GetObjectResults;
import com.soffid.iam.sync.engine.kerberos.KerberosManager;
import com.soffid.iam.sync.intf.AccessControlMgr;
import com.soffid.iam.sync.intf.AccessLogMgr;
import com.soffid.iam.sync.intf.CustomObjectMgr;
import com.soffid.iam.sync.intf.CustomTaskMgr;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObjectMgr;
import com.soffid.iam.sync.intf.GroupMgr;
import com.soffid.iam.sync.intf.HostMgr;
import com.soffid.iam.sync.intf.KerberosAgent;
import com.soffid.iam.sync.intf.LogEntry;
import com.soffid.iam.sync.intf.MailAliasMgr;
import com.soffid.iam.sync.intf.NetworkMgr;
import com.soffid.iam.sync.intf.ReconcileMgr;
import com.soffid.iam.sync.intf.ReconcileMgr2;
import com.soffid.iam.sync.intf.RoleMgr;
import com.soffid.iam.sync.intf.ServiceMgr;
import com.soffid.iam.sync.intf.SharedFolderMgr;
import com.soffid.iam.sync.intf.UserMgr;
import com.soffid.iam.sync.service.ChangePasswordNotificationQueue;
import com.soffid.iam.sync.service.LogCollectorService;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.sync.service.SyncServerStatsService;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.service.TaskQueue;
import com.soffid.iam.sync.service.UserGrantsCache;
import com.soffid.iam.util.Syslogger;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.AgentMirror;

public class DispatcherHandlerImpl extends DispatcherHandler implements Runnable {
    private static JbpmConfiguration jbpmConfig;
	Logger log = LoggerFactory.getLogger(DispatcherHandler.class);
    boolean active = true;
    private boolean reconfigure = false;
    private ThreadLocal<Object> agents = new ThreadLocal<Object>();
    private Object staticAgent = null;
    private boolean actionStop;
    private long nextConnect;
    private Exception connectException;
    private String agentVersion;
    private TaskQueue taskqueue;
    private TaskGenerator taskgenerator;
    private SecretStoreService secretStoreService;
    private String targetHost;
    private ChangePasswordNotificationQueue changePasswordNotificationQueue;
    private TaskEntityDao tasqueEntityDao;
	private ReconcileService reconcileService;

	private AuditEntityDao auditoriaDao;
	private static final int MAX_LENGTH = 150;
	private static final int MAX_ROLE_CODE_LENGTH = 50;
	private PasswordDomain passwordDomain = null;

	private enum DispatcherStatus {
		STARTING,
		LOOP_START,
		NEXT_TASK,
		GET_LOGS,
		WAIT
	}
	DispatcherStatus dispatcherStatus = DispatcherStatus.STARTING;
	private com.soffid.iam.sync.service.ServerService server;
	private UserDomainService domainService;
	private InternalPasswordService internalPasswordService;
	private AccountService accountService;
	private es.caib.seycon.ng.sync.engine.extobj.ObjectTranslator attributeTranslator;
	private com.soffid.iam.sync.engine.extobj.ObjectTranslator attributeTranslatorV2;
	private TenantEntityDao tenantDao;
	private String mirroredAgent;
	private SyncServerStatsService statsService = ServiceLocator.instance().getSyncServerStatsService();
	private boolean debugEnabled;
	
	public PasswordDomain getPasswordDomain() throws InternalErrorException
	{
		if (passwordDomain == null)
		{
			passwordDomain = domainService.findPasswordDomainByName(getSystem().getPasswordsDomain());
		}
		return passwordDomain;
	}
	
    public String getAgentVersion ()
	{
		return agentVersion;
	}

	public void setAgentVersion (String agentVersion)
	{
		this.agentVersion = agentVersion;
	}

	public Exception getConnectException() {
        return connectException;
    }

    public DispatcherHandlerImpl() {
    	debugEnabled = "true".equals(ConfigurationCache.getProperty("soffid.debug.dispatcher"));
        server = ServerServiceLocator.instance().getServerService();
        taskqueue = ServerServiceLocator.instance().getTaskQueue();
        taskgenerator = ServerServiceLocator.instance().getTaskGenerator();
        secretStoreService = ServerServiceLocator.instance().getSecretStoreService();
        changePasswordNotificationQueue = ServerServiceLocator.instance().getChangePasswordNotificationQueue();
        tasqueEntityDao = (TaskEntityDao) ServerServiceLocator.instance().getService("taskEntityDao");
        internalPasswordService = 
        		ServerServiceLocator.instance().getInternalPasswordService();
        accountService = ServerServiceLocator.instance().getAccountService();
        domainService = ServerServiceLocator.instance().getUserDomainService();
        auditoriaDao = (AuditEntityDao) ServerServiceLocator.instance().getService("auditEntityDao");
        tenantDao = (TenantEntityDao) ServerServiceLocator.instance().getService("tenantEntityDao");
        reconcileService = ServerServiceLocator.instance().getReconcileService();
        
        active = true;
    }

    @Override
    public boolean applies(TaskHandler t) {
    	return applies ( getCurrentAgent(), t);
    }

	private Object getCurrentAgent() {
		if (Boolean.TRUE.equals(system.getSharedDispatcher()))
			return staticAgent;
		else
			return agents.get();
	}
    	
	private void clearCurrentAgent() {
		if (Boolean.TRUE.equals(system.getSharedDispatcher()))
			staticAgent = null;
		else
			agents.remove();
		lastAgent = null;
	}

	private void setCurrentAgent(Object agent) {
		if (Boolean.TRUE.equals(system.getSharedDispatcher()))
			staticAgent = agent;
		else
			agents.set(agent);
		lastAgent = agent;
	}

    public boolean applies(Object agent, TaskHandler t) {
    	    
        String trans = t.getTask().getTransaction();
        boolean trusted = isTrusted();
        boolean readOnly = getSystem().isReadOnly();
        // Verificar el nom del dipatcher
        if (t.getTask().getSystemName() != null &&
        		( mirroredAgent != null && 
        				!mirroredAgent.equals(t.getTask().getSystemName()) &&
        				! getSystem().getName().equals(t.getTask().getSystemName()))) {
            return false;

            // Verificar el domini de contrasenyes
        } else if (t.getTask().getPasswordDomain() != null
                && !t.getTask().getPasswordDomain()
                        .equals(getSystem().getPasswordsDomain())) {
            return false;

            // Verificar el domini d' usuaris
        } else if (t.getTask().getUserDomain() != null
                && !t.getTask().getUserDomain().equals(getSystem().getUsersDomain())) {
            return false;

        } else if (t.getTask().getTransaction().equals(TaskHandler.UPDATE_USER)) {
        	if (readOnly)
        		return false;
        	if (agent != null)
        		return implemented(agent, UserMgr.class) ||
        				implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class);
	        try {
				User user = getUserInfo(t);
				if (isUnmanagedType ( user.getUserType()))
					return false;
				
				if (getAccounts(t).isEmpty())
					return false;
			} catch (Exception e) {
			}
	        
	        return true;
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_ACCOUNT)) {
            return !readOnly && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class) );
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_USER_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class ) ) &&
            	getSystem().getPasswordsDomain().equals(t.getTask().getPasswordDomain());
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class ) );
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_PROPAGATED_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.PROPAGATE_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class)  || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.PROPAGATE_ACCOUNT_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.EXPIRE_USER_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class)  );
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.EXPIRE_USER_UNTRUSTED_PASSWORD)) {
            return !readOnly && !trusted && (implemented(agent, UserMgr.class) || implemented(agent, UserMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.VALIDATE_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class) ) &&
                        	getSystem().getPasswordsDomain().equals(t.getTask().getPasswordDomain());

        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.VALIDATE_ACCOUNT_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class) || implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class)  );

        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.CREATE_FOLDER)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.SharedFolderMgr.class) 
            		|| implemented(agent, com.soffid.iam.sync.intf.SharedFolderMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_GROUP)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.GroupMgr.class) ||
            				implemented(agent, com.soffid.iam.sync.intf.GroupMgr.class)); 
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_ROLE)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.RoleMgr.class) ||
            		implemented(agent, com.soffid.iam.sync.intf.RoleMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_HOST)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.HostMgr.class) ||
            		implemented(agent,com.soffid.iam.sync.intf.HostMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.PURGE_HOSTS)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.HostMgr.class)||
            		implemented(agent,com.soffid.iam.sync.intf.HostMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.GET_LOG)) {
            return implemented(agent, es.caib.seycon.ng.sync.intf.AccessLogMgr.class)||
            		implemented(agent,com.soffid.iam.sync.intf.AccessLogMgr.class);
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_NETWORKS)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.NetworkMgr.class) ||
            		implemented(agent,com.soffid.iam.sync.intf.NetworkMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_USER_ALIAS)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.MailAliasMgr.class) ||
            		implemented(agent,com.soffid.iam.sync.intf.MailAliasMgr.class));
        } else if (trans.equals(TaskHandler.UPDATE_LIST_ALIAS)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.MailAliasMgr.class) ||
            		implemented(agent,com.soffid.iam.sync.intf.MailAliasMgr.class));
        } else if (trans.equals(TaskHandler.UPDATE_OBJECT)) {
            return !readOnly && (implemented(agent, CustomObjectMgr.class));
        }
		else if (trans.equals(TaskHandler.END_RECONCILE))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class) ||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class)||
            		implemented(agent,com.soffid.iam.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_USER))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class)||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class)||
            		implemented(agent,com.soffid.iam.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_USERS))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class)||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_ROLE))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class) ||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class)||
            		implemented(agent,com.soffid.iam.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_ROLES))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class) ||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class)||
            		implemented(agent,com.soffid.iam.sync.intf.ReconcileMgr2.class);
	        ///////////////////////////////////////////////////////////////////////
		}
		else if (trans.equals(TaskHandler.UPDATE_SERVICE_PASSWORD))
		{
			return implemented(agent, ServiceMgr.class);
        } else {
            return implemented(agent, es.caib.seycon.ng.sync.intf.CustomTaskMgr.class) ||
            		implemented(agent,CustomTaskMgr.class);
        }
    }

    private boolean isTrusted() {
        return getSystem().getTrusted() != null && getSystem().getTrusted().booleanValue();
    }

    @Override
    public boolean isComplete(TaskHandler task) {
        if (task.getLogs().size() <= internalId) {
            return false;
        }
        TaskHandlerLog tasklog = task.getLogs().get(internalId);
        if (tasklog == null)
            return false;

        return tasklog.isComplete();
    }

    @Override
    public boolean isError(TaskHandler task) {
        if (!applies(task)) {
            return false;
        }
        if (task.getLogs().size() <= internalId) {
            return false;
        }
        TaskHandlerLog tasklog = task.getLogs().get(internalId);
        if (tasklog == null)
            return false;

        return !tasklog.isComplete() && tasklog.getNumber() > 0;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void reconfigure() {
        reconfigure = true;
        int max = getSystem().getThreads() == null ||
        		getSystem().getThreads().longValue() < 2 ?
        				1 :
        				getSystem().getThreads().intValue();
        int n = 0;
        for (Thread thread: new LinkedList<Thread>(activeThreads))
        {
        	if (n >= max) 
        		activeThreads.remove(thread);
        	else if (max > 1)
    			thread.setName(getSystem().getTenant()+"\\"+getSystem().getName()+"#"+(n+1));
    		else
    			thread.setName(getSystem().getTenant()+"\\"+getSystem().getName());
    		thread.interrupt();
        	n++;
        }
        while (n < max)
        {
    		Thread th = new Thread(this);
    		if (max > 1)
    			th.setName(getSystem().getTenant()+"\\"+getSystem().getName()+"#"+(n+1));
    		else
    			th.setName(getSystem().getTenant()+"\\"+getSystem().getName());
    		activeThreads.add (th);
    		th.start();
    		try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
    		n++;
    	}

        passwordDomain = null;
    }

    
    @Override
    public void setSystem(com.soffid.iam.api.System dispatcher) throws InternalErrorException {
        log = LoggerFactory.getLogger(dispatcher.getTenant()+"\\"+dispatcher.getName());
        super.setSystem(dispatcher);
    	attributeTranslator = new es.caib.seycon.ng.sync.engine.extobj.ObjectTranslator(Dispatcher.toDispatcher(dispatcher));
    	attributeTranslatorV2 = new com.soffid.iam.sync.engine.extobj.ObjectTranslator(dispatcher);
    }

    public void reconfigure(com.soffid.iam.api.System newDispatcher) throws InternalErrorException {
        setSystem(newDispatcher);

        reconfigure();

    }

    public void gracefullyStop() {
        active = false;
    }

    

    List<Thread> activeThreads;
    public void start() {
        int max = getSystem().getThreads() == null ||
        		getSystem().getThreads().longValue() < 2 ?
        				1 :
        				getSystem().getThreads().intValue();
    	activeThreads = Collections.synchronizedList ( new LinkedList<Thread>() );
    	int n = 0;
    	do {
    		if (n > 0)
    		{
    			try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
    		}
    		Thread th = new Thread(this);
    		activeThreads.add (th);
    		if (max > 1)
    			th.setName(getSystem().getTenant()+"\\"+getSystem().getName()+"#"+(n+1));
    		else
    			th.setName(getSystem().getTenant()+"\\"+getSystem().getName());
    		th.start();
    		n++;
    	} while (n < max);
    }

    int delay, timeoutDelay;
    boolean abort = false;
	private long waitUntil;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> preInsertTrigger;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> postInsertTrigger;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> preUpdateTrigger;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> postUpdateTrigger;
	private Object lastAgent;


    public void run() {
    	String hostName = null;
		try {
			hostName = Config.getConfig().getHostName();
		} catch (IOException e1) {
			// Unlikely error
		}
    	Security.nestedLogin(getSystem().getTenant(), hostName, 
        		Security.ALL_PERMISSIONS
        	);
        try {
        	ConnectionPool pool = ConnectionPool.getPool();
        	
            Thread currentThread = Thread.currentThread();
            
            // boolean runTimedOutTasks = true;
            log.info("Registered dispatcher thread", null, null);
            runInit();
            // setName (agentName);
            while (active && activeThreads.contains(currentThread)) {
                // Actualiza información del último bucle realizado
//                taskQueueStartTime = new java.util.Date().getTime();

                // Forzar la reconexion

                try {
                    // //////////////////////////////////////////////////////////
                    // Ejecutar las transacciones
                    runLoopStart();
                    boolean ok = true;
                    setStatus("Getting Task");
                    while (!abort && !actionStop && !reconfigure && getCurrentAgent() != null) {
                        if (!runLoopNext())
                        	break;
                    }
                } catch (Throwable e) {
                    log.warn("Error on dispatcher loop", e);
                }
                // //////////////////////////////////////////////////////////
                // Recuperar los logs
                runGetLogs();
                // El séptimo dia descansó
                setStatus("Sleeping");
                try {
                    if (!actionStop)
                        Thread.sleep(delay);
                } catch (InterruptedException e2) {
                }
            } // Fin del bucle infinito (qué paradoja!)
        } catch (Throwable e) {
            log.warn("Distpacher dead", e);
            active = false;
        } finally {
            setStatus("Stopped");
            log.info("Stopped", null, null);
            Security.nestedLogoff();
        }

    }

	private void runGetLogs() throws InternalErrorException {
		Object agent = getCurrentAgent();
		if (!actionStop && !abort && agent != null)
		{
			try {
				if (agent instanceof AgentInterface && ((AgentInterface) agent).isSingleton() || 
						agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface && ((es.caib.seycon.ng.sync.agent.AgentInterface) agent).isSingleton())
				{
					try {
						Object agent1 = connect (false, false, system.getUrl());
					    getLogsStep(agent1);
					} catch (Exception e) {
						log.info("Service "+system.getName()+" is offline at "+system.getUrl());
					}
					// Check for backup server
					if (system.getUrl2() != null && ! system.getUrl2().trim().isEmpty()) {
						try {
							Object agent2 = connect (false, false, system.getUrl2());
							getLogsStep(agent2);
						} catch (Exception e) {
							log.info("Service "+system.getName()+" is offline at "+system.getUrl2());
						}
					}
				}
				else
				{
				    getLogsStep(agent);
				}
			} catch (Throwable th) {
				log.info("Error getting logs: "+th);
			}
		}
		waitUntil = System.currentTimeMillis() + delay;
	}

	public void getLogsStep(Object agent) throws InternalErrorException {
		if (taskgenerator.canGetLog(this)) {
			setStatus("Retrieving logs");
			try {
				startTask(true);
			    getLog(agent);
			} catch (RemoteException e) {
			    handleRMIError(e);
			} catch (InternalErrorException e) {
			    log.info("Cannot retrieve logs: {}", e.getMessage(), null);
			} catch (Throwable e) {
			    log.warn("Cannot retrieve logs: {}", e);
			} finally {
				endTask();
			    taskgenerator.finishGetLog(this);
			}
		}
	}

	private boolean runLoopNext()
			throws InternalErrorException {
    	ConnectionPool pool = ConnectionPool.getPool();
		try {
			startTask(false);
			return processAndLogTask();
		} finally {
			endTask();
		}
	}

	private void runLoopStart() throws InternalErrorException {
		if (reconfigure) {
			if (getCurrentAgent() != null)
			{
				log.info ("Disconnecting agent in order to apply new configuration");
				closeAgent(getCurrentAgent());
			}
		    clearCurrentAgent();
		    lastAgent = null;
		    nextConnect = 0;
		    reconfigure = false;
		}
		// //////////////////////////////////////////////////////////
		// Contactar con el agente
		//
		if (getCurrentAgent() == null && nextConnect < new java.util.Date().getTime()) {
		    try {
		        setStatus("Looking up server");
		        log.info("Connecting ...");
		        Object newAgent = connect(true, false);
				setCurrentAgent( newAgent );
		    } catch (Throwable e) {
		    	nextConnect = new java.util.Date().getTime() + timeoutDelay;
		        delay = timeoutDelay;
		        if (e instanceof Exception)
		        	connectException = (Exception) e;
		        else
		        	connectException = new InternalErrorException("Unexepcted error", e);
		        log.warn("Error connecting {}: {} ", getSystem().getName(), e.toString());
		    }
		}

        boolean ok = true;
        setStatus("Getting Task");

	}

	private void closeAgent(Object agent) {
		try {
			if (agent == null)
				return;
			if (agent instanceof AgentInterface)
				((AgentInterface) agent).close();
			else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
				((es.caib.seycon.ng.sync.agent.AgentInterface) agent).close();
		} catch (Exception e) {}
	}

	private void runInit() throws InternalErrorException {
		String retry = server.getConfig("server.dispatcher.delay");
		if (retry == null)
		    delay = 10000;
		else
		    delay = Integer.decode(retry).intValue() * 1000;
		retry = server.getConfig("server.dispatcher.timeout");
		if (retry == null)
		    timeoutDelay = 60000;
		else
		    timeoutDelay = Integer.decode(retry).intValue() * 1000;
	}

	/**
	 * @throws Exception 
	 * 
	 */
	public void doAuthoritativeImport (ScheduledTask task, PrintWriter out) 
	{
		AuthoritativeLoaderEngine engine = new AuthoritativeLoaderEngine(this);
		engine.doAuthoritativeImport(task, out);
	}

	private boolean processAndLogTask () throws InternalErrorException
	{
		String reason = "";
		boolean ok = false;
		TaskHandler currentTask = taskqueue.getPendingTask(this);
		if (currentTask == null)
		{
			if (debugEnabled)
				log.info("No task to do ", null, null);
			return false;

		}
		Throwable throwable = null;
		try
		{
			
			TaskHandlerLog thl = currentTask.getLog(getInternalId());
			reason = "";
			setStatus("Execute " + currentTask.toString());
			if (debugEnabled)
				log.info("Executing {} ", currentTask.toString(), null);
			try {
				processTask(getCurrentAgent(), currentTask);
				ok = true;
				statsService.register("tasks-success", getName(), 1);
				if (debugEnabled)
					log.debug("Task {} DONE", currentTask.toString(), null);
			} catch (RemoteException e) {
				handleRMIError(e);
				ok = false;
				reason = "Cannot connect to " + getSystem().getUrl();
				throwable = e;
				// abort = true ;
			} catch (Throwable e) {
				if (debugEnabled)
					log.info("Failed {} ", currentTask.toString(), null);
				statsService.register("tasks-error", getName(), 1);
				if ("local".equals(system.getUrl()))
				{
//					log.warn("Error interno", e);
				} else {
					String error = SoffidStackTrace.getStackTrace(e)
							.replaceAll("java.lang.OutOfMemoryError", "RemoteOutOfMemoryError");
//					log.warn("Error interno: "+error);
				}
				ok = false;
				Throwable e2 = e;
				while (e2.getCause() != null && e2.getCause() != e2)
				{
					e2 = e2.getCause();
				}
				if (e2.getClass() == InternalErrorException.class)
					reason = e2.getMessage();
				else
					reason = e2.toString();
				throwable = e;
			}
		} finally {
			taskqueue.notifyTaskStatus(currentTask, this, ok, reason, throwable);
		}
		return true;
	}

    /**
     * Tratar los errores RMI
     * 
     * @param e
     *            error producido
     */
    void handleRMIError(Exception e) {
        if (getCurrentAgent() != null) {
            log.info("Connection error {}", e.toString(), null);
            clearCurrentAgent();
            lastAgent = null;
            nextConnect = 0;
        }
    }

    /**
     * Determinar si el objeto RMI remoto implementa undeterminado interfaz Hace
     * la consulta via reflection. En caso de duda, asumo que si lo implementa.
     * 
     * @param interfaceName
     *            nombre cualificado del interfaz
     * @return true si lo immplementa
     */
    public boolean implemented(Object agent, Class clazz) {
        if (agent == null)
            return true;
        
        return clazz.isAssignableFrom(agent.getClass());
    }

    /**
     * Generar una password aleatoria. Se utiliza cuando se desea que el usuario
     * no tenga acceso al sistema, ya sea porque está caducado, o bien esta
     * obligado a cambiarla
     * 
     * @return password aleatoria a asignar
     * @throws UnknownUserException
     * @throws InternalErrorException
     */
    private Password generateRandomUserPassword(TaskHandler t) throws InternalErrorException {
        User uservo = getTaskUser(t);
        if (uservo == null)
            throw new InternalErrorException(String.format("Unknown user %s", uservo));
        return server.generateFakePassword(getSystem().getPasswordsDomain());
    }

    private Password getTaskPassword(TaskHandler t) throws RemoteException, InternalErrorException {
        return t.getPassword();
    }

    /**
     * Procesar una tarea
     * 
     * @param t
     *            tarea a procesar
     * @throws RemoteException
     *             error de comunicaciones
     * @throws InteranlErrorException
     *             error de lógica del agente
     */
    public void processTask(Object agent, TaskHandler t) throws RemoteException, InternalErrorException {
        if (!applies(agent, t)) {
            log.debug("Task ignored: {}", t, null);
            return;
        }
        log.debug ("Processing task {}", t);
        if (agent == null) {
            if (connectException == null)
                throw new RemoteException("Agente no conectado");
            else if (connectException instanceof RemoteException)
                throw (RemoteException) connectException;
            else
                throw new RemoteException("Unable to connect to " + getSystem().getUrl(),
                        connectException);
        }
        String trans = t.getTask().getTransaction();
        UserGrantsCache.setGrantsCache(new UserGrantsCache(getTaskUser(t), t.getGrants() ));
        try {
	        // /////////////////////////////////////////////////////////////////////
	        if (trans.equals(TaskHandler.UPDATE_ACCOUNT)) {
	            updateAccount(agent, t);
	        } 
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_USER)) {
	            updateUser(agent, t);
	        }
	        // //////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_USER_PASSWORD)) {
	            updateUserPassword(agent, t);
	        }
	        // //////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD)) {
	            updateAccountPassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_PROPAGATED_PASSWORD)) {
	            updatePropagatedPassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.EXPIRE_USER_PASSWORD)
	                || trans.equals(TaskHandler.EXPIRE_USER_UNTRUSTED_PASSWORD)) {
	            expireUserPassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.PROPAGATE_PASSWORD)) {
	            propagateUserPassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.PROPAGATE_ACCOUNT_PASSWORD)) {
	            propagatePassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.VALIDATE_PASSWORD)) {
	            validatePassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.VALIDATE_ACCOUNT_PASSWORD)) {
	            validateAccountPassword(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.CREATE_FOLDER)) {
	            createFolder(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_GROUP)) {
	            updateGroup(agent, t);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_ROLE)) {
	            updateRole(agent, t, trans);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_HOST)) {
	            updateHost(agent, t);
	        } else if (trans.equals(TaskHandler.UPDATE_NETWORKS)) {
	            updateNetworks(agent);
	        }
	        // /////////////////////////////////////////////////////////////////////
	        else if (trans.equals(TaskHandler.UPDATE_USER_ALIAS)) {
	            updateUserAlias(agent, t);
	        } else if (trans.equals(TaskHandler.UPDATE_LIST_ALIAS)) {
	            updateMailList(agent, t);
	        } else if (trans.equals(TaskHandler.UPDATE_ACESS_CONTROL)) {
	            updateAccessControl(agent);
			// /////////////////////////////////////////////////////////////////////
	        }else if (trans.equals(TaskHandler.END_RECONCILE))
			{
	        	endReconcile(agent, t);
			}
	        
	        else if (trans.equals(TaskHandler.RECONCILE_USERS))
	        {
	        	reconcileUsers(agent, t);
	        }
	
			else if (trans.equals(TaskHandler.RECONCILE_USER))
			{
				reconcileUser(agent, t);
			}
	
			else if (trans.equals(TaskHandler.RECONCILE_ROLE))
			{
				reconcileRole(agent, t);
			}
	
			else if (trans.equals(TaskHandler.RECONCILE_ROLES))
			{
				reconcileRoles(agent, t);
			}
			else if (trans.equals(TaskHandler.UPDATE_OBJECT))
			{
				updateObject(agent, t);
			}
			else if (trans.equals(TaskHandler.UPDATE_PRINTER))
			{
				// Nothing to do
			}
			else if (trans.equals(TaskHandler.UPDATE_SERVICE_PASSWORD))
			{
				updateServicePassword(agent, t);
	        } else {
	        	processCustomTask(agent, t);
	        }
        } finally {
        	UserGrantsCache.clearGrantsCache();
        }
    }

	private void updateServicePassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
		if (agent instanceof ServiceMgr) {
			ServiceMgr mgr = (ServiceMgr) agent;
           	Account acc = accountService.findAccount(t.getTask().getUser(), mirroredAgent);
           	for (Host host: ServiceLocator.instance().getNetworkDiscoveryService().findSystemHosts(getSystem())) {
           		for (HostService service: accountService.findAccountServices(acc)) {
           			if (service.getHostName().equals(host.getName()))	
           				mgr.setServicePassword(service.getService(), acc, t.getPassword());
           		}
           	}
		}
	}

	private void processCustomTask(Object agent, TaskHandler t) throws RemoteException, InternalErrorException {
		com.soffid.iam.sync.intf.CustomTaskMgr mgr = InterfaceWrapper.getCustomTaskMgr(agent);
		if (mgr != null)
		{
			mgr.processTask(t.getTask());
		}
	}

	/**
	 * @param agent 
     * @param t
     * @throws InternalErrorException 
     * @throws RemoteException 
	 */
	private void validateAccountPassword (Object agent, TaskHandler t) throws RemoteException, InternalErrorException
	{
        if (!isTrusted() || t.isValidated() || t.isExpired() || t.isComplete())
            return;

        com.soffid.iam.sync.intf.UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr != null)
        {
       		if (userMgr.validateUserPassword(t.getTask().getUser(), t.getPassword())) {
                t.setValidated(true);
                synchronized (t) {
                    t.notify();
                }
                cancelTask(t);
            }
        }
        else
        	return;
	}

	/**
	 * @param t
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 */
	private void updateAccount (Object agent, TaskHandler t) throws RemoteException, InternalErrorException
	{
		com.soffid.iam.sync.intf.UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);

		if (userMgr != null)
		{
			if (t.getTask().getUser() == null || t.getTask().getUser().trim().length() == 0 )
				return;
           	Account acc = accountService.findAccount(t.getTask().getUser(), mirroredAgent);
           	if (acc != null && ( acc.getType().equals (AccountType.IGNORED) ||
           			isUnmanagedType (acc.getPasswordPolicy())))
           	{
           		// Nothing to do
           		return;
           	}
           	
           	if (acc == null )
           	{
       			userMgr.removeUser(t.getTask().getUser());
       	        AuditEntity auditoria = auditoriaDao.newAuditEntity();
       	        auditoria.setAction("A"); // Applied changes
       	        auditoria.setDate(new Date());
       	        auditoria.setAccount(t.getTask().getUser());
       	        auditoria.setObject("SC_ACCOUN");
       	        auditoria.setDb(getSystem().getName());
       	        auditoriaDao.create(auditoria);
           	}
           	else if (acc.isDisabled())
           	{
       			userMgr.removeUser(t.getTask().getUser());           		
           		accountService.updateAccountLastUpdate(acc);
           	}
           	else
           	{
           		if (acc instanceof UserAccount)
           		{
           			String userId = ((UserAccount)acc).getUser();
           			User user;
           			try
    				{
    					user = server.getUserInfo(userId, null);
    				}
    				catch (UnknownUserException e)
    				{
    					throw new InternalErrorException("Error getting user "+userId);
    				}
           			
    	        	if ( acc.getOldName() != null && supportsRename)
    	        	{
    	        		userMgr.updateUser(acc, user);	    	        		
    	        	}
    	        	else if (acc.getOldName() != null)
    	        	{
    	        		userMgr.removeUser(acc.getOldName());
    	        		userMgr.updateUser(acc, user);	    	        		
    	        	}
    	        	else
           				userMgr.updateUser(acc, user);
           		}
           		else
           		{
    	        	if ( acc.getOldName() != null && supportsRename)
    	        	{
    	        		userMgr.updateUser(acc);	    	        		
    	        	}
    	        	else if (acc.getOldName() != null)
    	        	{
    	            	userMgr.removeUser(acc.getOldName());
    	        		userMgr.updateUser(acc);	    	        		
    	        	}
    	        	else
           				userMgr.updateUser(acc);           			
           		}
           		accountService.updateAccountLastUpdate(acc);
           	}
		}
	}

	static long unmanagedTypesTS = 0;
	static HashSet<String> unmanagedTypes = null;
	private boolean isUnmanagedType(String passwordPolicy) throws InternalErrorException {
		if (System.currentTimeMillis() > unmanagedTypesTS)
		{
			unmanagedTypes = new HashSet<String>();
			for (UserType tu: ServerServiceLocator.instance().getUserDomainService().findAllUserType())
			{
				if (tu.isUnmanaged())
					unmanagedTypes.add(tu.getCode());
			}
			unmanagedTypesTS = System.currentTimeMillis() + 5000; // Requery every five seconds 
		}
		return unmanagedTypes.contains(passwordPolicy);
	}

	/**
	 * @param t
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 * @throws AccountAlreadyExistsException 
	 */
	private void updateAccountPassword (Object agent, TaskHandler t) throws InternalErrorException, RemoteException
	{
        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr == null)
        	return;
        
       	Account acc = accountService.findAccount(t.getTask().getUser(), mirroredAgent);
       	if (acc == null || acc.isDisabled())
       		return;
       	if (acc.getType().equals (AccountType.IGNORED) ||
       		isUnmanagedType(acc.getPasswordPolicy())) 
       	{
       		if ( getSystem().getName().equals(ConfigurationCache.getProperty("AutoSSOSystem"))) {
	            Password p;
	            p = getTaskPassword(t);
        		secretStoreService.setPasswordAndUpdateAccount(acc.getId(), p,
       				 "S".equals((t.getTask().getPasswordChange())),
       				 t.getTask().getExpirationDate() == null ? null: t.getTask().getExpirationDate().getTime());
       		}
       	}
       	else
       	{
	        if ("S".equals(t.getTask().getPasswordChange()) && !getSystem().getTrusted().booleanValue()) {
	            Password p = server.generateFakePassword(acc.getName(), mirroredAgent);
            	userMgr.updateUserPassword(acc.getName(), null, p, true);
        		auditAccountPasswordChange(acc, null, true);
	    		accountService.updateAccountPasswordDate(acc, new Long(0));
	        } else {
	            Password p;
	            p = getTaskPassword(t);
            	userMgr.updateUserPassword(acc.getName(), null, p, "S".equals(t.getTask().getPasswordChange()));
	
        		auditAccountPasswordChange(acc, null, false);

        		secretStoreService.setPasswordAndUpdateAccount(acc.getId(), p,
        				 "S".equals((t.getTask().getPasswordChange())),
        				 t.getTask().getExpirationDate() == null ? null: t.getTask().getExpirationDate().getTime());

        		generateUpdateServicePassword(acc, t);
	            
	            for (String user: accountService.getAccountUsers(acc))
	            {
	            	changePasswordNotificationQueue.addNotification(user);
	            }
	        }
    	}
	}

	private void generateUpdateServicePassword(Account acc, TaskHandler t) throws InternalErrorException {
		if ( ! accountService.findAccountServices(acc).isEmpty()) {
            TaskEntity te = tasqueEntityDao.newTaskEntity();
            te.setTransaction(TaskHandler.UPDATE_SERVICE_PASSWORD);
            te.setPasswordsDomain(getSystem().getPasswordsDomain());
            te.setPassword(t.getPassword().toString());
            te.setUser(t.getTask().getUser());
            te.setPassword(t.getTask().getPassword());
            te.setDb(t.getTask().getDatabase());
            te.setTenant( tenantDao.load( t.getTenantId() ));
            taskqueue.addTask(te);
		}
	}

	private Long getPasswordTerm (PasswordPolicy politica)
	{
		Long l = null;
		
		if (politica != null && politica.getMaximumPeriod() != null && politica.getType().equals("M"))
		    l = politica.getMaximumPeriod();
		else if (politica != null && politica.getRenewalTime() != null && politica.getType().equals("A"))
			l = politica.getRenewalTime();
		else
			l = new Long(3650);
		return l;
	}

	private void updateAccessControl(Object agent) throws RemoteException, InternalErrorException {
        AccessControlMgr accessControlMgr = InterfaceWrapper.getAccessControlMgr (agent);
        if (accessControlMgr != null)
        	accessControlMgr.updateAccessControl();
    }

    private void updateMailList(Object agent, TaskHandler t) throws InternalErrorException {
        MailAliasMgr aliasMgr = InterfaceWrapper.getMailAliasMgr ( agent );
        try {
            aliasMgr = (MailAliasMgr) aliasMgr;
        } catch (ClassCastException e) {
            return;
        }
    	if (t.getTask().getAlias() != null && t.getTask().getMailDomain() != null)
    	{
    		try {
	            MailList llista = server.getMailList(t.getTask().getAlias(), t.getTask()
	                    .getMailDomain());
	            aliasMgr.updateListAlias(llista);
	        } catch (UnknownMailListException e) {
	            aliasMgr.removeListAlias(t.getTask().getAlias()+"@"+t.getTask().getMailDomain());
        	}
        }
    }

    private void updateUserAlias(Object agent, TaskHandler t) throws InternalErrorException {
        MailAliasMgr aliasMgr = InterfaceWrapper.getMailAliasMgr(agent);
        if (aliasMgr == null)
        	return;

        try {
            User usuari = getUserInfo(t);
            if (usuari.getShortName() == null || usuari.getMailDomain() == null)
                aliasMgr.removeUserAlias(usuari.getUserName());
            else
                aliasMgr.updateUserAlias(usuari.getUserName(), usuari);
        } catch (UnknownUserException e) {
            aliasMgr.removeUserAlias(t.getTask().getUser());
        }
    }

    private void updateNetworks(Object agent) throws RemoteException, InternalErrorException {
        NetworkMgr networkMgr = InterfaceWrapper.getNetworkMgr (agent);
        if (networkMgr != null)
        	networkMgr.updateNetworks();
    }

    private void updateHost(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        HostMgr hostMgr = InterfaceWrapper.getHostMgr (agent);
        if (hostMgr == null)
        	return;

        try {
            Host maq = server.getHostInfo(t.getTask().getHost());
            hostMgr.updateHost(maq);
        } catch (UnknownHostException e) {
            hostMgr.removeHost(t.getTask().getHost());
        }
    }

    private void updateRole(Object agent, TaskHandler t, String trans) throws InternalErrorException,
            RemoteException {
        RoleMgr roleMgr = InterfaceWrapper.getRoleMgr (agent);
        if (roleMgr == null)
        	return;
        String rol = t.getTask().getRole();
        String bd = t.getTask().getDatabase();
        if (bd == null)
        	bd = t.getTask().getSystemName();
        if (bd.equals( mirroredAgent ))
        {
	        try {
	            Role rolInfo = server.getRoleInfo(rol, bd);
	            if(rolInfo == null)
	            	roleMgr.removeRole(rol, bd);
	            else
	            	roleMgr.updateRole(rolInfo);
	        } catch (UnknownRoleException e) {
	            roleMgr.removeRole(rol, bd);
	        }
        }
    }

    private void updateGroup(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        GroupMgr groupMgr = InterfaceWrapper.getGroupMgr (agent);
        if (groupMgr == null)
        	return;
        
    	if (t.getTask().getGroup() != null)
    	{
	        try {
	            Group grup = server.getGroupInfo(t.getTask().getGroup(), mirroredAgent);
	            groupMgr.updateGroup(grup);
	        } catch (UnknownGroupException e) {
	            groupMgr.removeGroup(t.getTask().getGroup());
	        }
    	}
    }

    private void updateObject(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        CustomObjectMgr objectMgr = InterfaceWrapper.getCustomObjectMgr (agent);
        if (objectMgr == null)
        	return;
        
    	if (t.getTask().getCustomObjectName() != null &&
    			t.getTask().getCustomObjectType() != null)
    	{
        	CustomObject ot = server.getCustomObject(t.getTask().getCustomObjectType(), t.getTask().getCustomObjectName());
        	if (ot != null)
        	{
        		objectMgr.updateCustomObject(ot);
	        } else {
	        	ot = new CustomObject();
	        	ot.setName(t.getTask().getCustomObjectName());
	        	ot.setType(t.getTask().getCustomObjectType());
	            objectMgr.removeCustomObject(ot);
	        }
    	}
    }

	public  GetObjectResults  getSoffidObject(String systemName, SoffidObjectType type, String object1,
			String object2) throws Exception
	{
		if (! isConnected())
		{
			throw new InternalErrorException("System "+systemName+" is offline");
		}
		GetObjectResults r = new GetObjectResults();
		Object agent;
		try
		{
			agent = connect(false, true);
		}
		catch (Exception e)
		{
			throw new InternalErrorException ("Unable to connect to "+systemName, e);
		}

		try {
	        ExtensibleObjectMgr objectMgr = InterfaceWrapper.getExtensibleObjectMgr (agent);
	        if (objectMgr == null)
	        {
	        	r.setStatus("error");
	        	r.setLog("Function not implemented");
	        	r.setObject(new HashMap<String, Object>());
	        }
	        else
	        {
		        if (type == SoffidObjectType.OBJECT_ACCOUNT ||
		        		type == SoffidObjectType.OBJECT_ROLE)
		        	object2 = systemName;
		        ExtensibleObject object = objectMgr.getSoffidObject(type, object1, object2);
		        if (object == null)
		        {
		        	r.setObject( new HashMap<String, Object>(  ));
		        	r.setStatus("not found");
		        }
		        else
		        {
		        	r.setStatus("success");
		        	r.setObject( new HashMap<String, Object>( object ));
		        }
		   		r.setLog(( (AgentInterface) agent).getCapturedLog());
	        }
		} catch (Exception e) {
			r.setStatus("error");
			r.setObject(new HashMap<String, Object>());
	   		String log = ( (AgentInterface) agent).getCapturedLog();
	   		if (log == null) log = "";
	   		String stackTrace = SoffidStackTrace.getStackTrace(e);
	   		r.setLog(log + "\n" + stackTrace);
		} finally {
			closeAgent(agent);
		}
        return r;
    }

	public  GetObjectResults  getNativeObject(String systemName, SoffidObjectType type, String object1,
			String object2) throws Exception
	{
		if (! isConnected())
		{
			throw new InternalErrorException("System "+systemName+" is offline");
		}
		GetObjectResults r = new GetObjectResults();
		Object agent;
		try
		{
			agent = connect(false, true);
		}
		catch (Exception e)
		{
			throw new InternalErrorException ("Unable to connect to "+systemName, e);
		}

		try {
	        ExtensibleObjectMgr objectMgr = InterfaceWrapper.getExtensibleObjectMgr (agent);
	        if (objectMgr == null)
	        {
	        	r.setStatus("error");
	        	r.setLog("Function not implemented");
	        	r.setObject(new HashMap<String, Object>());
	        }
	        else
	        {
		        if (type == SoffidObjectType.OBJECT_ACCOUNT ||
		        		type == SoffidObjectType.OBJECT_ROLE)
		        	object2 = systemName;
		        ExtensibleObject object = objectMgr.getNativeObject(type, object1, object2);
		        if (object == null)
		        {
		        	r.setObject( new HashMap<String, Object>(  ));
		        	r.setStatus("not found");
		        }
		        else
		        {
		        	r.setStatus("success");
		        	r.setObject( new HashMap<String, Object>( object ));
		        }
		   		r.setLog(( (AgentInterface) agent).getCapturedLog());
	        }
		} catch (Exception e) {
			r.setStatus("error");
			r.setObject(new HashMap<String, Object>());
	   		String log = ( (AgentInterface) agent).getCapturedLog();
	   		if (log == null) log = "";
	   		String stackTrace = SoffidStackTrace.getStackTrace(e);
	   		r.setLog(log + "\n" + stackTrace);
		} finally {
			closeAgent(agent);
		}
        return r;
    }

	public Collection<Map<String, Object>> invoke(String verb, String command,
			Map<String, Object> params) throws Exception 
	{
		if (! isConnected())
		{
			throw new InternalErrorException("System "+getName()+" is offline");
		}
		Collection<Map<String, Object>> r = null;
		Object agent;
		try
		{
			agent = connect(false, false);
		}
		catch (Exception e)
		{
			throw new InternalErrorException ("Unable to connect to "+getName(), e);
		}

		try {
	        ExtensibleObjectMgr objectMgr = InterfaceWrapper.getExtensibleObjectMgr (agent);
	        r = objectMgr.invoke (verb, command, params);
		} finally {
			closeAgent(agent);
		}
        return r;
    }

	private void createFolder(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        SharedFolderMgr sharedFolderMgr = InterfaceWrapper.getSharedFolderMgr ( agent );
        if (sharedFolderMgr == null)
        	return ;
        // Comprobar que el destino es el adecuado
        if (t.getTask().getFolderType().equals("U")) {
        	try 
        	{
        		User ui = server.getUserInfo(t.getTask().getFolder(), getSystem().getName());
        		sharedFolderMgr.createUserFolder(ui);
            } catch (UnknownUserException e) {
                return;
            }
        } else {
        	try
        	{
        		Group gi = server.getGroupInfo(t.getTask().getFolder(), getSystem().getName());
        		sharedFolderMgr.createGroupFolder(gi);
            } catch (UnknownGroupException e) {
                return;
            }
        } // end if
    }

    private void validatePassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        if (!isTrusted() || t.isValidated() || t.isExpired() || t.isComplete())
            return;

        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr == null)
        	return;

        if (getSystem().getPasswordsDomain().equals(t.getTask().getPasswordDomain()))
        {
        	for (Account acc: getAccounts(t))
        	{
        		if (!acc.isDisabled())
        		{
					if (userMgr != null && userMgr.validateUserPassword(acc.getName(), t.getPassword()) ) {
					    t.setValidated(true);
					    synchronized (t) {
					        t.notify();
					    }
					    cancelTask(t);
					    break;
					}
        		}
        	}
        }
    }

    private void propagatePassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        if (!isTrusted())
            return;

        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr == null)
        	return;

        Account acc = accountService.findAccount(t.getTask().getUser(), mirroredAgent);
        if (acc != null && !acc.isDisabled() )
        {
        	if ( isPasswordTraceEnabled())
        		log.info("Checking password {} for {}", t.getPassword().getPassword(), acc.getName());
        			
        	if ( userMgr.validateUserPassword(acc.getName(), t.getPassword())) 
        	{
	            Syslogger.send(getName() + ":PropagatePassword", "user: " + acc.getName() + " password:"
	                    + t.getPassword().getHash() + ": ACCEPTED");
	            log.info("Accepted proposed password for {}", acc.getName(), null);
	            cancelTask(t);
	        	if (acc instanceof UserAccount)
	        	{
	        		UserAccount ua = (UserAccount) acc;
		            
		            TaskEntity te = tasqueEntityDao.newTaskEntity();
		            te.setTransaction(TaskHandler.UPDATE_PROPAGATED_PASSWORD);
		            te.setPasswordsDomain(getSystem().getPasswordsDomain());
		            te.setPassword(t.getPassword().toString());
		            te.setUser(ua.getUser());
		            te.setTenant( tenantDao.findByName( getSystem().getTenant() ));
		            taskqueue.addTask(te);
		            
		            AuditEntity auditoria = auditoriaDao.newAuditEntity();
		            auditoria.setAction("L");
		            auditoria.setDate(new Date());
		            auditoria.setUser(ua.getUser());
		            auditoria.setPasswordDomain(getSystem().getPasswordsDomain());
		            auditoria.setObject("SC_USUARI");
		            auditoria.setDb(getSystem().getName());
		            auditoria.setAccount(acc.getName());
		            auditoriaDao.create(auditoria);

					internalPasswordService.storePassword(ua.getUser(), getSystem().getPasswordsDomain(), 
									t.getPassword().getPassword(), false);
					secretStoreService.putSecret(
									getTaskUser(t),
									"dompass/" + getPasswordDomain().getId(), 
									t.getPassword());
	        	}
	        	else
	        	{
   		            for (String u: accountService.getAccountUsers(acc))
   		            {
   		            	changePasswordNotificationQueue.addNotification(u);
   		            }
		            AuditEntity auditoria = auditoriaDao.newAuditEntity();
		            auditoria.setAction("L");
		            auditoria.setDate(new Date());
		            auditoria.setAccount(acc.getName());
		            auditoria.setDb(acc.getSystem());
		            auditoria.setObject("SC_ACCOUN");
		            auditoriaDao.create(auditoria);
	        	}
        		secretStoreService.setPasswordAndUpdateAccount(acc.getId(), t.getPassword(), false, null);
	        } else {
	        	String timeout = ConfigurationCache.getProperty("soffid.propagate.timeout");
	        	if (timeout == null) timeout = "5";
	        	if (timeout != null)
	        	{
        			long timeoutLong;
					try {
						timeoutLong = Long.decode(timeout)*1000;
						TaskHandlerLog tasklog = t.getLog(getInternalId());
						if (tasklog == null || tasklog.first == 0 ||
								System.currentTimeMillis() < tasklog.first + timeoutLong)
						{
							log.info("Rejected proposed password for {}. Retrying", t.getTask().getUser(), null);
							throw new InternalErrorException("Rejected proposed password for "+t.getTask().getUser()+". Retry");
						}
					} catch (NumberFormatException e) {
					}
	        	}
	        	
	       		log.info("Rejected proposed password for {}", t.getTask().getUser(), null);
	        }
	    } else {
       		log.debug("Ignoring proposed password for {}", t.getTask().getUser(), null);
	    }
    }

    private void propagateUserPassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        if (!isTrusted())
            return;

        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr == null)
        	return;

    	for (Account acc: accountService.findUsersAccounts(t.getTask().getUser(), mirroredAgent))
    	{
	        if (!acc.isDisabled() )
	        {
	        	if ( isPasswordTraceEnabled())
	        		log.info("Checking password {} for {}", t.getPassword().getPassword(), acc.getName());
	        	if ( userMgr.validateUserPassword(acc.getName(), t.getPassword())) 
	        	{
		            Syslogger.send(getName() + ":PropagatePassword", "user: " + acc.getName() + " password:"
		                    + t.getPassword().getHash() + ": ACCEPTED");
		            log.info("Accepted proposed password for {}", acc.getName(), null);
		            cancelTask(t);
		        	if (acc instanceof UserAccount)
		        	{
		        		UserAccount ua = (UserAccount) acc;
			            
			            TaskEntity te = tasqueEntityDao.newTaskEntity();
			            te.setTransaction(TaskHandler.UPDATE_PROPAGATED_PASSWORD);
			            te.setPasswordsDomain(getSystem().getPasswordsDomain());
			            te.setPassword(t.getPassword().toString());
			            te.setUser(ua.getUser());
			            te.setTenant( tenantDao.findByName( getSystem().getTenant() ));
			            taskqueue.addTask(te);
			            
			            AuditEntity auditoria = auditoriaDao.newAuditEntity();
			            auditoria.setAction("L");
			            auditoria.setDate(new Date());
			            auditoria.setUser(ua.getUser());
			            auditoria.setPasswordDomain(getSystem().getPasswordsDomain());
			            auditoria.setObject("SC_USUARI");
			            auditoria.setDb(getSystem().getName());
			            auditoria.setAccount(acc.getName());
			            auditoriaDao.create(auditoria);
	
						internalPasswordService.storePassword(ua.getUser(), getSystem().getPasswordsDomain(), 
										t.getPassword().getPassword(), false);
						secretStoreService.putSecret(
										getTaskUser(t),
										"dompass/" + getPasswordDomain().getId(), 
										t.getPassword());
		        	}
		        	else
		        	{
	   		            for (String u: accountService.getAccountUsers(acc))
	   		            {
	   		            	changePasswordNotificationQueue.addNotification(u);
	   		            }
			            AuditEntity auditoria = auditoriaDao.newAuditEntity();
			            auditoria.setAction("L");
			            auditoria.setDate(new Date());
			            auditoria.setAccount(acc.getName());
			            auditoria.setDb(acc.getSystem());
			            auditoria.setObject("SC_ACCOUN");
			            auditoriaDao.create(auditoria);
		        	}
	        		secretStoreService.setPasswordAndUpdateAccount(acc.getId(), t.getPassword(), false, null);
		        } else {
		        	String timeout = ConfigurationCache.getProperty("soffid.propagate.timeout");
		        	if (timeout == null) timeout = "5";
		        	if (timeout != null)
		        	{
		        		long timeoutLong;
						try {
							timeoutLong = Long.decode(timeout)*1000;
							TaskHandlerLog tasklog = t.getLog(getInternalId());
							if (tasklog == null || tasklog.first == 0 ||
									System.currentTimeMillis() < tasklog.first + timeoutLong)
							{
								log.info("Rejected proposed password for {}. Retrying", t.getTask().getUser(), null);
								throw new InternalErrorException("Rejected proposed password for "+t.getTask().getUser()+". Retry");
							}
						} catch (NumberFormatException e) {
						}
		        	}
		        	
		       		log.info("Rejected proposed password for {}", t.getTask().getUser(), null);
		        }
	        }
	    }
    }

    private void expireUserPassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr == null)
        	return;
        User user;
        try {
            user = getUserInfo(t);
        } catch (UnknownUserException e) {
            return;
        }
    	for (Account acc: getAccounts(t))
    	{
	        Password p = generateRandomUserPassword(t);
	        userMgr.updateUserPassword(acc.getName(), user, p, false);
	        Syslogger.send(getName() + ":ExpireUserPassword:" + t.getTask().getId(), "user: " + acc.getName()
	                + " password:" + p.getHash());
           	accountService.updateAccountLastUpdate(acc);
    		auditAccountPasswordChange(acc, user, true);
    	}
    }

    private void updatePropagatedPassword(Object agent, TaskHandler t) throws InternalErrorException,
            RemoteException {
        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr == null)
        	return;
        User user;
        try {
            user = getUserInfo(t);
        } catch (UnknownUserException e) {
            return;
        }
        if (isUnmanagedType(user.getUserType()))
        	return;
        boolean anyChange = false;
    	for (Account acc: getAccounts(t))
    	{
    		if (!acc.isDisabled())
    		{
		        boolean ok = userMgr.validateUserPassword(acc.getName(), t.getPassword());
	            Password p = getTaskPassword(t);
		        if (!ok) 
		        {
		        	if ( isPasswordTraceEnabled())
		        		log.info("Checking password {} for {}", t.getPassword().getPassword(), acc.getName());

		        	log.debug("Updating propagated password for account {}/{}",
		            				t.getTask().getUser(), getSystem().getName());
		            userMgr.updateUserPassword(acc.getName(), user, p, false);
            		auditAccountPasswordChange(acc, user, false);
   		            secretStoreService.setPasswordAndUpdateAccount(acc.getId(), p,
   		            	false, null);
   		            anyChange = true;
		        } else {
		        	Password old = secretStoreService.getPassword(acc.getId());
		        	if (old != null && old.getPassword().equals(p))
		        	{
		        		log.debug("Ignoring already updated password for {}/{}",
		        						t.getTask().getUser(), getSystem().getName());
		        	}
		        	else
		        	{
		        		log.debug("Storing already updated password for {}/{}",
		        						t.getTask().getUser(), getSystem().getName());
	   		            secretStoreService.setPasswordAndUpdateAccount(acc.getId(), p,
	   		            		false, null);
	   		            anyChange = true;
		        	}
		        }
    		}
    	}
    	if (anyChange)
    	{
           	changePasswordNotificationQueue.addNotification(t.getTask().getUser());
    	}
    }

	public boolean isPasswordTraceEnabled() {
		return "true".equals(ConfigurationCache.getProperty("soffid.server.trace-passwords"));
	}
    
    private void auditAccountPasswordChange (Account account, User user, boolean random)
    {
        AuditEntity auditoria = auditoriaDao.newAuditEntity();
        auditoria.setAction(random ? "Z" : "X");
        auditoria.setDate(new Date());
        auditoria.setAccount(account.getName());
        auditoria.setDb(account.getSystem());
        auditoria.setObject("SC_ACCOUN");
        if (user != null) auditoria.setUser(user.getUserName());
        auditoria.setDb(getSystem().getName());
        auditoriaDao.create(auditoria);
    }

    private void updateUserPassword(Object agent2, TaskHandler t) throws InternalErrorException, RemoteException {
        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent2);
        if (userMgr == null)
        	return;

        User user;
        try {
            user = getUserInfo(t);
        } catch (UnknownUserException e) {
            return;
        }

        if (isUnmanagedType(user.getUserType()))
        	return;
        
    	for (Account acc: getAccounts(t))
    	{
    		if (!acc.isDisabled())
    		{
		        if ("S".equals(t.getTask().getPasswordChange()) && !getSystem().getTrusted().booleanValue()) {
		            Password p;
		            p = generateRandomUserPassword(t);
	            	userMgr.updateUserPassword(acc.getName(), user, p, true);
            		accountService.updateAccountPasswordDate(acc, new Long(0));
            		auditAccountPasswordChange(acc, user, true);
		        } else {
		            Password p;
		            p = getTaskPassword(t);
	            	userMgr.updateUserPassword(acc.getName(), user, p, "S".equals(t.getTask().getPasswordChange()));
		
            		auditAccountPasswordChange(acc, user, false);

            		if (! "S".equals(t.getTask().getPasswordChange()))
		            {
		            	PasswordPolicy politica = domainService.findPolicyByTypeAndPasswordDomain(
		    	            			acc.getPasswordPolicy(), getSystem().getPasswordsDomain());
	   	            	accountService.updateAccountPasswordDate(acc, getPasswordTerm(politica));
		            } else {
   	            		accountService.updateAccountPasswordDate(acc, new Long(0));
		            }
		            secretStoreService.setPassword(acc.getId(), p);
		            Syslogger.send(
		                    String.format("%s:UpdateUserPassword[%d]", getSystem().getName()
		                            , t.getTask().getId()),
		                    String.format("user:%s password:%s", acc.getName(), p.getHash()));
		        }
    		}
    	}
       	changePasswordNotificationQueue.addNotification(t.getTask().getUser());
    }

    private void updateUser(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        UserMgr userMgr = InterfaceWrapper.getUserMgr(agent);
        if (userMgr != null)
        {
            User user = null;
            try {
    	        user = getUserInfo(t);
    	        if (! isUnmanagedType ( user.getUserType()))
    	        {
	        		for (Account account: getAccounts(t))
	    	        {
	    	        	if (account.isDisabled())
	    	        	{
	    	            	userMgr.removeUser(account.getName());
	    	        	}
	    	        	else if ( account.getOldName() != null && supportsRename)
	    	        	{
	    	        		userMgr.updateUser(account, user);	    	        		
	    	        	}
	    	        	else if (account.getOldName() != null)
	    	        	{
	    	            	userMgr.removeUser(account.getOldName());
	    	        		userMgr.updateUser(account, user);	    	        		
	    	        	}
	    	        	else
	    	        	{
	    	        		userMgr.updateUser(account, user);
	    	        	}
	    				accountService.updateAccountLastUpdate(account);
	    	        }
    	        }
            } 
            catch (UnknownUserException e) 
            {
            	
            }
        }
         
    }


    private void cancelTask(TaskHandler t) throws InternalErrorException {
    	if (debugEnabled)
    		log.info("Cancelling task "+t.toString());
    	t.cancel();
        taskqueue.cancelTask(t.getTask().getId());
    }

    private String getName() {
        return getSystem().getName();
    }

    private Date lastLog = null;
	private boolean supportsRename;
	private String status;
	private Object kerberosAgent;

    /**
     * Recuperar los registros de acceso del agente remoto
     * @param agent 
     * 
     * @throws InternalErrorException
     *             error de lógica interna
     * @throws SQLException
     *             error al almacenar los logs
     * @throws RemoteException
     *             error de comunicaciones
     */
    public void getLog(Object agent) throws InternalErrorException, RemoteException {
        AccessLogMgr logmgr = InterfaceWrapper.getAccessLogMgr(agent);
        if (logmgr == null)
        	return;
        Date date;
        LogCollectorService lcs = ServerServiceLocator.instance().getLogCollectorService();

        // Para hacer seguimiento de total de insertados
        int totalInsertados = 0;

        log.debug("Getting logs", null, null);
        if (lastLog != null) {
            date = lastLog;
        } else {
            date = lcs.getLastLogEntryDate(getName());
        }

        Collection<? extends LogEntry> le = logmgr.getLogFromDate(date);

        log.debug("Got log ", null, null);
        
        if (le != null) {
            for (LogEntry logEntry: le) {
                try {
                    if (logEntry.type == LogEntry.LOGOFF) {
                        lcs.registerLogoff(getName(), logEntry.SessionId, logEntry.date,
                                logEntry.user, logEntry.host, logEntry.client, logEntry.protocol,
                                logEntry.info);
                    } else if (logEntry.type == LogEntry.LOGON) {
                        lcs.registerLogon(getName(), logEntry.SessionId, logEntry.date,
                                logEntry.user, logEntry.host, logEntry.client, logEntry.protocol,
                                logEntry.info);
                    } else if (logEntry.type == LogEntry.LOGON_DENIED) {
                        lcs.registerFailedLogon(getName(), logEntry.SessionId, logEntry.date,
                                logEntry.user, logEntry.host, logEntry.client, logEntry.protocol,
                                logEntry.info);
                    } else {
                        throw new InternalErrorException(String.format("Invalid log of type %d",
                                logEntry.type));
                    }
                } catch (UnknownHostException e) {
                    log.warn("Unknown host {} loading logs: {}", logEntry.host, e.getMessage());
                } catch (UnknownUserException e) {
                    log.warn("Unknown user {} loading logs: {}", logEntry.user, e.getMessage());
                }
                if (lastLog == null)
                	lastLog = logEntry.date;
                else if (logEntry.date != null && logEntry.date.after(lastLog))
                	lastLog = logEntry.date;
            } // end-try
        } // end-if (le != null)
        if (totalInsertados > 0)
        	log.info("Loaded log. Inserted {} rows till {}", totalInsertados, lastLog);
    } // end method

    private void setStatus(String string) {
        status = string;
    }

    @Override
    public void sanityCheck() {
    }

    @Override
    public boolean isConnected() {
        return !reconfigure && lastAgent != null;
    }

    /**
     * Inicializar las conexiones RMI
     * @param b 
     * 
     * @throws InternalErrorException
     *             error interno asociado a la lógica del agente. Posiblemente
     *             el agente no confia en la clave privada del servidor
     * @throws InterruptedException
     *             proceso interrumpido por time-out
     * @throws NotBoundException
     *             eel servidor RMI contactado no es un agente seycon
     * @throws NoSuchAlgorithmException
     *             no se dispone del algoritmo DSA necesario
     * @throws InvalidKeyException
     *             la clave privada del servidor no es correcta
     * @throws SignatureException
     *             error al firmar
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws Exception 
     */
    public Object connect(boolean mainAgent, boolean debug) throws Exception {
        URLManager um = new URLManager(getSystem().getUrl());
    	try {
    		Object o = connect (mainAgent, debug, getSystem().getUrl());
    		if (mainAgent && !debug && o != null && getSystem().getUrl2() != null && ! getSystem().getUrl2().trim().isEmpty())
    		{
    			if (o instanceof AgentInterface && ((AgentInterface)o).isSingleton() ||
    					o instanceof es.caib.seycon.ng.sync.agent.AgentInterface && ((es.caib.seycon.ng.sync.agent.AgentInterface)o).isSingleton())
    				connect (mainAgent, debug, getSystem().getUrl2());
    		}
    		return o;
    	} catch (Exception e) {
    		if (getSystem().getUrl2() != null && !getSystem().getUrl2().isEmpty())
    		{
    	   		return connect (mainAgent, debug, getSystem().getUrl2());
    		}
    		else
    			throw e;
    	}
    }
    
    private Object connect(boolean mainAgent, boolean debug, String url) throws ClassNotFoundException, InstantiationException,
    	IllegalAccessException, InvocationTargetException, InternalErrorException, IOException {

        URLManager um = new URLManager(url);
        Object agent;
		try {
            startTask(true);
        	try {
            	targetHost = um.getServerURL().getHost();
        	} catch (Exception e) {
            	targetHost = "";
        	}
        	// Eliminar el dominio
        	if (targetHost.indexOf(".") > 0)
            	targetHost = targetHost.substring(0, targetHost.indexOf("."));
    
        	String phase = "connecting to";
        	if ("local".equals(getSystem().getUrl())) {
            	try {
                	AgentManager am = ServerServiceLocator.instance().getAgentManager();
               		phase = "configuring";
                	agent = debug? am.createLocalAgentDebug(getSystem()) : am.createLocalAgent(getSystem());
            	} catch (Exception e) {
                	throw new InternalErrorException(String.format("Error %s %s", phase,
                        	getSystem().getUrl()), e);
            	}
            	log.info("Instantiated in memory", null, null);
        	} else {
            	try {
                	RemoteServiceLocator rsl = new RemoteServiceLocator();
                	rsl.setServer(getSystem().getUrl().toString());
                	AgentManager am = rsl.getAgentManager();
                	phase = "configuring";
                	String agenturl = debug ? am.createAgentDebug(getSystem()) : am.createAgent(getSystem());
                	agent = rsl.getRemoteService(agenturl);
            	} catch (Exception e) {
                	throw new InternalErrorException(String.format("Error %s agent %s: %s", phase,
                        	getSystem().getUrl(), e.toString()), e);
            	}
            	log.info("Connected", null, null);
        	}
        	if (agent instanceof ExtensibleObjectMgr)
        	{
        		((ExtensibleObjectMgr) agent).configureMappings(attributeTranslatorV2.getObjects());
        	}
        	if (agent instanceof es.caib.seycon.ng.sync.intf.ExtensibleObjectMgr)
        	{
        		((es.caib.seycon.ng.sync.intf.ExtensibleObjectMgr) agent).
        			configureMappings(attributeTranslator.getObjects());
        	}
        	if (mainAgent)
        	{
            	if (agent instanceof AgentInterface)
            	{
            		agentVersion = ((AgentInterface)agent).getAgentVersion();
            		supportsRename = ((AgentInterface) agent).supportsRename();
            	}
            	else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
            	{
            		agentVersion = ((es.caib.seycon.ng.sync.agent.AgentInterface)agent).getAgentVersion();
            		supportsRename = ((es.caib.seycon.ng.sync.agent.AgentInterface) agent).supportsRename();
            	}
            	else
            	{
            		agentVersion = "Unknown";
            		supportsRename = false;
            	}
            	KerberosAgent krb = InterfaceWrapper.getKerberosAgent (agent);
            	if (krb != null) {
            		this.kerberosAgent = agent;
                	String domain = krb.getRealmName();
                	KerberosManager m = new KerberosManager();
                	try {
                    	log.info("Using server principal {} for realm {}", m.getServerPrincipal(getSystem()),
                            	domain);
                	} catch (Exception e) {
                    	log.warn("Unable to create Kerberos principal", e);
                	}
            	}
	            mirroredAgent = null;
	            if (agent instanceof AgentMirror)
	            	mirroredAgent = ((AgentMirror) agent).getAgentToMirror();
	            if (mirroredAgent == null)
	            	mirroredAgent = getSystem().getName();
        	}
        	return agent;

        } finally {
        	endTask();
         }
    }

    /**
     * Method to get a task user info
     * 
     * @param task
     * @return
     * @throws UnknownUserException
     * @throws InternalErrorException
     */
    private User getTaskUser(TaskHandler task) throws InternalErrorException {
        User user = task.getUser();
        if (user != null)
            return user;
        if (task.getTask().getUser() == null)
        	return null;
        Long id = task.getTask().getId();
        if (id == null)
        	id = new Long(0);
        synchronized (id) {
	        try {
	        	Collection<RoleGrant> grants = null;
				if (task.getTask().getTransaction().equals (TaskHandler.PROPAGATE_ACCOUNT_PASSWORD) ||
	        			task.getTask().getTransaction().equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD) ||
	        			task.getTask().getTransaction().equals(TaskHandler.VALIDATE_ACCOUNT_PASSWORD) ||
	        			task.getTask().getTransaction().equals(TaskHandler.UPDATE_ACCOUNT))
	        	{
		            user = server.getUserInfo(task.getTask().getUser(), mirroredAgent);
		            grants  = server.getUserRoles(user.getId(), null);
	        	}
	        	else
	        	{
		            user = server.getUserInfo(task.getTask().getUser(), null);
		            grants = server.getUserRoles(user.getId(), null);
	        	}
	            task.setUser(user);
	            task.setGrants(grants);
	        } catch (UnknownUserException e) {
	            user = null;
	        }
	        return user;
        }
    }

    private User getUserInfo(TaskHandler t) throws InternalErrorException, UnknownUserException {
        return server.getUserInfo(t.getTask().getUser(), null);
    }

    private Collection<UserAccount> getAccounts(TaskHandler task) throws InternalErrorException {
        User usuari = getTaskUser(task);
        if (usuari == null)
            return Collections.EMPTY_LIST;
        return server.getUserAccounts(usuari.getId(), mirroredAgent);
    }

    @Override
    public KerberosAgent getKerberosAgent() {
    	if (kerberosAgent == null)
    		return null;
    	else
    		return InterfaceWrapper.getKerberosAgent (kerberosAgent);
    }

    public Date getCertificateNotValidAfter() {
        if (isConnected())
            try {
                AgentManager agentMgr;
                if (getSystem().equals("local")) {
                    agentMgr = ServerServiceLocator.instance().getAgentManager();
                    return null;
                } else {
                    RemoteServiceLocator locator = new RemoteServiceLocator(getSystem()
                            .getUrl());
                    agentMgr = locator.getAgentManager();
                }
                return agentMgr.getCertificateValidityDate();
            } catch (Throwable th) {
                // th.printStackTrace();
            }
        return null;
    }

    @Override
    public Object getRemoteAgent() {
        return lastAgent;
    }

	protected static JbpmConfiguration getConfig ()
	{
		return com.soffid.iam.bpm.config.Configuration.getConfig();
	}

	/**
	 * Reconcile users
	 * 
	 * <p>
	 * Implements the functionality to get an account list dispatcher, verify
	 * which ones already exist, and create the reconcile user task for each non
	 * existing user.
	 * @param agent 
	 * 
	 * @param taskHandler
	 *            Task handler.
	 */
	public void reconcileUsers (Object agent, TaskHandler taskHandler)
		throws InternalErrorException, RemoteException
	{
		TaskEntity taskEntity;				// Task entity
		ReconcileMgr reconcileManager = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
		ReconcileMgr2 reconcileManager2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
		
		TenantEntity tenant = tenantDao.findByName(getSystem().getTenant());

		// Create reconcile user task for users
		if (reconcileManager2 == null)
		{
			for (String user : (reconcileManager2 == null ? reconcileManager.getAccountsList() : reconcileManager2.getAccountsList()))
			{
				// Check user code length
				if (user.length() <= MAX_LENGTH)
				{
					taskEntity = tasqueEntityDao.newTaskEntity();
					taskEntity.setTransaction(TaskHandler.RECONCILE_USER);
					taskEntity.setDate(new Timestamp(System.currentTimeMillis()));
					taskEntity.setHost(taskHandler.getTask().getHost());
					taskEntity.setUser(user);
					taskEntity.setSystemName(getSystem().getName());
		            tasqueEntityDao.createForce(taskEntity);

					taskqueue.addTask(taskEntity);
				}
			}
	
			// Create reconcile roles task
			taskEntity = tasqueEntityDao.newTaskEntity();
			taskEntity.setTransaction(TaskHandler.RECONCILE_ROLES);
			taskEntity.setDate(new Timestamp(System.currentTimeMillis()));
			taskEntity.setHost(taskHandler.getTask().getHost());
			taskEntity.setSystemName(getSystem().getName());
	
			taskqueue.addTask(taskEntity);
		}
		else
		{
			if (reconcileThread == null)
			{
				reconcileThread = new ReconcileThread();
				reconcileThread.setName("Reconcile thread for "+getSystem().getName());
				try {
					Object tempAgent = connect(false, false);
					ManualReconcileEngine engine = new ManualReconcileEngine(Security.getCurrentTenantName(), getSystem(), InterfaceWrapper.getReconcileMgr2(tempAgent), null);
					engine.setReconcileProcessId(Long.decode(taskHandler.getTask().getHost()));
					reconcileThread.setEngine(
							engine);
				} catch (Exception e) {
					throw new InternalErrorException("Error connecting agent "+e);
				}
				reconcileThread.start();
				throw new InternalErrorException("Reconcile started", new ReconcileInProgress("Reconcile started"));
			}
			else if (reconcileThread.isAlive())
			{
				throw new InternalErrorException("Reconcile in progress", new ReconcileInProgress("Reconcile in progress"));
			}
			else
			{
				ReconcileThread rt = reconcileThread;
				reconcileThread = null;
				if (rt.isFinished())
				{
					if (rt.getException() != null)
						throw new InternalErrorException("Error during reconcile process", rt.getException());
				}
				else
				{
					throw new InternalErrorException("Reconcile process aborted");
				}
			}
		}
	}
	
	ReconcileThread reconcileThread = null;

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
	public void endReconcile (Object agent, TaskHandler taskHandler)
		throws InternalErrorException, RemoteException
	{
		ReconcileService service = ServerServiceLocator.instance()
						.getReconcileService();

		Long processId = Long.decode(taskHandler.getTask().getHost());
		Long taskId = taskHandler.getTask().getId();
		
		// Check pending tasks
		if (!service.isPendingTasks(processId, taskId))
		{
			JbpmContext ctx = getConfig().createJbpmContext();
			ProcessInstance pi = ctx.getProcessInstance(processId);
			
			if (pi == null)
				throw new InternalErrorException("Wrong request ID");

			pi.signal();
			ctx.close();
		}
		
		else
		{
			throw new InternalErrorException ("Waiting for process completion");
		}
	}
	
	/**
	 * Reconcile user
	 * 
	 * <p>
	 * Implements the functionality to create reconcile information on reconcyle
	 * tables for this user.
	 * @param agent 
	 * 
	 * @param taskHandler
	 *            Task handler.
	 */
	public void reconcileUser (Object agent, TaskHandler taskHandler)
					throws InternalErrorException, RemoteException
	{
		ReconcileMgr reconcileManager = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
		ReconcileMgr2 reconcileManager2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
		if (reconcileManager != null)
			reconcileUser(reconcileManager, taskHandler);
		else if (reconcileManager2 != null)
			reconcileAccount(reconcileManager2, taskHandler);
		else
			return;
	}
	
	public void reconcileUser (ReconcileMgr reconcileManager, TaskHandler taskHandler)
			throws InternalErrorException, RemoteException
	{
		User user; // User to reconcile
		ReconcileAccount reconcileAccount = null; // Reconcile accounts handler
		ReconcileAssignment reconcileAssign = null; // Reconcile assignments handler

		try
		{
		}
		catch (ClassCastException e)
		{
			return;
		}

		String accountName = taskHandler.getTask().getUser();
		// Check existing user on system
		Account account = accountService.findAccount(accountName,
						mirroredAgent);

		Collection<RoleGrant> grants = new LinkedList<RoleGrant>();
		
		if (account == null)
		{

			user = reconcileManager.getUserInfo(taskHandler.getTask().getUser());

			// Check correct user
			if (user != null && user.getUserName() != null)
			{
				// Set user parameters
				reconcileAccount = new ReconcileAccount();
				reconcileAccount.setAccountName(user.getUserName());
				if (user.getFullName() == null)
					reconcileAccount.setDescription(user.getFirstName() + " "
									+ user.getLastName());
				else
					reconcileAccount.setDescription(user.getFullName());
				reconcileAccount.setProcessId(Long.parseLong(taskHandler.getTask()
								.getHost()));
				reconcileAccount.setProposedAction(AccountProposedAction.CREATE_NEW_USER);
				reconcileAccount.setDispatcher(getSystem().getName());
				reconcileService.addUser(reconcileAccount);
			}
		}
		else
			grants = server.getAccountRoles(accountName, mirroredAgent);
		
		for (Role role : reconcileManager.getAccountRoles(taskHandler.getTask().getUser()))
		{
			if (role.getName().length() <= MAX_LENGTH)
			{
				boolean found = false;
				for (RoleGrant rg: grants)
				{
					if (rg.getRoleName().equals (role.getName()) && rg.getSystem().equals (mirroredAgent))
					{
						found = true;
						break;
					}
				}
				
				if (! found)
				{
					reconcileAssign = new ReconcileAssignment();
					reconcileAssign.setAccountName(accountName);
					reconcileAssign.setAssignmentName(accountName + " - "
									+ role.getName());
					reconcileAssign.setProcessId(Long.parseLong(taskHandler
									.getTask().getHost()));
					reconcileAssign.setProposedAction(ProposedAction.LOAD);
					reconcileAssign.setRoleName(role.getName());
					reconcileAssign.setDispatcher(getSystem().getName());
	
					reconcileService.addAssignment(reconcileAssign);
				}
			}
		}
	}

	public void reconcileAccount (ReconcileMgr2 reconcileManager, TaskHandler taskHandler)
			throws InternalErrorException, RemoteException
	{
		Account user; // User to reconcile
		ReconcileAccount reconcileAccount = null; // Reconcile accounts handler
		ReconcileAssignment reconcileAssign = null; // Reconcile assignments handler

		String accountName = taskHandler.getTask().getUser();
		// Check existing user on system
		Account account = accountService.findAccount(accountName,
						mirroredAgent);

		Collection<RoleGrant> grants = new LinkedList<RoleGrant>();
		
		if (account == null)
		{

			user = reconcileManager.getAccountInfo(taskHandler.getTask().getUser());

			// Check correct user
			if (user != null && user.getName() != null)
			{
				// Set user parameters
				reconcileAccount = new ReconcileAccount();
				reconcileAccount.setAccountName(user.getName());
				if (user.getDescription() == null)
					reconcileAccount.setDescription(reconcileAccount.getAccountName()+" account");
				else
					reconcileAccount.setDescription(user.getDescription());
				reconcileAccount.setProcessId(Long.parseLong(taskHandler.getTask()
								.getHost()));
				reconcileAccount.setProposedAction(AccountProposedAction.CREATE_NEW_USER);
				reconcileAccount.setDispatcher(getSystem().getName());
				reconcileAccount.setAttributes(user.getAttributes());
				reconcileAccount.setNewAccount(Boolean.TRUE);
				reconcileAccount.setDeletedAccount(Boolean.FALSE);
				reconcileAccount.setAttributes(new HashMap<String, Object>());
				if (user.getAttributes() != null)
					reconcileAccount.getAttributes().putAll(user.getAttributes());
				reconcileService.addUser(reconcileAccount);
			}
		}
		else
		{
			user = reconcileManager.getAccountInfo(taskHandler.getTask().getUser());
			if (user == null)
				return;
			boolean anyChange = account.isDisabled() != user.isDisabled();
			anyChange = anyChange || (user.getDescription() != null && ! account.getDescription().equals(user.getDescription()));
			if (user.getAttributes() != null)
			{
				for (String att: user.getAttributes().keySet())
				{
					Object value = user.getAttributes().get(att);
					if (value != null && ! value.equals(account.getAttributes().get(att)))
						anyChange = true;
				}
			}
			if (anyChange)
			{
				// Set user parameters
				reconcileAccount = new ReconcileAccount();
				reconcileAccount.setAccountName(user.getName());
				if (user.getDescription() == null)
					reconcileAccount.setDescription(account.getDescription());
				else
					reconcileAccount.setDescription(user.getDescription());
				reconcileAccount.setProcessId(Long.parseLong(taskHandler.getTask()
								.getHost()));
				reconcileAccount.setProposedAction(AccountProposedAction.UPDATE_ACCOUNT);
				reconcileAccount.setDispatcher(getSystem().getName());
				reconcileAccount.setAttributes(user.getAttributes());
				reconcileAccount.setNewAccount(Boolean.FALSE);
				reconcileAccount.setDeletedAccount(Boolean.FALSE);
				reconcileAccount.setAttributes(new HashMap<String, Object>());
				if (user.getAttributes() != null)
					reconcileAccount.getAttributes().putAll(user.getAttributes());
				reconcileService.addUser(reconcileAccount);				
			}
			grants = server.getAccountRoles(accountName, mirroredAgent);
		}
		
		for (RoleGrant role : reconcileManager.getAccountGrants(taskHandler.getTask().getUser()))
		{
			if (role.getRoleName().length() <= MAX_LENGTH)
			{
				boolean found = false;
				for (RoleGrant rg: grants)
				{
					if (rg.getRoleName().equals (role.getRoleName()) && rg.getSystem().equals (mirroredAgent))
					{
						found = true;
						break;
					}
				}
				
				if (! found)
				{
					reconcileAssign = new ReconcileAssignment();
					reconcileAssign.setAccountName(accountName);
					reconcileAssign.setAssignmentName(accountName + " - "
									+ role.getRoleName());
					reconcileAssign.setProcessId(Long.parseLong(taskHandler
									.getTask().getHost()));
					reconcileAssign.setProposedAction(ProposedAction.LOAD);
					reconcileAssign.setRoleName(role.getRoleName());
					reconcileAssign.setDispatcher(getSystem().getName());
					//if (role.getDomainValue() != null)
						//reconcileAssign.setDomainValue(role.getDomainValue());
					reconcileService.addAssignment(reconcileAssign);
				}
			}
		}
	}
	/**
	 * Reconcile roles
	 * 
	 * <p>
	 * Implements the functionality to enumerate the full list of roles and will
	 * create reconcile role task for each role.
	 * @param agent 
	 * 
	 * @param taskHandler
	 *            Task handler.
	 */
	public void reconcileRoles (Object agent, TaskHandler taskHandler)
		throws InternalErrorException, RemoteException
	{
		TaskEntity taskEntity;		// Task entity
		TenantEntity tenant = tenantDao.findByName(getSystem().getTenant());
		
		ReconcileMgr reconcileManager = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
		ReconcileMgr2 reconcileManager2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager

		if (reconcileManager == null && reconcileManager2 == null)
			return;
		
		List<String> roles = reconcileManager == null ? reconcileManager2.getRolesList() : reconcileManager.getRolesList();
		// Create reconcile role task
		for (String role : roles) {
            if (role.length() <= MAX_ROLE_CODE_LENGTH) {
                taskEntity = tasqueEntityDao.newTaskEntity();
                taskEntity.setTransaction(TaskHandler.RECONCILE_ROLE);
                taskEntity.setDate(new Timestamp(System.currentTimeMillis()));
                taskEntity.setHost(taskHandler.getTask().getHost());
                taskEntity.setRole(role);
                taskEntity.setDb(getSystem().getName());
                taskEntity.setSystemName(getSystem().getName());
	            taskEntity.setTenant( tenant );
	            tasqueEntityDao.createForce(taskEntity);
                taskqueue.addTask(taskEntity);
            }
        }

		// Create reconcile roles task
		taskEntity = tasqueEntityDao.newTaskEntity();
		taskEntity.setTransaction(TaskHandler.END_RECONCILE);
		taskEntity.setDate(new Timestamp(System.currentTimeMillis()));
		taskEntity.setHost(taskHandler.getTask().getHost());
		taskEntity.setSystemName(getSystem().getName());
        taskEntity.setTenant( tenant );

		taskqueue.addTask(taskEntity);
	}

	/**
	 * Reconcile role
	 * 
	 * <p>
	 * Implements the functionality to reconcile role information.
	 * @param agent 
	 * 
	 * @remarks If the role does not exist on Soffid, its information will be gathered
	 *          and a new role reconcile will be created on reconcyle tables. Anyway,
	 *          the list of users with the role granted will be retrieved and matched
	 *          against Soffid information. A row for assingment reconcile will be
	 *          created for each mismatched assignment.
	 * @param taskHandler
	 *            Task handler.
	 */
	public void reconcileRole (Object agent, TaskHandler taskHandler)
		throws InternalErrorException, RemoteException
	{
		Role role;						// Role to reconcile
		ReconcileMgr reconMgr = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
		ReconcileMgr2 reconMgr2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
		ReconcileRole reconRole;

		try
		{
			if (taskHandler.getTask().getRole() != null
							&& taskHandler.getTask().getRole().length() > 0)
			{
				role = server.getRoleInfo(taskHandler.getTask().getRole(), taskHandler
								.getTask().getDatabase());

				// Check not existing role
				if (role == null)
				{
					role = reconMgr != null ? reconMgr.getRoleFullInfo(taskHandler.getTask().getRole())
							: reconMgr2 != null? reconMgr2.getRoleFullInfo(taskHandler.getTask().getRole())
							: null;

					if (role != null)
					{
						reconRole = new ReconcileRole();
						reconRole.setRoleName(role.getName());
	
						// Check role description lenght
						if (role.getDescription() == null || role.getDescription().trim().length() == 0)
						{
							reconRole.setDescription(role.getName());
						}
						else if (role.getDescription().length() <= MAX_LENGTH)
							reconRole.setDescription(role.getDescription());
	
						else
							reconRole.setDescription(role.getDescription().substring(0,
											MAX_LENGTH));
	
						reconRole.setProcessId(Long.parseLong(taskHandler.getTask()
										.getHost()));
						reconRole.setProposedAction(ProposedAction.LOAD);
						reconRole.setDispatcher(getSystem().getName());
	
						reconcileService.addRole(reconRole);
					}
				}
			}
		}

		catch (UnknownRoleException e)
		{
			return;
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.engine.DispatcherHandler#processOBTask(es.caib.seycon.ng.sync.engine.TaskHandler)
	 */
	@Override
	public void processOBTask (TaskHandler task) throws InternalErrorException
	{
		try
		{
			if (applies(null, task))
			{
				Object agent = connect(false, false);
				try {
					if (applies(agent, task))
					{
						processTask(agent, task);
					}
				} finally {
					closeAgent(agent);
				}
			}
		}
		catch (InternalErrorException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new InternalErrorException ("Unable to process out of band task", e);
		}
	}

	@Override
	public DebugTaskResults debugTask (TaskHandler task) throws InternalErrorException
	{
		DebugTaskResults r = new DebugTaskResults();
		r.setLog(new String());
		AgentInterface agent = null;
		try
		{
			try {
				agent = (AgentInterface) connect(false, true);
			} catch (Exception e) {
				r.setException(e);
				r.setStatus("Connection error");
				r.setLog(SoffidStackTrace.getStackTrace(e));
				return r;
			} 
			if (applies(agent, task))
			{
				processTask(agent, task);
				r.setStatus("Success");
				r.setLog(agent.endCaptureLog());
			} else {
				r.setStatus("The agent does not accept the task");
			}
		}
		catch (Exception e)
		{
			r.setStatus("Error");
			r.setException(e);
			String log = agent.endCaptureLog();
			if (log == null) log = "";
			String now = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date());
			r.setLog(log + "\n"+now+" WARN Unexpected error:\n"+ SoffidStackTrace.getStackTrace(e));
		} finally {
			closeAgent(agent);
		}
		return r;
	}

	boolean ongoingReconcile = false;
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.engine.DispatcherHandler#doReconcile()
	 */
	@Override
	public void doReconcile (ScheduledTask task, PrintWriter out)
	{
		synchronized (this)
		{
			if (ongoingReconcile)
				throw new RuntimeException("Another reconciliation is in process");
			ongoingReconcile = true;
		}
		try {
    		Object agent = connect(false, false);
    		try {
				ReconcileMgr reconMgr = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
				ReconcileMgr2 reconMgr2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
	    		if (reconMgr != null)
	    		{
	    			new ReconcileEngine1 (getSystem(), reconMgr, out).reconcile();
	    		} 
	    		else if (reconMgr2 != null)
	        	{
	        		new ReconcileEngine2 (getSystem(), reconMgr2, InterfaceWrapper.getServiceMgr(agent), out).reconcile();
	    		} 
	    		else {
	    			out.append ("This agent does not support account reconciliation");
	    		}
    		} finally {
    			closeAgent(agent);
    		}
		} 
		catch (Exception e)
		{
			task.setError(true);
			out.println ("*************");
			out.println ("**  ERROR **");
			out.println ("*************");
			out.println (e.toString());
			try {
				log.warn("Error during reconcile process", e);
				SoffidStackTrace.printStackTrace(e, out);
			} catch (Exception e2) {}
		} finally {
			ongoingReconcile = false;
		}
	}
	
	@Override
	public void doImpact (ScheduledTask task, PrintWriter out)
	{
		synchronized (this)
		{
			if (ongoingReconcile)
				throw new RuntimeException("Another reconciliation is in process");
			ongoingReconcile = true;
		}
		try {
    		Object agent = connect(false, false);
    		try {
				ReconcileMgr reconMgr = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
				ReconcileMgr2 reconMgr2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
	    		if (reconMgr != null)
	    		{
	    			new PreviewChangesEngine1(getSystem(), reconMgr, out).execute();
	    		} 
	    		else if (reconMgr2 != null)
	        	{
	    			new PreviewChangesEngine2(getSystem(), reconMgr2, out).execute();
	    		} 
	    		else 
	    		{
	    			out.append ("This agent does not support account reconciliation");
	    		}
    		} finally {
    			closeAgent(agent);
    		}
		} 
		catch (Exception e)
		{
			task.setError(true);
			out.println ("*************");
			out.println ("**  ERROR **");
			out.println ("*************");
			out.println (e.toString());
			try {
				log.warn("Error during reconcile process", e);
				SoffidStackTrace.printStackTrace(e, out);
			} catch (Exception e2) {}
   		} finally {
   			ongoingReconcile = false;
		}
	}
	
	public DispatcherStatus getDispatcherStatus ()
	{
		return dispatcherStatus;
	}
	
	public boolean runStep () throws InternalErrorException
	{
		boolean done = true;
		switch (dispatcherStatus)
		{
		case STARTING:
			if (debugEnabled) log.info("Starting dispatcher "+getName());
			runInit();
			dispatcherStatus = DispatcherStatus.LOOP_START;
			break;
		case WAIT:
			if (System.currentTimeMillis() < waitUntil)
			{
				done = false;
				break;
			}
			// Go on with loop_start
		case LOOP_START:
			if (debugEnabled) log.info("Connecting agent "+getName());
			runLoopStart();
			dispatcherStatus = DispatcherStatus.NEXT_TASK;
			break;
		case NEXT_TASK:
			if (runLoopNext())
				dispatcherStatus = DispatcherStatus.NEXT_TASK;
			else
				dispatcherStatus = DispatcherStatus.GET_LOGS;
			break;
		case GET_LOGS:
			if (debugEnabled) log.info("Getting logs "+getName());
			runGetLogs();
			dispatcherStatus = DispatcherStatus.WAIT;
			break;
		}
		return done;
	}
	

	private void startTask (boolean longTask) {
		if (longTask)
			Watchdog.instance().interruptMe(getSystem().getLongTimeout());
		else 
			Watchdog.instance().interruptMe(getSystem().getTimeout());
	}

	private void endTask () {
		Watchdog.instance().dontDisturb();
	}

	@Override
	public String parseKerberosToken(String domain, String serviceName, byte[] keytab, byte[] token) throws Exception {
		if (isConnected() && kerberosAgent != null)
		{
			Object agent = connect( false, false );
			KerberosAgent krbAgent = InterfaceWrapper.getKerberosAgent(agent);
			if (krbAgent != null)
			{
				String krbDomain = krbAgent.getRealmName();
				if (domain.equalsIgnoreCase(krbDomain))
					return krbAgent.parseKerberosToken(serviceName, keytab, token);
			}
		}
		return null;
				
	}

	@Override
	public void doReconcile(String account, PrintWriter out, boolean debug) throws Exception {
		
		// Check that no task is affecting this user
		if ( ! taskInProgress (account))
		{
			Object agent = connect(false, false);
			es.caib.seycon.ng.sync.agent.AgentInterface agentV1 = 
					agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface ?
							(es.caib.seycon.ng.sync.agent.AgentInterface) agent:
							null;
			AgentInterface agentV2 = agent instanceof AgentInterface ? (AgentInterface) agent : null;
			if (debug) {
				if (agentV2 != null)
				{
					agentV2.setDebug(true);
					agentV2.startCaptureLog();
				}
				if (agentV1 != null)
				{
					agentV1.setDebug(true);
					agentV1.startCaptureLog();
				}
			}
			try {
				ReconcileMgr reconMgr = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
				ReconcileMgr2 reconMgr2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
				if (reconMgr != null)
				{
					new ReconcileEngine1 (getSystem(), reconMgr, out).reconcileAccount(account);
				} 
				else if (reconMgr2 != null)
				{
					new ReconcileEngine2 (getSystem(), reconMgr2, InterfaceWrapper.getServiceMgr(agent), out).reconcileAccount(account);
				} 
				else {
					out.println ("This agent does not support account reconciliation");
				}
			} finally {
				closeAgent(agent);
			}
			if (debug)
			{
				if (agentV1 != null)
				{
					out.println ( agentV1.endCaptureLog());
				}
				if (agentV2 != null)
				{
					out.println ( agentV2.endCaptureLog());
				}
			}
		}
		else
		{
			out.println("Discarding reconcile process as account "+account+" is not synchronized yet");
		}
	}

	private boolean taskInProgress(String accountName) throws InternalErrorException {
		Account account = ServiceLocator.instance().getAccountService().findAccount(accountName, getName());
		if (account == null)
			return false;
		if (account.getLastUpdated() != null && 
				System.currentTimeMillis() - account.getLastUpdated().getTime().getTime() < 60000 ) // 1 minute
		{
			return true;
		}

		if (ServiceLocator.instance().getAccountService().isUpdatePending(account))
			return true;
		
		return false;
	}

	@Override
	public PasswordValidation checkPasswordSynchronizationStatus(String accountName) throws Exception {
		Collection<Map<String, Object>> s = invoke("checkPassword", accountName, new HashMap<String, Object>());
		for (Map<String, Object> ss: s) {
			PasswordValidation status = (PasswordValidation) ss.get("passwordStatus");
			if (status != null) {
				Account account = accountService.findAccount(accountName, getName());
				if (account != null)
				{
					Object oldStatus = account.getAttributes().get("passwordStatus");
					if ( ! status.toString().equals(oldStatus))
					{
						account.setPasswordStatus(status);
						accountService.updateAccount(account);
					}
				}
				return status;
			}
		}
		return null;
	}

}

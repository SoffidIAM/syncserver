package com.soffid.iam.sync.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Configuration;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.MailList;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordDomain;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.ReconcileTrigger;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.UserType;
import com.soffid.iam.authoritative.service.AuthoritativeChangeService;
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
import com.soffid.iam.service.ConfigurationService;
import com.soffid.iam.service.CustomObjectService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.GroupService;
import com.soffid.iam.service.InternalPasswordService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.agent.AgentInterface;
import com.soffid.iam.sync.agent.AgentManager;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.extobj.CustomExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GroupExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.UserExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ValueObjectMapper;
import com.soffid.iam.sync.engine.kerberos.KerberosManager;
import com.soffid.iam.sync.intf.AccessControlMgr;
import com.soffid.iam.sync.intf.AccessLogMgr;
import com.soffid.iam.sync.intf.AuthoritativeChange;
import com.soffid.iam.sync.intf.CustomObjectMgr;
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
import com.soffid.iam.sync.intf.SharedFolderMgr;
import com.soffid.iam.sync.intf.UserMgr;
import com.soffid.iam.sync.service.ChangePasswordNotificationQueue;
import com.soffid.iam.sync.service.LogCollectorService;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.service.TaskQueue;
import com.soffid.iam.util.Syslogger;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class DispatcherHandlerImpl extends DispatcherHandler implements Runnable {
    private static JbpmConfiguration jbpmConfig;
	Logger log = LoggerFactory.getLogger(DispatcherHandler.class);
    boolean active = true;
    private boolean reconfigure = false;
    private String status;
    private Object agent;
    private Object objectClass;
    private long lastConnect;
    private boolean actionStop;
    private long taskQueueStartTime;
    private long nextConnect;
    private Exception connectException;
    private String agentVersion;
    private TaskQueue taskqueue;
    private TaskGenerator taskgenerator;
    private SecretStoreService secretStoreService;
    private String targetHost;
    private Thread currentThread;
    private ChangePasswordNotificationQueue changePasswordNotificationQueue;
    private TaskEntityDao tasqueEntityDao;
	private ReconcileService reconcileService;

	private AuditEntityDao auditoriaDao;
	private static final int MAX_LENGTH = 150;
	private static final int MAX_ROLE_CODE_LENGTH = 50;
	private PasswordDomain passwordDomain = null;
	private AuthoritativeChangeService authoritativeService;

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
	private UserService userService;
	private GroupService groupService;
	private CustomObjectService objectService;
	private InternalPasswordService internalPasswordService;
	private AccountService accountService;
	private es.caib.seycon.ng.sync.engine.extobj.ObjectTranslator attributeTranslator;
	private com.soffid.iam.sync.engine.extobj.ObjectTranslator attributeTranslatorV2;
	private TenantEntityDao tenantDao;
	private DispatcherService dispatcherService;

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
        userService = ServerServiceLocator.instance().getUserService();
        groupService = ServerServiceLocator.instance().getGroupService();
        auditoriaDao = (AuditEntityDao) ServerServiceLocator.instance().getService("auditEntityDao");
        tenantDao = (TenantEntityDao) ServerServiceLocator.instance().getService("tenantEntityDao");
        reconcileService = ServerServiceLocator.instance().getReconcileService();
        authoritativeService = ServerServiceLocator.instance().getAuthoritativeChangeService();
        dispatcherService = ServerServiceLocator.instance().getDispatcherService();
        objectService = ServiceLocator.instance().getCustomObjectService();
        
        active = true;
    }

    @Override
    public boolean applies(TaskHandler t) {
    	return applies (agent, t);
    }
    	
    public boolean applies(Object agent, TaskHandler t) {
    	    
        String trans = t.getTask().getTransaction();
        boolean trusted = isTrusted();
        boolean readOnly = getSystem().isReadOnly();
        // Verificar el nom del dipatcher
        if (t.getTask().getSystemName() != null
                && !this.getSystem().getName().equals(t.getTask().getSystemName())) {
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
            return !readOnly && (
            				implemented(agent, UserMgr.class) ||
            				implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class));
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
        } else {
            return true;
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
        if (currentThread != null)
        	currentThread.interrupt();
        passwordDomain = null;
    }

    
    @Override
    public void setSystem(com.soffid.iam.api.System dispatcher) throws InternalErrorException {
        log = LoggerFactory.getLogger(dispatcher.getName());
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

    public void start() {
        new Thread(this).start();
    }

    int delay, timeoutDelay;
    boolean abort = false;
	private TaskHandler nextTask;
	private long waitUntil;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> preInsertTrigger;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> postInsertTrigger;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> preUpdateTrigger;
	private Map<SoffidObjectType,LinkedList<ReconcileTrigger>> postUpdateTrigger;


    public void run() {
        try {
        	Security.nestedLogin(getSystem().getTenant(), Config.getConfig().getHostName(), new String [] {
        		Security.AUTO_AUTHORIZATION_ALL
        	});
        	ConnectionPool pool = ConnectionPool.getPool();
        	
            currentThread = Thread.currentThread();
            Thread.currentThread().setName(getSystem().getName());
            // boolean runTimedOutTasks = true;
            log.info("Registered dispatcher thread", null, null);
            runInit();
            // setName (agentName);
            while (active) {
                // Actualiza información del último bucle realizado
                taskQueueStartTime = new java.util.Date().getTime();

                // Forzar la reconexion

                try {
                    // //////////////////////////////////////////////////////////
                    // Ejecutar las transacciones
                    runLoopStart();
                    boolean ok = true;
                    setStatus("Getting Task");
                    while (nextTask != null && !abort && !actionStop && !reconfigure && agent != null) {
                        runLoopNext();
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
        } finally {
            active = false;
            setStatus("Stopped");
            log.info("Stopped", null, null);
            Security.nestedLogoff();
        }

    }

	private void runGetLogs() throws InternalErrorException {
		if (!actionStop && !abort && agent != null && taskgenerator.canGetLog(this)) {
		    setStatus("Retrieving logs");
		    try {
		    	startTask(true);
		        getLog();
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
		waitUntil = System.currentTimeMillis() + delay;
	}

	private void runLoopNext()
			throws InternalErrorException {
    	ConnectionPool pool = ConnectionPool.getPool();
	try {
		startTask(false);
		nextTask = processAndLogTask(nextTask);
	} finally {
		endTask();
	}
	}

	private void runLoopStart() throws InternalErrorException {
		if (reconfigure) {
			if (agent != null)
				log.info ("Disconnecting agent in order to apply new configuration");
		    agent = null;
		    nextConnect = 0;
		    reconfigure = false;
		}
		// //////////////////////////////////////////////////////////
		// Contactar con el agente
		//
		if (agent == null && nextConnect < new java.util.Date().getTime()) {
		    try {
		        setStatus("Looking up server");
		        log.info("Connecting ...");
		        agent = connect(true);
		        nextConnect = new java.util.Date().getTime() + timeoutDelay;
		    } catch (Throwable e) {
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
        log.info("Getting tasks");

    	ConnectionPool pool = ConnectionPool.getPool();
    	
    	nextTask = taskqueue.getPendingTask(this);
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
	public void doAuthoritativeImport (ScheduledTask task) 
	{
		AuthoritativeLoaderEngine engine = new AuthoritativeLoaderEngine(this);
		engine.doAuthoritativeImport(task);
	}

	private TaskHandler processAndLogTask (TaskHandler t) throws InternalErrorException
	{
		ConnectionPool pool = ConnectionPool.getPool();
		String reason;
		boolean ok;
		TaskHandlerLog thl = t.getLog(getInternalId());
		if (thl == null || thl.getNext() < System.currentTimeMillis()) {
		    reason = "";
		    setStatus("Execute " + t.toString());
		    log.debug("Executing {} ", t.toString(), null);
		    Throwable throwable = null;
		    try {
		        processTask(agent, t);
		        ok = true;
		        log.debug("Task {} DONE", t.toString(), null);
		    } catch (RemoteException e) {
		        handleRMIError(e);
		        ok = false;
		        reason = "Cannot connect to " + getSystem().getUrl();
		        throwable = e;
		        // abort = true ;
		    } catch (Throwable e) {
		        log.warn("Error interno", e);
		        ok = false;
		        reason = e.toString();
		        throwable = e;
		    }
		    setStatus("Getting Task");
		    TaskHandler nextTask = taskqueue.getNextPendingTask(this, t);
		    // setStatus("Notifying task status " + t.transactionCode);
		    taskqueue.notifyTaskStatus(t, this, ok, reason, throwable);
		    t = nextTask;
		} else {
		    t = taskqueue.getNextPendingTask(this, t);
		}
		return t;
	}

    /**
     * Tratar los errores RMI
     * 
     * @param e
     *            error producido
     */
    void handleRMIError(Exception e) {
        if (agent != null) {
            log.info("Connection error {}", e.toString(), null);
            agent = null;
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
            propagatePassword(agent, t);
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
        } else {
            throw new InternalErrorException("Tipo de transacción no válida: " + trans);
        }
    }

	DataSource dataSource;
	private DataSource getDataSource ()
	{
		if (dataSource == null)
			dataSource = (DataSource) ServerServiceLocator.instance().getService("dataSource");
		return dataSource;
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
           	Account acc = accountService.findAccount(t.getTask().getUser(), getSystem().getName());
           	if (acc != null && ( acc.getType().equals (AccountType.IGNORED) ||
           			isUnmanagedType (acc.getPasswordPolicy())))
           	{
           		// Nothing to do
           		return;
           	}
           	else if (acc == null || 
           			acc.isDisabled())
           	{
       			userMgr.removeUser(t.getTask().getUser());
           			
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
           			
           			userMgr.updateUser(acc, user);
           		}
           		else
       				userMgr.updateUser(acc);
           	}
           	if (acc != null)
           	{
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
			unmanagedTypesTS = System.currentTimeMillis() + 60000; // Requery every minute 
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
        
       	Account acc = accountService.findAccount(t.getTask().getUser(), getSystem().getName());
       	if (acc != null && !acc.isDisabled() &&
       			! acc.getType().equals (AccountType.IGNORED) &&
       			! isUnmanagedType(acc.getPasswordPolicy()))
       	{
	        if ("S".equals(t.getTask().getPasswordChange()) && !getSystem().getTrusted().booleanValue()) {
	            Password p = server.generateFakePassword(acc.getName(), getSystem().getName());
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
	            
	            for (String user: accountService.getAccountUsers(acc))
	            {
	            	changePasswordNotificationQueue.addNotification(user);
	            }
	        }
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
            aliasMgr = (MailAliasMgr) agent;
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

    private void updateGroup(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        GroupMgr groupMgr = InterfaceWrapper.getGroupMgr (agent);
        if (groupMgr == null)
        	return;
        
    	if (t.getTask().getGroup() != null)
    	{
	        try {
	            Group grup = server.getGroupInfo(t.getTask().getGroup(), getSystem().getName());
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

        Account acc = accountService.findAccount(t.getTask().getUser(), getSystem().getName());
        if (acc != null && !acc.isDisabled() )
        {
        	if ( userMgr.validateUserPassword(acc.getName(), t.getPassword())) 
        	{
	            Syslogger.send(getName() + ":PropagatePassword", "user: " + acc.getName() + " password:"
	                    + t.getPassword().getHash() + ": ACCEPTED");
	            log.debug("Accepted proposed password for {}", acc.getName(), null);
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
	        	String timeout = System.getProperty("soffid.propagate.timeout");
	        	if (timeout != null)
	        	{
	        		long timeoutLong = Long.decode(timeout)*1000;
		        	TaskHandlerLog tasklog = t.getLog(getInternalId());
		        	if (tasklog == null || tasklog.first == 0 ||
		        			System.currentTimeMillis() < tasklog.first + timeoutLong)
		        	{
		                log.info("Rejected proposed password for {}. Retrying", t.getTask().getUser(), null);
		                throw new InternalErrorException("Rejected proposed password for "+t.getTask().getUser()+". Retry");
		        	}
	        	}
	        	
	       		log.info("Rejected proposed password for {}", t.getTask().getUser(), null);
	        }
	    } else {
       		log.debug("Ignoring proposed password for {}", t.getTask().getUser(), null);
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
		        if (!ok) {
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
	    				accountService.updateAccountLastUpdate(account);
	    	        	if (account.isDisabled())
	    	        		userMgr.removeUser(account.getName());
	    	        	else
	    	        		userMgr.updateUser(account, user);
	    	        }
    	        }
            } 
            catch (UnknownUserException e) 
            {
            	
            }
        }
         
    }


    private void cancelTask(TaskHandler t) throws InternalErrorException {
        taskqueue.cancelTask(t.getTask().getId());
    }

    private String getName() {
        return getSystem().getName();
    }

    private Date lastLog = null;

    /**
     * Recuperar los registros de acceso del agente remoto
     * 
     * @throws InternalErrorException
     *             error de lógica interna
     * @throws SQLException
     *             error al almacenar los logs
     * @throws RemoteException
     *             error de comunicaciones
     */
    public void getLog() throws InternalErrorException, RemoteException {
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
        return !reconfigure && agent != null;
    }

    /**
     * Inicializar las conexiones RMI
     * 
     * @throws InternalErrorException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ClassNotFoundException
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
     * @throws IOException
     */
    public Object connect(boolean mainAgent) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, InvocationTargetException, InternalErrorException, IOException {
        URLManager um = new URLManager(getSystem().getUrl());
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
                	agent = am.createLocalAgent(getSystem());
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
                	String agenturl = am.createAgent(getSystem());
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
            		agentVersion = ((AgentInterface)agent).getAgentVersion();
            	else if (agent instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
            		agentVersion = ((es.caib.seycon.ng.sync.agent.AgentInterface)agent).getAgentVersion();
            	else
            		agentVersion = "Unknown";
            	objectClass = agent.getClass();
            	lastConnect = new java.util.Date().getTime();
            	KerberosAgent krb = InterfaceWrapper.getKerberosAgent (agent);
            	if (krb != null) {
            		this.agent = agent;
                	String domain = krb.getRealmName();
                	KerberosManager m = new KerberosManager();
                	try {
                    	log.info("Using server principal {} for realm {}", m.getServerPrincipal(domain),
                            	domain);
                	} catch (Exception e) {
                    	log.warn("Unable to create Kerberos principal", e);
                	}
            	}
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
        try {
        	if (task.getTask().getTransaction().equals (TaskHandler.PROPAGATE_ACCOUNT_PASSWORD) ||
        			task.getTask().getTransaction().equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD) ||
        			task.getTask().getTransaction().equals(TaskHandler.VALIDATE_ACCOUNT_PASSWORD) ||
        			task.getTask().getTransaction().equals(TaskHandler.UPDATE_ACCOUNT))
        	{
	            user = server.getUserInfo(task.getTask().getUser(), getSystem().getName());
        	}
        	else
        	{
	            user = server.getUserInfo(task.getTask().getUser(), null);
        	}
            task.setUser(user);
        } catch (UnknownUserException e) {
            user = null;
        }
        return user;
    }

    private User getUserInfo(TaskHandler t) throws InternalErrorException, UnknownUserException {
        return server.getUserInfo(t.getTask().getUser(), null);
    }

    private Collection<UserAccount> getAccounts(TaskHandler task) throws InternalErrorException {
        User usuari = getTaskUser(task);
        if (usuari == null)
            return Collections.EMPTY_LIST;
        return server.getUserAccounts(usuari.getId(), getSystem().getName());
    }

    @Override
    public KerberosAgent getKerberosAgent() {
    	if (agent == null)
    		return null;
    	else
    		return InterfaceWrapper.getKerberosAgent (agent);
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
        return agent;
    }

	private static JbpmConfiguration getConfig ()
	{
		if (jbpmConfig == null)
		{
			jbpmConfig = JbpmConfiguration
							.getInstance("es/caib/seycon/ng/sync/jbpm/jbpm.cfg.xml");
		}
		return jbpmConfig;
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
		for (String user : (reconcileManager2 == null ? reconcileManager.getAccountsList() : reconcileManager2.getAccountsList())) {
            if (user.length() <= MAX_LENGTH) {
                taskEntity = tasqueEntityDao.newTaskEntity();
                taskEntity.setTransaction(TaskHandler.RECONCILE_USER);
                taskEntity.setDate(new Timestamp(System.currentTimeMillis()));
                taskEntity.setHost(taskHandler.getTask().getHost());
                taskEntity.setUser(user);
                taskEntity.setSystemName(getSystem().getName());
	            taskEntity.setTenant( tenant );
                taskqueue.addTask(taskEntity);
            }
        }

		// Create reconcile roles task
		taskEntity = tasqueEntityDao.newTaskEntity();
		taskEntity.setTransaction(TaskHandler.RECONCILE_ROLES);
		taskEntity.setDate(new Timestamp(System.currentTimeMillis()));
		taskEntity.setHost(taskHandler.getTask().getHost());
		taskEntity.setSystemName(getSystem().getName());
        taskEntity.setTenant( tenant );

		taskqueue.addTask(taskEntity);
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
						getSystem().getName());

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
			grants = server.getAccountRoles(accountName, getSystem().getName());
		
		for (Role role : reconcileManager.getAccountRoles(taskHandler.getTask().getUser()))
		{
			if (role.getName().length() <= MAX_LENGTH)
			{
				boolean found = false;
				for (RoleGrant rg: grants)
				{
					if (rg.getRoleName().equals (role.getName()) && rg.getSystem().equals (getSystem().getName()))
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
						getSystem().getName());

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
			grants = server.getAccountRoles(accountName, getSystem().getName());
		}
		
		for (RoleGrant role : reconcileManager.getAccountGrants(taskHandler.getTask().getUser()))
		{
			if (role.getRoleName().length() <= MAX_LENGTH)
			{
				boolean found = false;
				for (RoleGrant rg: grants)
				{
					if (rg.getRoleName().equals (role.getRoleName()) && rg.getSystem().equals (getSystem().getName()))
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
			Object agent = connect(false);
			if (applies(agent, task))
			{
				processTask(agent, task);
			}
		}
		catch (Exception e)
		{
			throw new InternalErrorException ("Unable to process out of band task", e);
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.engine.DispatcherHandler#doReconcile()
	 */
	@Override
	public void doReconcile (ScheduledTask task)
	{
		StringBuffer result = task.getLastLog();
		try {
    		Object agent = connect(false);
			ReconcileMgr reconMgr = InterfaceWrapper.getReconcileMgr(agent);	// Reconcile manager
			ReconcileMgr2 reconMgr2 = InterfaceWrapper.getReconcileMgr2(agent);	// Reconcile manager
    		if (reconMgr != null)
    		{
    			new ReconcileEngine (getSystem(), reconMgr).reconcile();
    		} 
    		else if (reconMgr2 != null)
        	{
        		new ReconcileEngine2 (getSystem(), reconMgr2, result).reconcile();
    		} 
    		else {
    			result.append ("This agent does not support account reconciliation");
    		}
		} 
		catch (Exception e)
		{
			task.setError(true);
			result.append ("*************\n");
			result.append ("**  ERROR **\n");
			result.append ("*************\n");
			result.append (e.toString()).append ("\n");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				log.warn("Error during reconcile process", e);
				SoffidStackTrace.printStackTrace(e, new PrintStream(out, true, "UTF-8"));
				result.append (out.toString("UTF-8"));
				if (result.length() > 32000)
				{
					result.replace(0, result.length()-32000, "** TRUNCATED FILE ***\n...");
				}
			} catch (Exception e2) {}
		}
	}
	
	public TaskHandler getNextTask ()
	{
		return nextTask;
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
			runLoopStart();
			dispatcherStatus = nextTask == null ? DispatcherStatus.GET_LOGS : DispatcherStatus.NEXT_TASK;
			break;
		case NEXT_TASK:
			runLoopNext();
			dispatcherStatus = nextTask == null ? DispatcherStatus.GET_LOGS : DispatcherStatus.NEXT_TASK;
			break;
		case GET_LOGS:
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

}

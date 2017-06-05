package es.caib.seycon.ng.sync.engine;

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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sql.DataSource;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.api.ReconcileTrigger;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.authoritative.service.AuthoritativeChangeService;
import com.soffid.iam.reconcile.common.AccountProposedAction;
import com.soffid.iam.reconcile.common.ProposedAction;
import com.soffid.iam.reconcile.common.ReconcileAccount;
import com.soffid.iam.reconcile.common.ReconcileAssignment;
import com.soffid.iam.reconcile.common.ReconcileRole;
import com.soffid.iam.reconcile.service.ReconcileService;
import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Table;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Configuracio;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.DominiContrasenya;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.LlistaCorreu;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.PoliticaContrasenya;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.TipusDada;
import es.caib.seycon.ng.comu.TipusUsuari;
import es.caib.seycon.ng.comu.UserAccount;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.model.AuditoriaEntity;
import es.caib.seycon.ng.model.AuditoriaEntityDao;
import es.caib.seycon.ng.model.TasqueEntity;
import es.caib.seycon.ng.model.TasqueEntityDao;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.ConfiguracioService;
import es.caib.seycon.ng.servei.DadesAddicionalsService;
import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.servei.DominiUsuariService;
import es.caib.seycon.ng.servei.GrupService;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.agent.AgentInterface;
import es.caib.seycon.ng.sync.agent.AgentManager;
import es.caib.seycon.ng.sync.bootstrap.QueryHelper;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool.ThreadBound;
import es.caib.seycon.ng.sync.engine.extobj.GroupExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.ObjectTranslator;
import es.caib.seycon.ng.sync.engine.extobj.UserExtensibleObject;
import es.caib.seycon.ng.sync.engine.extobj.ValueObjectMapper;
import es.caib.seycon.ng.sync.engine.kerberos.KerberosManager;
import es.caib.seycon.ng.sync.intf.AccessControlMgr;
import es.caib.seycon.ng.sync.intf.AccessLogMgr;
import es.caib.seycon.ng.sync.intf.AuthoritativeChange;
import es.caib.seycon.ng.sync.intf.AuthoritativeIdentitySource;
import es.caib.seycon.ng.sync.intf.AuthoritativeIdentitySource2;
import es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr;
import es.caib.seycon.ng.sync.intf.DatabaseReplicaOfflineChangeRetriever;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.intf.ExtensibleObjectMgr;
import es.caib.seycon.ng.sync.intf.GroupMgr;
import es.caib.seycon.ng.sync.intf.HostMgr;
import es.caib.seycon.ng.sync.intf.KerberosAgent;
import es.caib.seycon.ng.sync.intf.LogEntry;
import es.caib.seycon.ng.sync.intf.MailAliasMgr;
import es.caib.seycon.ng.sync.intf.NetworkMgr;
import es.caib.seycon.ng.sync.intf.ReconcileMgr;
import es.caib.seycon.ng.sync.intf.ReconcileMgr2;
import es.caib.seycon.ng.sync.intf.RoleMgr;
import es.caib.seycon.ng.sync.intf.SharedFolderMgr;
import es.caib.seycon.ng.sync.intf.UserMgr;
import es.caib.seycon.ng.sync.replica.DatabaseRepository;
import es.caib.seycon.ng.sync.replica.MainDatabaseSynchronizer;
import es.caib.seycon.ng.sync.servei.ChangePasswordNotificationQueue;
import es.caib.seycon.ng.sync.servei.LogCollectorService;
import es.caib.seycon.ng.sync.servei.SecretStoreService;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.util.Syslogger;

public class DispatcherHandlerImpl extends DispatcherHandler implements Runnable {
    private static JbpmConfiguration jbpmConfig;
	Logger log = LoggerFactory.getLogger(DispatcherHandler.class);
    boolean active = true;
    private boolean reconfigure = false;
    private String status;
    private Object agent;
    private Object objectClass;
    private long lastConnect;
    private ServerService server;
    private boolean actionStop;
    private long taskQueueStartTime;
    private long nextConnect;
    private Exception connectException;
    private String agentVersion;
    private es.caib.seycon.ng.sync.servei.TaskQueue taskqueue;
    private es.caib.seycon.ng.sync.servei.TaskGenerator taskgenerator;
    private SecretStoreService secretStoreService;
    private String targetHost;
    private Thread currentThread;
    private ChangePasswordNotificationQueue changePasswordNotificationQueue;
    private TasqueEntityDao tasqueEntityDao;
    private InternalPasswordService internalPasswordService;
	private ReconcileService reconcileService;
	private AccountService accountService;
	private DominiUsuariService dominiService;
	private DispatcherService dispatcherService;
	
	
	private AuditoriaEntityDao auditoriaDao;
	private static final int MAX_LENGTH = 150;
	private static final int MAX_ROLE_CODE_LENGTH = 50;
	private DominiContrasenya passwordDomain = null;
	private ObjectTranslator attributeTranslator;
	private AuthoritativeChangeService authoritativeService;

	private enum DispatcherStatus {
		STARTING,
		LOOP_START,
		NEXT_TASK,
		GET_LOGS,
		WAIT
	}
	DispatcherStatus dispatcherStatus = DispatcherStatus.STARTING;
	private UsuariService usuariService;
	private GrupService grupService;

	public DominiContrasenya getPasswordDomain() throws InternalErrorException
	{
		if (passwordDomain == null)
		{
			passwordDomain = dominiService.findDominiContrasenyaByCodi(getDispatcher().getDominiContrasenyes());
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
        tasqueEntityDao = (TasqueEntityDao) ServerServiceLocator.instance().getService("tasqueEntityDao");
        internalPasswordService = (InternalPasswordService)
        		ServerServiceLocator.instance().getInternalPasswordService();
        accountService = ServerServiceLocator.instance().getAccountService();
        dominiService = ServerServiceLocator.instance().getDominiUsuariService();
        dispatcherService = ServerServiceLocator.instance().getDispatcherService();
        usuariService = ServerServiceLocator.instance().getUsuariService();
        ServerServiceLocator.instance().getDadesAddicionalsService();
        grupService = ServerServiceLocator.instance().getGrupService();
        auditoriaDao = (AuditoriaEntityDao) ServerServiceLocator.instance().getService("auditoriaEntityDao");
        reconcileService = ServerServiceLocator.instance().getReconcileService();
        authoritativeService = ServerServiceLocator.instance().getAuthoritativeChangeService();
        
        active = true;
    }

    @Override
    public boolean applies(TaskHandler t) {
    	return applies (agent, t);
    }
    	
    public boolean applies(Object agent, TaskHandler t) {
    	    
        String trans = t.getTask().getTransa();
        boolean trusted = isTrusted();
        boolean readOnly = getDispatcher().isReadOnly();
        // Verificar el nom del dipatcher
        if (t.getTask().getCoddis() != null
                && !this.getDispatcher().getCodi().equals(t.getTask().getCoddis())) {
            return false;

            // Verificar el domini de contrasenyes
        } else if (t.getTask().getDominiContrasenyes() != null
                && !t.getTask().getDominiContrasenyes()
                        .equals(getDispatcher().getDominiContrasenyes())) {
            return false;

            // Verificar el domini d' usuaris
        } else if (t.getTask().getDominiUsuaris() != null
                && !t.getTask().getDominiUsuaris().equals(getDispatcher().getDominiUsuaris())) {
            return false;

        } else if (t.getTask().getTransa().equals(TaskHandler.UPDATE_USER)) {
            return !readOnly && (
            				implemented(agent, es.caib.seycon.ng.sync.intf.UserMgr.class) );
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_ACCOUNT)) {
            return !readOnly && (implemented(agent, UserMgr.class) );
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_USER_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class)  ) &&
            	getDispatcher().getDominiContrasenyes().equals(t.getTask().getDominiContrasenyes());
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class)  );
        }
        ///////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_PROPAGATED_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class) );
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.PROPAGATE_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class)  );
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.PROPAGATE_ACCOUNT_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class) );
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.EXPIRE_USER_PASSWORD)) {
            return !readOnly && (implemented(agent, UserMgr.class)  );
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.EXPIRE_USER_UNTRUSTED_PASSWORD)) {
            return !readOnly && !trusted && (implemented(agent, UserMgr.class) );
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.VALIDATE_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class)  ) &&
                        	getDispatcher().getDominiContrasenyes().equals(t.getTask().getDominiContrasenyes());

        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.VALIDATE_ACCOUNT_PASSWORD)) {
            return trusted && (implemented(agent, UserMgr.class)  );

        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.CREATE_FOLDER)) {
            return !readOnly && implemented(agent, es.caib.seycon.ng.sync.intf.SharedFolderMgr.class);
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_GROUP)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.GroupMgr.class) ||
            				implemented(agent, es.caib.seycon.ng.sync.intf.ExtensibleObject.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_ROLE)) {
            return !readOnly && (implemented(agent, es.caib.seycon.ng.sync.intf.RoleMgr.class));
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_HOST)) {
            return !readOnly && implemented(agent, es.caib.seycon.ng.sync.intf.HostMgr.class);
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.PURGE_HOSTS)) {
            return !readOnly && implemented(agent, es.caib.seycon.ng.sync.intf.HostMgr.class);
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.GET_LOG)) {
            return implemented(agent, es.caib.seycon.ng.sync.intf.AccessLogMgr.class);
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_NETWORKS)) {
            return !readOnly && implemented(agent, es.caib.seycon.ng.sync.intf.NetworkMgr.class);
        }
        // /////////////////////////////////////////////////////////////////////
        else if (trans.equals(TaskHandler.UPDATE_USER_ALIAS)) {
            return !readOnly && implemented(agent, es.caib.seycon.ng.sync.intf.MailAliasMgr.class);
        } else if (trans.equals(TaskHandler.UPDATE_LIST_ALIAS)) {
            return !readOnly && implemented(agent, es.caib.seycon.ng.sync.intf.MailAliasMgr.class);
        }
		else if (trans.equals(TaskHandler.END_RECONCILE))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class) ||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_USER))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class)||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_ROLE))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class) ||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.RECONCILE_ROLES))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr.class) ||
					implemented(agent, es.caib.seycon.ng.sync.intf.ReconcileMgr2.class);
		}
		else if (trans.equals(TaskHandler.UPDATE_OBJECT) ||
				trans.equals(TaskHandler.DELETE_OBJECT))
		{
			return implemented(agent, es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr.class);
        } else {
            return true;
        }
    }

    private boolean isTrusted() {
        return getDispatcher().getSegur() != null && getDispatcher().getSegur().booleanValue();
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
    public void setDispatcher(Dispatcher dispatcher) throws InternalErrorException {
        log = LoggerFactory.getLogger(dispatcher.getCodi());
        super.setDispatcher(dispatcher);
    	attributeTranslator = new ObjectTranslator(dispatcher);
    }

    public void reconfigure(Dispatcher newDispatcher) throws InternalErrorException {
        setDispatcher(newDispatcher);

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
	private LinkedList<ReconcileTrigger> preInsertTrigger;
	private LinkedList<ReconcileTrigger> postInsertTrigger;
	private LinkedList<ReconcileTrigger> preUpdateTrigger;
	private LinkedList<ReconcileTrigger> postUpdateTrigger;


    public void run() {
        try {
        	ConnectionPool pool = ConnectionPool.getPool();
        	
            currentThread = Thread.currentThread();
            Thread.currentThread().setName(getDispatcher().getCodi());
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
		if (nextTask.isOfflineTask() && pool.isOfflineMode() ||
			! nextTask.isOfflineTask() && ! pool.isOfflineMode())
		{
			try {
				startTask(false);
				nextTask = processAndLogTask(nextTask);
			} finally {
				endTask();
			}
		}
		else
			nextTask = taskqueue.getNextPendingTask(this, nextTask);
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
		        if (agent instanceof DatabaseReplicaMgr)
		        {
		        	try {
		            	MainDatabaseSynchronizer mds = new MainDatabaseSynchronizer();
		            	mds.setAgent((DatabaseReplicaOfflineChangeRetriever) agent);
		            	mds.doSynchronize();
		            } catch (Throwable e) {
		                log.warn("Error synchronyzing main database {}: {} ", getDispatcher().getCodi(), e.toString());
		            }
		        }
		    } catch (Throwable e) {
		        delay = timeoutDelay;
		        if (e instanceof Exception)
		        	connectException = (Exception) e;
		        else
		        	connectException = new InternalErrorException("Unexepcted error", e);
		        log.warn("Error connecting {}: {} ", getDispatcher().getCodi(), e.toString());
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

		StringBuffer result = task.getLastLog();
		try {
			preInsertTrigger = new LinkedList<ReconcileTrigger>();
			postInsertTrigger = new LinkedList<ReconcileTrigger>();
			preUpdateTrigger = new LinkedList<ReconcileTrigger>();
			postUpdateTrigger = new LinkedList<ReconcileTrigger>();
			for (ReconcileTrigger trigger: dispatcherService.findReconcileTriggersByDispatcher(dispatcher.getId()))
			{
				if (trigger.getObjectType().equals(SoffidObjectType.OBJECT_USER))
				{
					if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_INSERT))
						preInsertTrigger.add (trigger);
					else if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_UPDATE))
						preUpdateTrigger.add (trigger);
					if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_INSERT))
						postInsertTrigger.add (trigger);
					if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_UPDATE))
						postUpdateTrigger.add (trigger);
				}
			}
			ObjectTranslator objectTranslator = new ObjectTranslator (dispatcher);
			ValueObjectMapper vom = new ValueObjectMapper();

			Object agent = connect(false);
    		if (agent instanceof AuthoritativeIdentitySource)
    		{
    			AuthoritativeIdentitySource source = (AuthoritativeIdentitySource) agent;
    			Collection<AuthoritativeChange> changes = source.getChanges();
    			if ( changes != null && !changes.isEmpty())
    			{
	    	        for (AuthoritativeChange change: changes)
	    	        {
	    	        	processChange(change, source, result, objectTranslator, vom);
	    	        }
	    			changes = source.getChanges();
    			}
    		} else if (agent instanceof AuthoritativeIdentitySource2)
    		{
    			ConfiguracioService cfgSvc = ServiceLocator.instance().getConfiguracioService();
    			String lastId = null;
    			String cfgId = "soffid.sync.authoritative.change."+getDispatcher().getCodi();
    			Configuracio cfg = cfgSvc.findParametreByCodiAndCodiXarxa(cfgId, null);
    			if (cfg != null)
    				lastId = cfg.getValor();
    			AuthoritativeIdentitySource2 source = (AuthoritativeIdentitySource2) agent;
				boolean anyError = false;
				do
				{
					Collection<AuthoritativeChange> changes = source.getChanges(lastId);
					if (changes == null || changes.isEmpty())
						break;
	    	        for (AuthoritativeChange change: changes)
	    	        {
	    	        	if ( processChange(change, null, result, objectTranslator, vom ) )
	    	        		anyError = true;
	    	        }
   				} while (source.hasMoreData());
				String nextChange = source.getNextChange();
				if (! anyError && nextChange != null)
				{
					if (cfg == null) {
						cfg = new Configuracio();
						cfg.setValor(nextChange);
						cfg.setCodi(cfgId);
						cfg.setDescripcio("Last authoritative change id loaded");
						cfgSvc.create(cfg);
					} else {
						cfg.setValor(nextChange);
						cfgSvc.update(cfg);
					}
				}
    		} else {
    			result.append ("This agent does not support account reconciliation");
    		}
		} 
		catch (Exception e)
		{
			log.info("Error performing authoritative data load process", e);
			result.append ("*************\n");
			result.append ("**  ERROR **\n");
			result.append ("*************\n");
			result.append (e.toString());
			result.append("\n\nStack trace:\n")
				.append(SoffidStackTrace.getStackTrace(e));
			task.setError(true);
		}
		
	}

	
	private boolean processChange(AuthoritativeChange change,
			AuthoritativeIdentitySource source, StringBuffer result, ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getDispatcher().getCodi());
		try {
			Usuari previousUser = change.getUser() == null ||
					change.getUser().getCodi() == null ? null :
						usuariService.findUsuariByCodiUsuari(change.getUser().getCodi());
			boolean ok = true;
			if (previousUser == null)
			{
				if (! preInsertTrigger.isEmpty())
				{
					UserExtensibleObject eo = buildExtensibleObject(change);
					eo.setAttribute("attributes", change.getAttributes());
					if (executeTriggers(preInsertTrigger, null, eo, objectTranslator))
					{
						change.setUser( vom.parseUsuari(eo));
						change.setAttributes((Map<String, Object>) eo.getAttribute("attributes"));
					}
					else
					{
						result.append("Change to user "+change.getUser().getCodi()+" is rejected by pre-insert trigger\n");
						log.info("Change to user "+change.getUser().getCodi()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				if (! preUpdateTrigger.isEmpty())
				{
					UserExtensibleObject eo = buildExtensibleObject(change);
					if (executeTriggers(preUpdateTrigger, 
							new UserExtensibleObject(new Account (), previousUser, server), 
							eo, objectTranslator))
					{
						change.setUser( vom.parseUsuari(eo));
						change.setAttributes((Map<String, Object>) eo.getAttribute("attributes"));
					}
					else
					{
						result.append("Change to user "+change.getUser().getCodi()+" is rejected by pre-update trigger\n");
						log.info("Change to user "+change.getUser().getCodi()+" is rejected by pre-update trigger");
						ok = false;
					}
				}
			}
			if (ok)
			{
				if (authoritativeService
						.startAuthoritativeChange(change))
				{
					result.append(
							"Applied authoritative change for  ")
							.append(change.getUser().getCodi())
							.append("\n");
					log.info(
							"Applied authoritative change for  "+change.getUser().getCodi());
				}
				else
				{
					log.info("Prepared authoritative change for  "+change.getUser().getCodi());
					result.append(
							"Prepared authoritative change for  ")
							.append(change.getUser().getCodi())
							.append("\n");
				}

				if (previousUser == null)
				{
					if (! postInsertTrigger.isEmpty())
					{
						UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
						executeTriggers(postInsertTrigger, null, eo, objectTranslator);
					}
				} else {
					if (! postUpdateTrigger.isEmpty())
					{
						UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
						executeTriggers(postUpdateTrigger, 
								new UserExtensibleObject(new Account (), previousUser, server), 
								eo, objectTranslator);
					}
				}
				if (source != null)
					source.commitChange(change.getId());
			}
		} catch ( Exception e) {
			error = true;
			log.info("Error uploading change "+change.getId().toString(), e);
			log.info("User information: "+change.getUser().toString());
			result.append ("Error uploading change ")
				.append(change.getId().toString())
				.append(":");
			StringWriter sw = new StringWriter();
			e.printStackTrace (new PrintWriter(sw));
			result.append(sw.getBuffer())
				.append ("\n");
			result.append("User information: ").append(change.getUser()).append("\n");
		}
		return error;
	}

	private UserExtensibleObject buildExtensibleObject(
			AuthoritativeChange change) throws InternalErrorException {
		UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
		eo.setAttribute("attributes", change.getAttributes());
		List<ExtensibleObject> l = new LinkedList<ExtensibleObject>();
		if (change.getGroups() != null)
		{
			for (String s: change.getGroups())
			{
				Grup g = null;
				
				try {
					g = server.getGroupInfo(s, getName());
				} catch (UnknownGroupException e) {
				}
				
				if (g == null)
				{
					ExtensibleObject eo2 = new ExtensibleObject();
					eo2.setAttribute("name", s);
					l.add(eo2);
				}
				else
				{
					l.add( new GroupExtensibleObject(g, getName(), server));
				}
			}
		}
		eo.setAttribute("secondaryGroups", l);
		return eo;
	}

	private boolean executeTriggers (List<ReconcileTrigger> triggerList, ExtensibleObject old, ExtensibleObject newObject, ObjectTranslator objectTranslator) throws InternalErrorException
	{
		ExtensibleObject eo = new ExtensibleObject ();
		eo.setAttribute("oldObject", old);
		eo.setAttribute("newObject", newObject);
		boolean ok = true;
		for (ReconcileTrigger t: triggerList)
		{
			if (! objectTranslator.evalExpression(eo, t.getScript()))
				ok = false;
		}
		
		return ok;
	}

	private TaskHandler processAndLogTask (TaskHandler t) throws InternalErrorException
	{
		ConnectionPool pool = ConnectionPool.getPool();
		pool.setThreadStatus(
			t.isOfflineTask()? 
				ConnectionPool.ThreadBound.BACKUP:
				ConnectionPool.ThreadBound.MASTER);
		try
		{
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
    		        reason = "Cannot connect to " + getDispatcher().getUrl();
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
    	} finally {
    		pool.setThreadStatus(ThreadBound.ANY);
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
        Usuari uservo = getTaskUser(t);
        if (uservo == null)
            throw new InternalErrorException(String.format("Unknown user %s", uservo));
        return server.generateFakePassword(getDispatcher().getDominiContrasenyes());
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
                throw new RemoteException("Unable to connect to " + getDispatcher().getUrl(),
                        connectException);
        }
        String trans = t.getTask().getTransa();
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

		else if (trans.equals(TaskHandler.DELETE_OBJECT))
		{
			updateObject(agent, t);
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

	/**
	 * @param agent2
	 * @param t
	 * @throws InternalErrorException
	 */
	private void updateObject (Object agent2, TaskHandler t) throws InternalErrorException
	{
		DatabaseReplicaMgr mgr;
		try {
			mgr = (DatabaseReplicaMgr) agent;
		} catch (ClassCastException e) {
			return;
		}
		try {
			DatabaseRepository db ;
			try {
				db = new DatabaseRepository();
			} catch (Exception e)
			{
				throw new InternalErrorException("Error parsing data model", e);
			}
			Table table = db.getTable(t.getTask().getEntity());
			if (table != null)
			{
				Connection conn = getDataSource().getConnection();
				try
				{
					boolean exists;
					Vector<String> cols = new Vector<String>(table.columns.size());
					Vector<Object> values = new Vector<Object>(table.columns.size());
					StringBuffer sb1 = new StringBuffer();
					String primaryKey = null;
					for (Column column : table.columns)
					{
						if (column.primaryKey)
						{
							primaryKey = column.name;
						}
						else
						{
							if (sb1.length() == 0)
								sb1.append("SELECT ");
							else
								sb1.append(", ");
							sb1.append(column.name);
							cols.add(column.name);
						}
					}
					if (primaryKey != null)
					{
						sb1.append(" FROM ").append(table.name).append(" WHERE ")
										.append(primaryKey).append("= ?");
						QueryHelper qh  = new QueryHelper(conn);
						qh.setEnableNullSqlObject(true);
						List<Object[]> result = qh.
										select(sb1.toString(), 
												new Object[] { t.getTask().getPrimaryKeyValue() });
						if (result.isEmpty())
						{
							mgr.remove(table.name, primaryKey, t.getTask()
											.getPrimaryKeyValue());
						}
						else
						{
							Object rows[] = result.get(0);
							for (int i = 0; i < rows.length; i++)
								values.add(rows[i]);
							
							mgr.update(table.name, primaryKey, t.getTask()
								.getPrimaryKeyValue(), cols, values);
						}
					}
				} finally {
					conn.close ();
				}
			}
		}
		catch (SQLException e1)
		{
			e1.printStackTrace();
			throw new InternalErrorException("Error executing task", e1);
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

        UserMgr userMgr = null;
        if (agent instanceof UserMgr)
        {
            userMgr = (UserMgr) agent;
       		if (userMgr.validateUserPassword(t.getTask().getUsuari(), t.getPassword())) {
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
		UserMgr userMgr = agent instanceof UserMgr ? (UserMgr) agent: null;
		if (userMgr != null)
		{
			if (t.getTask().getUsuari() == null || t.getTask().getUsuari().trim().length() == 0 )
				return;
           	Account acc = accountService.findAccount(t.getTask().getUsuari(), getDispatcher().getCodi());
           	if (acc != null && ( acc.getType().equals (AccountType.IGNORED) ||
           			isUnmanagedType (acc.getPasswordPolicy())))
           	{
           		// Nothing to do
           		return;
           	}
           	else if (acc == null || 
           			acc.isDisabled())
           	{
       			userMgr.removeUser(t.getTask().getUsuari());
           			
           	}
           	else
           	{
           		if (acc instanceof UserAccount)
           		{
           			String userId = ((UserAccount)acc).getUser();
           			Usuari user;
           			try
    				{
    					user = server.getUserInfo(userId, null);
    				}
    				catch (UnknownUserException e)
    				{
    					throw new InternalErrorException("Error getting user "+userId);
    				}
           			
           			userMgr.updateUser(acc.getName(), user);
           		}
           		else
       				userMgr.updateUser(acc.getName(), acc.getDescription());
           	}
           	if (acc != null)
           	{
           		accountService.updateAccountLastUpdate(acc);
           	}
		}
	}

	static long unmanagedTypesTS = 0;
	static HashSet<String> unmangedTypes = null;
	private boolean isUnmanagedType(String passwordPolicy) throws InternalErrorException {
		if (System.currentTimeMillis() > unmanagedTypesTS)
		{
			unmangedTypes = new HashSet<String>();
			for (TipusUsuari tu: ServiceLocator.instance().getDominiUsuariService().findAllTipusUsuari())
			{
				if (tu.isUnmanaged())
					unmangedTypes.add(tu.getCodi());
			}
			unmanagedTypesTS = System.currentTimeMillis() + 60000; // Requery every minute 
		}
		return unmangedTypes.contains(passwordPolicy);
	}

	/**
	 * @param t
	 * @throws InternalErrorException 
	 * @throws RemoteException 
	 * @throws AccountAlreadyExistsException 
	 */
	private void updateAccountPassword (Object agent, TaskHandler t) throws InternalErrorException, RemoteException
	{
        UserMgr userMgr = null;
        if (agent instanceof UserMgr)
            userMgr = (UserMgr) agent;
        else
        	return;
        
       	Account acc = accountService.findAccount(t.getTask().getUsuari(), getDispatcher().getCodi());
       	if (acc != null && !acc.isDisabled() &&
       			! acc.getType().equals (AccountType.IGNORED) &&
       			! isUnmanagedType(acc.getPasswordPolicy()))
       	{
	        if ("S".equals(t.getTask().getCancon()) && !getDispatcher().getSegur().booleanValue()) {
	            Password p = server.generateFakePassword(acc.getName(), getDispatcher().getCodi());
            	userMgr.updateUserPassword(acc.getName(), null, p, true);
        		auditAccountPasswordChange(acc, null, true);
	    		accountService.updateAccountPasswordDate(acc, new Long(0));
	        } else {
	            Password p;
	            p = getTaskPassword(t);
            	userMgr.updateUserPassword(acc.getName(), null, p, "S".equals(t.getTask().getCancon()));
	
        		auditAccountPasswordChange(acc, null, false);

        		secretStoreService.setPasswordAndUpdateAccount(acc.getId(), p,
        				 "S".equals((t.getTask().getCancon())),
        				 t.getTask().getExpirationDate() == null ? null: t.getTask().getExpirationDate().getTime());
	            
	            for (String user: accountService.getAccountUsers(acc))
	            {
	            	changePasswordNotificationQueue.addNotification(user);
	            }
	        }
    	}
	}

	private Long getPasswordTerm (PoliticaContrasenya politica)
	{
		Long l = null;
		
		if (politica != null && politica.getDuradaMaxima() != null && politica.getTipus().equals("M"))
		    l = politica.getDuradaMaxima();
		else if (politica != null && politica.getTempsRenovacio() != null && politica.getTipus().equals("A"))
			l = politica.getTempsRenovacio();
		else
			l = new Long(3650);
		return l;
	}

	private void updateAccessControl(Object agent) throws RemoteException, InternalErrorException {
        AccessControlMgr accessControlMgr;
        try {
            accessControlMgr = (AccessControlMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        accessControlMgr.updateAccessControl();
    }

    private void updateMailList(Object agent, TaskHandler t) throws InternalErrorException {
        MailAliasMgr aliasMgr;
        try {
            aliasMgr = (MailAliasMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
    	if (t.getTask().getAlies() != null && t.getTask().getDomcor() != null)
    	{
    		try {
	            LlistaCorreu llista = server.getMailList(t.getTask().getAlies(), t.getTask()
	                    .getDomcor());
	            aliasMgr.updateListAlias(llista);
	        } catch (UnknownMailListException e) {
	            aliasMgr.removeListAlias(t.getTask().getAlies(), t.getTask().getDomcor());
        	}
        }
    }

    private void updateUserAlias(Object agent, TaskHandler t) throws InternalErrorException {
        MailAliasMgr aliasMgr;
        try {
            aliasMgr = (MailAliasMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        try {
            Usuari usuari = getUserInfo(t);
            if (usuari.getNomCurt() == null || usuari.getDominiCorreu() == null)
                aliasMgr.removeUserAlias(usuari.getCodi());
            else
                aliasMgr.updateUserAlias(usuari.getCodi(), usuari);
        } catch (UnknownUserException e) {
            aliasMgr.removeUserAlias(t.getTask().getUsuari());
        }
    }

    private void updateNetworks(Object agent) throws RemoteException, InternalErrorException {
        NetworkMgr networkMgr;
        try {
            networkMgr = (NetworkMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        networkMgr.updateNetworks();
    }

    private void updateHost(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        HostMgr hostMgr;
        try {
            hostMgr = (HostMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        try {
            Maquina maq = server.getHostInfo(t.getTask().getMaquin());
            hostMgr.updateHost(maq);
        } catch (UnknownHostException e) {
            hostMgr.removeHost(t.getTask().getMaquin());
        }
    }

    private void updateRole(Object agent, TaskHandler t, String trans) throws InternalErrorException,
            RemoteException {
        RoleMgr roleMgr;
        try {
            roleMgr = (RoleMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        String rol = t.getTask().getRole();
        String bd = t.getTask().getBd();
        try {
            Rol rolInfo = server.getRoleInfo(rol, bd);
            if(rolInfo == null)
            	roleMgr.removeRole(rol, bd);
            else
            	roleMgr.updateRole(rolInfo);
        } catch (UnknownRoleException e) {
            roleMgr.removeRole(rol, bd);
        }
    }

    private void updateGroup(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        GroupMgr groupMgr;
        try {
            groupMgr = (GroupMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
    	if (t.getTask().getGrup() != null)
    	{
	        try {
	            Grup grup = server.getGroupInfo(t.getTask().getGrup(), getDispatcher().getCodi());
	            groupMgr.updateGroup(t.getTask().getGrup(), grup);
	        } catch (UnknownGroupException e) {
	            groupMgr.removeGroup(t.getTask().getGrup());
	        }
    	}
    }

    // TODO: Fix
    private void createFolder(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        SharedFolderMgr sharedFolderMgr;
        try {
            sharedFolderMgr = (SharedFolderMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        // Comprobar que el destino es el adecuado
//        if (t.getTask().getTipcar().equals("U")) {
//            Usuari ui;
//            String user;
//            try {
//                ui = server.getUserInfo(t.getTask().getCarpet(), getDispatcher().getCodi());
//                if (targetHost.equalsIgnoreCase(ui.getServidorHome())) {
//                    sharedFolderMgr.createFolder(user,
//                            SharedFolderMgr.userFolderType);
//                }
//                user = server.getUserKey(ui.getId(), getDispatcher().getDominiUsuaris());
//                if (user == null)
//                    return;
//            } catch (UnknownUserException e) {
//                return;
//            }
//        } else {
//            Grup gi;
//            try {
//            	// TODO: fix
//                gi = server.getGroupInfo(t.getTask().getCarpet(), getDispatcher().getId());
//            } catch (UnknownGroupException e) {
//                return;
//            }
//            if (targetHost.equalsIgnoreCase(gi.getNomServidorOfimatic())) {
//                sharedFolderMgr.createFolder(t.getTask().getCarpet(),
//                        SharedFolderMgr.groupFolderType);
//            } // end if
//        } // end if
    }

    private void validatePassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        if (!isTrusted() || t.isValidated() || t.isExpired() || t.isComplete())
            return;

        UserMgr userMgr = null;
        if (agent instanceof UserMgr)
            userMgr = (UserMgr) agent;
        else
        	return;

        if (getDispatcher().getDominiContrasenyes().equals(t.getTask().getDominiContrasenyes()))
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

        UserMgr userMgr;
        try {
            userMgr = (UserMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        Account acc = accountService.findAccount(t.getTask().getUsuari(), getDispatcher().getCodi());
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
		            
		            TasqueEntity te = tasqueEntityDao.newTasqueEntity();
		            te.setTransa(TaskHandler.UPDATE_PROPAGATED_PASSWORD);
		            te.setDominiContrasenyes(getDispatcher().getDominiContrasenyes());
		            te.setContra(t.getPassword().toString());
		            te.setUsuari(ua.getUser());
		            taskqueue.addTask(te);
		            
		            AuditoriaEntity auditoria = auditoriaDao.newAuditoriaEntity();
		            auditoria.setAccio("L");
		            auditoria.setData(new Date());
		            auditoria.setUsuari(ua.getUser());
		            auditoria.setPasswordDomain(getDispatcher().getDominiContrasenyes());
		            auditoria.setObjecte("SC_USUARI");
		            auditoria.setBbdd(getDispatcher().getCodi());
		            auditoria.setAccount(acc.getName());
		            auditoriaDao.create(auditoria);

					internalPasswordService.storePassword(ua.getUser(), getDispatcher().getDominiContrasenyes(), 
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
		            AuditoriaEntity auditoria = auditoriaDao.newAuditoriaEntity();
		            auditoria.setAccio("L");
		            auditoria.setData(new Date());
		            auditoria.setAccount(acc.getName());
		            auditoria.setBbdd(acc.getDispatcher());
		            auditoria.setObjecte("SC_ACCOUN");
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
		                log.info("Rejected proposed password for {}. Retrying", t.getTask().getUsuari(), null);
		                throw new InternalErrorException("Rejected proposed password for "+t.getTask().getUsuari()+". Retry");
		        	}
	        	}
	        	
	       		log.info("Rejected proposed password for {}", t.getTask().getUsuari(), null);
	        }
	    } else {
       		log.debug("Ignoring proposed password for {}", t.getTask().getUsuari(), null);
	    }
    }


    private void expireUserPassword(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        UserMgr userMgr;
        try {
            userMgr = (UserMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        Usuari user;
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
        UserMgr userMgr;
        try {
            userMgr = (UserMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
        Usuari user;
        try {
            user = getUserInfo(t);
        } catch (UnknownUserException e) {
            return;
        }
        if (isUnmanagedType(user.getTipusUsuari()))
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
		            				t.getTask().getUsuari(), getDispatcher().getCodi());
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
		        						t.getTask().getUsuari(), getDispatcher().getCodi());
		        	}
		        	else
		        	{
		        		log.debug("Storing already updated password for {}/{}",
		        						t.getTask().getUsuari(), getDispatcher().getCodi());
	   		            secretStoreService.setPasswordAndUpdateAccount(acc.getId(), p,
	   		            		false, null);
	   		            anyChange = true;
		        	}
		        }
    		}
    	}
    	if (anyChange)
    	{
           	changePasswordNotificationQueue.addNotification(t.getTask().getUsuari());
    	}
    }
    
    private void auditAccountPasswordChange (Account account, Usuari user, boolean random)
    {
        AuditoriaEntity auditoria = auditoriaDao.newAuditoriaEntity();
        auditoria.setAccio(random ? "Z": "X");
        auditoria.setData(new Date());
        auditoria.setAccount(account.getName());
        auditoria.setBbdd(account.getDispatcher());
        auditoria.setObjecte("SC_ACCOUN");
        if (user != null) auditoria.setUsuari(user.getCodi());
        auditoria.setBbdd(getDispatcher().getCodi());
        auditoriaDao.create(auditoria);
    }

    private void updateUserPassword(Object agent2, TaskHandler t) throws InternalErrorException, RemoteException {
        UserMgr userMgr = null;
        if (agent instanceof UserMgr)
            userMgr = (UserMgr) agent;
        else
        	return;

        Usuari user;
        try {
            user = getUserInfo(t);
        } catch (UnknownUserException e) {
            return;
        }

        if (isUnmanagedType(user.getTipusUsuari()))
        	return;
        
    	for (Account acc: getAccounts(t))
    	{
    		if (!acc.isDisabled())
    		{
		        if ("S".equals(t.getTask().getCancon()) && !getDispatcher().getSegur().booleanValue()) {
		            Password p;
		            p = generateRandomUserPassword(t);
	            	userMgr.updateUserPassword(acc.getName(), user, p, true);
            		accountService.updateAccountPasswordDate(acc, new Long(0));
            		auditAccountPasswordChange(acc, user, true);
		        } else {
		            Password p;
		            p = getTaskPassword(t);
	            	userMgr.updateUserPassword(acc.getName(), user, p, "S".equals(t.getTask().getCancon()));
		
            		auditAccountPasswordChange(acc, user, false);

            		if (! "S".equals(t.getTask().getCancon()))
		            {
		            	PoliticaContrasenya politica = dominiService.findPoliticaByTipusAndDominiContrasenyas(
		    	            			acc.getPasswordPolicy(), getDispatcher().getDominiContrasenyes());
	   	            	accountService.updateAccountPasswordDate(acc, getPasswordTerm(politica));
		            } else {
   	            		accountService.updateAccountPasswordDate(acc, new Long(0));
		            }
		            secretStoreService.setPassword(acc.getId(), p);
		            Syslogger.send(
		                    String.format("%s:UpdateUserPassword[%d]", getDispatcher().getCodi()
		                            , t.getTask().getId()),
		                    String.format("user:%s password:%s", acc.getName(), p.getHash()));
		        }
    		}
    	}
       	changePasswordNotificationQueue.addNotification(t.getTask().getUsuari());
    }

    private void updateUser(Object agent, TaskHandler t) throws InternalErrorException, RemoteException {
        if (agent instanceof UserMgr )
        {
            Usuari user = null;
            try {
    	        user = getUserInfo(t);
    	        if (! isUnmanagedType ( user.getTipusUsuari()))
    	        {
	        		for (Account account: getAccounts(t))
	    	        {
	    				accountService.updateAccountLastUpdate(account);
	    	        	if (account.isDisabled())
	    	            	((UserMgr) agent).removeUser(account.getName());
	    	        	else
	    	        		((UserMgr) agent).updateUser(account.getName(), user);
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
        return getDispatcher().getCodi();
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
        AccessLogMgr logmgr;
        try {
            logmgr = (AccessLogMgr) agent;
        } catch (ClassCastException e) {
            return;
        }
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

        Collection<LogEntry> le;
        le = logmgr.getLogFromDate(date);

        log.debug("Got log ", null, null);
        
        if (le != null) {
            for (Iterator<LogEntry> it = le.iterator(); it.hasNext();) {
                LogEntry logEntry = it.next();
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
    private Object connect(boolean mainAgent) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, InvocationTargetException, InternalErrorException, IOException {
        URLManager um = new URLManager(getDispatcher().getUrl());
        Object agent;
        try 
        {
            startTask(true);
        	
	        if ( mainAgent)
	        {
	            try {
	                targetHost = um.getServerURL().getHost();
	            } catch (Exception e) {
	                targetHost = "";
	            }
	            // Eliminar el dominio
	            if (targetHost.indexOf(".") > 0)
	                targetHost = targetHost.substring(0, targetHost.indexOf("."));
	            
	        }
	        
	        String phase = "connecting to";
	        if ("local".equals(getDispatcher().getUrl())) {
	            try {
	                AgentManager am = ServerServiceLocator.instance().getAgentManager();
	               	phase = "configuring";
	                agent = am.createLocalAgent(getDispatcher());
	            } catch (Exception e) {
	                throw new InternalErrorException(String.format("Error %s %s", phase,
	                        getDispatcher().getUrl()), e);
	            }
	            log.info("Instantiated in memory", null, null);
	        } else {
	            try {
	                RemoteServiceLocator rsl = new RemoteServiceLocator();
	                rsl.setServer(getDispatcher().getUrl().toString());
	                AgentManager am = rsl.getAgentManager();
	                phase = "configuring";
	                String agenturl = am.createAgent(getDispatcher());
	                agent = rsl.getRemoteService(agenturl);
	            } catch (Exception e) {
	                throw new InternalErrorException(String.format("Error %s agent", phase,
	                        getDispatcher().getUrl()), e);
	            }
	            log.info("Connected", null, null);
	        }
	        if (agent instanceof ExtensibleObjectMgr)
	        {
	        	((ExtensibleObjectMgr) agent).configureMappings(attributeTranslator.getObjects());
	        }
	        if (mainAgent)
	        {
	            if (agent instanceof AgentInterface)
	            	agentVersion = ((AgentInterface)agent).getAgentVersion();
	            else
	            	agentVersion = "Unknown";
	            objectClass = agent.getClass();
	            lastConnect = new java.util.Date().getTime();
	            if (agent instanceof KerberosAgent) {
	            	this.agent = agent;
	                String domain = ((KerberosAgent) agent).getRealmName();
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
    private Usuari getTaskUser(TaskHandler task) throws InternalErrorException {
        Usuari user = task.getUsuari();
        if (user != null)
            return user;
        try {
        	if (task.getTask().getTransa().equals (TaskHandler.PROPAGATE_ACCOUNT_PASSWORD) ||
        			task.getTask().getTransa().equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD) ||
        			task.getTask().getTransa().equals(TaskHandler.VALIDATE_ACCOUNT_PASSWORD) ||
        			task.getTask().getTransa().equals(TaskHandler.UPDATE_ACCOUNT))
        	{
	            user = server.getUserInfo(task.getTask().getUsuari(), getDispatcher().getCodi());
        	}
        	else
        	{
	            user = server.getUserInfo(task.getTask().getUsuari(), null);
        	}
            task.setUsuari(user);
        } catch (UnknownUserException e) {
            user = null;
        }
        return user;
    }

    private Usuari getUserInfo(TaskHandler t) throws InternalErrorException, UnknownUserException {
        return server.getUserInfo(t.getTask().getUsuari(), null);
    }

    private Collection<UserAccount> getAccounts(TaskHandler task) throws InternalErrorException {
        Usuari usuari = getTaskUser(task);
        if (usuari == null)
            return Collections.EMPTY_LIST;
        return server.getUserAccounts(usuari.getId(), getDispatcher().getCodi());
    }

    @Override
    public KerberosAgent getKerberosAgent() {
        if (agent != null && agent instanceof KerberosAgent)
            return (KerberosAgent) agent;
        else
            return null;
    }

    public Date getCertificateNotValidAfter() {
        if (isConnected())
            try {
                AgentManager agentMgr;
                if (getDispatcher().equals("local")) {
                    agentMgr = ServerServiceLocator.instance().getAgentManager();
                    return null;
                } else {
                    RemoteServiceLocator locator = new RemoteServiceLocator(getDispatcher()
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
		TasqueEntity taskEntity;				// Task entity
		ReconcileMgr reconcileManager = null;	// Reconcile manager
		ReconcileMgr2 reconcileManager2 = null;	// Reconcile manager

		try
		{
			if (agent instanceof ReconcileMgr)
				reconcileManager = (ReconcileMgr) agent;
			else if (agent instanceof ReconcileMgr2)
				reconcileManager2 = (ReconcileMgr2) agent;
			else
				return;
		}
		catch (ClassCastException ex)
		{
			return;
		}

		// Create reconcile user task for users
		for (String user : (reconcileManager2 == null ? reconcileManager.getAccountsList() : reconcileManager2.getAccountsList()))
		{
			// Check user code length
			if (user.length() <= MAX_LENGTH)
			{
				taskEntity = tasqueEntityDao.newTasqueEntity();
				taskEntity.setTransa(TaskHandler.RECONCILE_USER);
				taskEntity.setData(new Timestamp(System.currentTimeMillis()));
				taskEntity.setMaquin(taskHandler.getTask().getMaquin());
				taskEntity.setUsuari(user);
				taskEntity.setCoddis(getDispatcher().getCodi());
				
				taskqueue.addTask(taskEntity);
			}
		}

		// Create reconcile roles task
		taskEntity = tasqueEntityDao.newTasqueEntity();
		taskEntity.setTransa(TaskHandler.RECONCILE_ROLES);
		taskEntity.setData(new Timestamp(System.currentTimeMillis()));
		taskEntity.setMaquin(taskHandler.getTask().getMaquin());
		taskEntity.setCoddis(getDispatcher().getCodi());

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

		Long processId = Long.decode(taskHandler.getTask().getMaquin());
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
		if (agent instanceof ReconcileMgr)
			reconcileUser((ReconcileMgr) agent, taskHandler);
		else if (agent instanceof ReconcileMgr2)
			reconcileAccount((ReconcileMgr2) agent, taskHandler);
		else
			return;
	}
	
	public void reconcileUser (ReconcileMgr reconcileManager, TaskHandler taskHandler)
			throws InternalErrorException, RemoteException
	{
		Usuari user; // User to reconcile
		ReconcileAccount reconcileAccount = null; // Reconcile accounts handler
		ReconcileAssignment reconcileAssign = null; // Reconcile assignments handler

		try
		{
		}
		catch (ClassCastException e)
		{
			return;
		}

		String accountName = taskHandler.getTask().getUsuari();
		// Check existing user on system
		Account account = accountService.findAccount(accountName,
						getDispatcher().getCodi());

		Collection<RolGrant> grants = new LinkedList<RolGrant>();
		
		if (account == null)
		{

			user = reconcileManager.getUserInfo(taskHandler.getTask().getUsuari());

			// Check correct user
			if (user != null && user.getCodi() != null)
			{
				// Set user parameters
				reconcileAccount = new ReconcileAccount();
				reconcileAccount.setAccountName(user.getCodi());
				if (user.getFullName() == null)
					reconcileAccount.setDescription(user.getNom() + " "
									+ user.getPrimerLlinatge());
				else
					reconcileAccount.setDescription(user.getFullName());
				reconcileAccount.setProcessId(Long.parseLong(taskHandler.getTask()
								.getMaquin()));
				reconcileAccount.setProposedAction(AccountProposedAction.CREATE_NEW_USER);
				reconcileAccount.setDispatcher(getDispatcher().getCodi());
				reconcileService.addUser(reconcileAccount);
			}
		}
		else
			grants = server.getAccountRoles(accountName, getDispatcher().getCodi());
		
		for (Rol role : reconcileManager.getAccountRoles(taskHandler.getTask().getUsuari()))
		{
			if (role.getNom().length() <= MAX_LENGTH)
			{
				boolean found = false;
				for (RolGrant rg: grants)
				{
					if (rg.getRolName().equals (role.getNom()) && rg.getDispatcher().equals (getDispatcher().getCodi()))
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
									+ role.getNom());
					reconcileAssign.setProcessId(Long.parseLong(taskHandler
									.getTask().getMaquin()));
					reconcileAssign.setProposedAction(ProposedAction.LOAD);
					reconcileAssign.setRoleName(role.getNom());
					reconcileAssign.setDispatcher(getDispatcher().getCodi());
	
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

		try
		{
		}
		catch (ClassCastException e)
		{
			return;
		}

		String accountName = taskHandler.getTask().getUsuari();
		// Check existing user on system
		Account account = accountService.findAccount(accountName,
						getDispatcher().getCodi());

		Collection<RolGrant> grants = new LinkedList<RolGrant>();
		
		if (account == null)
		{

			user = reconcileManager.getAccountInfo(taskHandler.getTask().getUsuari());

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
								.getMaquin()));
				reconcileAccount.setProposedAction(AccountProposedAction.CREATE_NEW_USER);
				reconcileAccount.setDispatcher(getDispatcher().getCodi());
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
			user = reconcileManager.getAccountInfo(taskHandler.getTask().getUsuari());
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
								.getMaquin()));
				reconcileAccount.setProposedAction(AccountProposedAction.UPDATE_ACCOUNT);
				reconcileAccount.setDispatcher(getDispatcher().getCodi());
				reconcileAccount.setAttributes(user.getAttributes());
				reconcileAccount.setNewAccount(Boolean.FALSE);
				reconcileAccount.setDeletedAccount(Boolean.FALSE);
				reconcileAccount.setAttributes(new HashMap<String, Object>());
				if (user.getAttributes() != null)
					reconcileAccount.getAttributes().putAll(user.getAttributes());
				reconcileService.addUser(reconcileAccount);				
			}
			grants = server.getAccountRoles(accountName, getDispatcher().getCodi());
		}
		
		for (RolGrant role : reconcileManager.getAccountGrants(taskHandler.getTask().getUsuari()))
		{
			if (role.getRolName().length() <= MAX_LENGTH)
			{
				boolean found = false;
				for (RolGrant rg: grants)
				{
					if (rg.getRolName().equals (role.getRolName()) && rg.getDispatcher().equals (getDispatcher().getCodi()))
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
									+ role.getRolName());
					reconcileAssign.setProcessId(Long.parseLong(taskHandler
									.getTask().getMaquin()));
					reconcileAssign.setProposedAction(ProposedAction.LOAD);
					reconcileAssign.setRoleName(role.getRolName());
					reconcileAssign.setDispatcher(getDispatcher().getCodi());
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
		TasqueEntity taskEntity;		// Task entity
		ReconcileMgr reconcileManager = agent instanceof ReconcileMgr ? (ReconcileMgr) agent: null;	// Reconcile manager
		ReconcileMgr2 reconcileManager2 = agent instanceof ReconcileMgr2 ? (ReconcileMgr2) agent: null;	// Reconcile manager

		if (reconcileManager == null && reconcileManager2 == null)
			return;
		
		List<String> roles = reconcileManager == null ? reconcileManager2.getRolesList() : reconcileManager.getRolesList();
		// Create reconcile role task
		for (String role : roles)
		{
			// Check role code length
			if (role.length() <= MAX_ROLE_CODE_LENGTH)
			{
				taskEntity = tasqueEntityDao.newTasqueEntity();
				
				taskEntity.setTransa(TaskHandler.RECONCILE_ROLE);
				taskEntity.setData(new Timestamp(System.currentTimeMillis()));
				taskEntity.setMaquin(taskHandler.getTask().getMaquin());
				taskEntity.setRole(role);
				taskEntity.setBd(getDispatcher().getCodi());
				taskEntity.setCoddis(getDispatcher().getCodi());
				
				taskqueue.addTask(taskEntity);
			}
		}

		// Create reconcile roles task
		taskEntity = tasqueEntityDao.newTasqueEntity();
		taskEntity.setTransa(TaskHandler.END_RECONCILE);
		taskEntity.setData(new Timestamp(System.currentTimeMillis()));
		taskEntity.setMaquin(taskHandler.getTask().getMaquin());
		taskEntity.setCoddis(getDispatcher().getCodi());

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
		Rol role;						// Role to reconcile
		ReconcileMgr reconMgr = agent instanceof ReconcileMgr ? (ReconcileMgr) agent : null;	// Reconcile manager
		ReconcileMgr2 reconMgr2 = agent instanceof ReconcileMgr2 ? (ReconcileMgr2) agent : null;	// Manage reconcile process
		ReconcileRole reconRole;

		try
		{
			if (taskHandler.getTask().getRole() != null
							&& taskHandler.getTask().getRole().length() > 0)
			{
				role = server.getRoleInfo(taskHandler.getTask().getRole(), taskHandler
								.getTask().getBd());

				// Check not existing role
				if (role == null)
				{
					role = reconMgr != null ? reconMgr.getRoleFullInfo(taskHandler.getTask().getRole())
							: reconMgr2 != null? reconMgr2.getRoleFullInfo(taskHandler.getTask().getRole())
							: null;

					if (role != null)
					{
						reconRole = new ReconcileRole();
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
	
						reconRole.setProcessId(Long.parseLong(taskHandler.getTask()
										.getMaquin()));
						reconRole.setProposedAction(ProposedAction.LOAD);
						reconRole.setDispatcher(getDispatcher().getCodi());
	
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
    		if (agent instanceof ReconcileMgr)
    		{
    			new ReconcileEngine (getDispatcher(), (ReconcileMgr) agent).reconcile();
    		} 
    		else if (agent instanceof ReconcileMgr2)
        	{
        		new ReconcileEngine2 (getDispatcher(), (ReconcileMgr2) agent, result).reconcile();
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
				SoffidStackTrace.printStackTrace(e, new PrintStream(out, true, "UTF-8"));
				result.append (out.toString("UTF-8"));
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
			Watchdog.instance().interruptMe(getDispatcher().getLongTimeout());
		else 
			Watchdog.instance().interruptMe(getDispatcher().getTimeout());
	}

	private void endTask () {
		Watchdog.instance().dontDisturb();
	}

}

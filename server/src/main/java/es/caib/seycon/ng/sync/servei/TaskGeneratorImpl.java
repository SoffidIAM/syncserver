package es.caib.seycon.ng.sync.servei;

import com.soffid.iam.model.Parameter;
import com.soffid.iam.model.ReplicaDatabaseEntity;
import com.soffid.iam.model.SystemEntity;
import com.soffid.iam.model.TaskEntity;
import com.soffid.iam.model.criteria.CriteriaSearchConfiguration;
import com.soffid.tools.db.persistence.XmlReader;
import com.soffid.tools.db.persistence.XmlWriter;
import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Database;
import com.soffid.tools.db.schema.Table;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.DispatcherHandlerImpl;
import es.caib.seycon.ng.sync.engine.ReplicaConnection;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.replica.DatabaseRepository;
import es.caib.seycon.ng.sync.servei.TaskGeneratorBase;
import es.caib.seycon.ng.sync.servei.TaskQueue;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

public class TaskGeneratorImpl extends TaskGeneratorBase implements ApplicationContextAware {

    org.slf4j.Logger log = LoggerFactory.getLogger("TaskGenerator");
    boolean firstRun;
    boolean firstOfflineRun;
    boolean active;
    Config config;
    ArrayList<DispatcherHandlerImpl> dispatchers = new ArrayList<DispatcherHandlerImpl>();
    Map<String,DispatcherHandlerImpl> dispatchersMap = new HashMap<String, DispatcherHandlerImpl>();
    private boolean logCollectorEnabled;
    String status = null;
    private Object logCollectorLock;
    private String logCollector;
	private long memoryLimit;

    public TaskGeneratorImpl() throws FileNotFoundException, IOException, InternalErrorException {
        config = Config.getConfig();
        firstRun = true;
        firstOfflineRun = true;
        active = false;
        logCollectorEnabled = false;
        logCollectorLock = new Object();
        logCollector = null;
        memoryLimit = Runtime.getRuntime().maxMemory() * 97 / 100;
    }

    @Override
    protected String handleGetStatus() throws Exception {
        return status;
    }

    @Override
    protected void handleLoadTasks() throws Exception {
        Collection<TaskEntity> tasks;
        Connection connection = ConnectionPool.getPool().getPoolConnection();
        Runtime runtime = Runtime.getRuntime();
        if (runtime.totalMemory() - runtime.freeMemory() > memoryLimit)
        	runtime.gc();
        if (runtime.totalMemory() - runtime.freeMemory() > memoryLimit)
        {
        	log.warn("Free Memory too low. New tasks are not being scheduled");
        	return;
        }
        try {
        	CriteriaSearchConfiguration csc = new CriteriaSearchConfiguration();
        	csc.setMaximumResultSize(5000);
        	if (((ReplicaConnection)connection).isMainDatabase())
        	{
                log.info("Looking for new tasks to schedule");
                if (firstRun) {
                    tasks = getTaskEntityDao().query("select tasca from es.caib.seycon.ng.model.TasqueEntity tasca where tasca.server = :server order by tasca.id", new Parameter[]{new Parameter("server", config.getHostName())});
                } else {
                    tasks = getTaskEntityDao().query("select tasca from es.caib.seycon.ng.model.TasqueEntity tasca where tasca.server is null order by tasca.prioritat, tasca.id", new Parameter[0], csc);
                }
        	} else {
                if (firstOfflineRun) {
                    log.info("Looking for offline tasks");
                    firstOfflineRun = false;
                    tasks = getTaskEntityDao().query("select tasca from es.caib.seycon.ng.model.TasqueEntity tasca where tasca.server = :server order by tasca.id", new Parameter[]{new Parameter("server", config.getHostName())});
                } else {
                	tasks = Collections.emptyList();
                }
        	}
            TaskQueue taskQueue = getTaskQueue();
            int i = 0;
            flushAndClearSession();
            for (Iterator<TaskEntity> it = tasks.iterator(); active && it.hasNext(); ) {
                TaskEntity tasca = it.next();
                taskQueue.addTask(tasca);
                flushAndClearSession();
                if (runtime.totalMemory() - runtime.freeMemory() > memoryLimit && !firstRun) {
                    runtime.gc();
                    return;
                }
            }
        	if (((ReplicaConnection)connection).isMainDatabase())
        		firstRun = false;
        } finally {
        	connection.close ();
        }
    }

    @Override
    protected boolean handleCanGetLog(DispatcherHandler td) throws Exception {
        boolean result;
        if (!logCollectorEnabled)
            return false;
        synchronized (logCollectorLock) {
            if (logCollector == null) {
                result = true;
                logCollector = td.getDispatcher().getCodi();
            } else
                result = false;
        }
        return result;
    }

    @Override
    protected void handleFinishGetLog(DispatcherHandler td) throws Exception {
        synchronized (logCollectorLock) {
            // ServerApplication.out.println ("Logs recuperados");
            logCollector = null;
        }
    }

    @Override
    protected void handleShutDown() throws Exception {
    }

    @Override
    protected boolean handleIsEnabled() throws Exception {
        return active;
    }

    @Override
    protected Collection<DispatcherHandler> handleGetDispatchers() throws Exception {
        ArrayList<DispatcherHandler> list = new ArrayList<DispatcherHandler>();
        list.addAll(dispatchers);
        return list;
    }

    @Override
    protected void handleUpdateAgents() throws Exception {
        log.info("Looking for agent updates", null, null);
        ArrayList<DispatcherHandlerImpl> oldDispatchers = new ArrayList<DispatcherHandlerImpl>(
                dispatchers);
        Collection<SystemEntity> entities = getSystemEntityDao().loadAll();
        // Reconfigurar dispatcher modificats
        for (Iterator<SystemEntity> it = entities.iterator(); it.hasNext(); ) {
            SystemEntity dispatcherEntity = it.next();
            if (dispatcherEntity.getUrl() == null || dispatcherEntity.getUrl().isEmpty()) {
                it.remove();
            } else {
                for (Iterator<DispatcherHandlerImpl> itOld = oldDispatchers.iterator(); itOld.hasNext(); ) {
                    DispatcherHandlerImpl oldHandler = itOld.next();
                    if (oldHandler.getDispatcher().getId().equals(dispatcherEntity.getId()) && oldHandler.isActive()) {
                        itOld.remove();
                        it.remove();
                        DispatcherHandlerImpl current = dispatchers.get(oldHandler.getInternalId());
                        Dispatcher newDispatcher = getSystemEntityDao().toDispatcher(dispatcherEntity);
                        populateReplicaAttributes(dispatcherEntity, newDispatcher);
                        checkNulls(newDispatcher);
                        Dispatcher oldDispatcher = current.getDispatcher();
                        if (!oldDispatcher.getTimeStamp().equals(newDispatcher.getTimeStamp())) {
                            current.reconfigure(newDispatcher);
                        }
                        break;
                    }
                }
            }
        }

        // Aturar dispatcher eliminats
        for (Iterator<DispatcherHandlerImpl> itOld = oldDispatchers.iterator(); itOld.hasNext();) {
            DispatcherHandlerImpl oldHandler = itOld.next();
            if (oldHandler.isActive())
            {
                oldHandler.gracefullyStop();
            }
        }

        // Instanciar nous dispatchers
        for (Iterator<SystemEntity> it = entities.iterator(); it.hasNext(); ) {
            SystemEntity entity = it.next();
            DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
            int id = dispatchers.size();
            Dispatcher dis = getSystemEntityDao().toDispatcher(entity);
            populateReplicaAttributes(entity, dis);
            checkNulls(dis);
            handler.setDispatcher(dis);
            handler.setInternalId(id);
            dispatchers.add(handler);
            dispatchersMap.put(dis.getCodi(), handler);
            handler.start();
        }

    }

    /**
	 * @param entity
	 * @param dis
     * @throws Exception 
     * @throws IOException 
	 */
	private void populateReplicaAttributes(SystemEntity entity, Dispatcher dis) throws IOException, Exception {

		if (! entity.getReplicaDatabases().isEmpty())
		{
			ReplicaDatabaseEntity replica = entity.getReplicaDatabases().iterator().next();
			dis.setParam0(replica.getUserName());
			dis.setParam1(replica.getPassword());
			dis.setParam2(replica.getUrl());
			DatabaseRepository db = new DatabaseRepository();
			dis.setParam3(db.getSchema());
			dis.setParam4(replica.getIdSeed().toString());
		}
	}

	private WeakReference<String> currentSchema = new WeakReference<String>(null);
	private ApplicationContext applicationContext;
	private SessionFactory sessionFactory;

	private void checkNulls(Dispatcher newDispatcher) {
        if (newDispatcher.getBasRol() == null)
            newDispatcher.setBasRol(new Boolean(false));
        if (newDispatcher.getControlAccess() == null)
            newDispatcher.setControlAccess(new Boolean(false));
        if (newDispatcher.getSegur() == null)
            newDispatcher.setSegur(new Boolean(false));
        if (newDispatcher.getDominiContrasenyes() == null)
            newDispatcher.setDominiContrasenyes("");
        if (newDispatcher.getDominiUsuaris() == null)
            newDispatcher.setDominiUsuaris("");
        if (newDispatcher.getGrups() == null)
            newDispatcher.setGrups("");
        if (newDispatcher.getIdDominiContrasenyes() == null)
            newDispatcher.setIdDominiContrasenyes(new Long(-1));
        if (newDispatcher.getNomCla() == null)
            newDispatcher.setNomCla("");
        if (newDispatcher.getParam0() == null)
            newDispatcher.setParam0("");
        if (newDispatcher.getParam1() == null)
            newDispatcher.setParam1("");
        if (newDispatcher.getParam2() == null)
            newDispatcher.setParam2("");
        if (newDispatcher.getParam3() == null)
            newDispatcher.setParam3("");
        if (newDispatcher.getParam4() == null)
            newDispatcher.setParam4("");
        if (newDispatcher.getParam5() == null)
            newDispatcher.setParam5("");
        if (newDispatcher.getParam6() == null)
            newDispatcher.setParam6("");
        if (newDispatcher.getParam7() == null)
            newDispatcher.setParam7("");
        if (newDispatcher.getParam8() == null)
            newDispatcher.setParam8("");
        if (newDispatcher.getParam9() == null)
            newDispatcher.setParam9("");
        if (newDispatcher.getRelacioLaboral() == null)
            newDispatcher.setRelacioLaboral("");
    }

    @Override
    protected void handleSetEnabled(boolean enabled) throws Exception {
        active = enabled;
        if (config.getSeyconServerHostList().length == 1 || !config.isMainServer()) {
            logCollectorEnabled = true;
        }
    }

	@Override
	protected DispatcherHandler handleGetDispatcher(String id) throws Exception {
		return dispatchersMap.get(id);
	}
	
	
	private void flushAndClearSession ()
	{
		Session session = SessionFactoryUtils.getSession(getSessionFactory(), false) ;
		session.flush();
		session.clear();

	}
	private SessionFactory getSessionFactory ()
	{
		if (sessionFactory == null)
			sessionFactory = (SessionFactory) applicationContext.getBean("sessionFactory");
		return sessionFactory;
	}

	/* (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	public void setApplicationContext (ApplicationContext applicationContext)
					throws BeansException
	{
		this.applicationContext = applicationContext; 
	}
}

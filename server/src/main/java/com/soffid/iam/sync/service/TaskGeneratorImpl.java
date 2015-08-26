package com.soffid.iam.sync.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
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

import com.soffid.iam.api.System;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.Parameter;
import com.soffid.iam.model.SystemEntity;
import com.soffid.iam.model.TaskEntity;
import com.soffid.iam.model.criteria.CriteriaSearchConfiguration;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.DispatcherHandlerImpl;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.TaskGeneratorBase;
import com.soffid.iam.sync.service.TaskQueue;

import es.caib.seycon.ng.exception.InternalErrorException;

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
	
	SharedThreadPool threadPool = new SharedThreadPool();

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
            log.info("Looking for new tasks to schedule");
            if (firstRun) {
                tasks = getTaskEntityDao().query("select tasca from com.soffid.iam.model.TaskEntity tasca "
                		+ "where tasca.server = :server "
                		+ "order by tasca.id", 
                		new Parameter[]{new Parameter("server", config.getHostName())});
            } else {
                tasks = getTaskEntityDao().query("select tasca from com.soffid.iam.model.TaskEntity tasca "
                		+ "where tasca.server is null "
                		+ "order by tasca.priority, tasca.id", new Parameter[0], csc);
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
                logCollector = td.getSystem().getName();
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
    	boolean anySharedThreadChange = false;
    	
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
                    if (oldHandler.getSystem().getId().equals(dispatcherEntity.getId()) && oldHandler.isActive()) {
                        itOld.remove();
                        it.remove();
                        DispatcherHandlerImpl current = dispatchers.get(oldHandler.getInternalId());
                        System newDispatcher = getSystemEntityDao().toSystem(dispatcherEntity);
                        checkNulls(newDispatcher);
                        System oldDispatcher = current.getSystem();
                        if (!oldDispatcher.getTimeStamp().equals(newDispatcher.getTimeStamp())) 
                        {// S'han produït canvis
                        	boolean oldThread = oldDispatcher.getSharedDispatcher() != null && oldDispatcher.getSharedDispatcher().booleanValue();
                        	boolean newThread = newDispatcher.getSharedDispatcher() != null && newDispatcher.getSharedDispatcher().booleanValue();
                        	if (oldThread && newThread || ! oldThread && ! newThread)
                        		current.reconfigure(newDispatcher);
                        	else 
                        	{
                        		
                       			current.gracefullyStop();
                                DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
                                int id = current.getInternalId();
                                checkNulls(newDispatcher);
                                handler.setSystem(newDispatcher);
                                handler.setInternalId(id);
                                dispatchers.remove(oldHandler);
                                dispatchers.add(handler);
                                dispatchersMap.put(newDispatcher.getName(), handler);
                                if (newThread)
                                	handler.start();
                                anySharedThreadChange = true;
                        	}
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
            System dis = getSystemEntityDao().toSystem(entity);
            checkNulls(dis);
            handler.setSystem(dis);
            handler.setInternalId(id);
            dispatchers.add(handler);
            dispatchersMap.put(dis.getName(), handler);
            if (dis.getSharedDispatcher() == null || ! dis.getSharedDispatcher().booleanValue())
            	handler.start();
            else
                anySharedThreadChange = true;
        }

        if (anySharedThreadChange)
        	threadPool.updateThreads(dispatchers);
    }

	private WeakReference<String> currentSchema = new WeakReference<String>(null);
	private ApplicationContext applicationContext;
	private SessionFactory sessionFactory;

	private void checkNulls(com.soffid.iam.api.System newDispatcher) {
        if (newDispatcher.getRolebased() == null)
            newDispatcher.setRolebased(new Boolean(false));
        if (newDispatcher.getAccessControl() == null)
            newDispatcher.setAccessControl(new Boolean(false));
        if (newDispatcher.getTrusted() == null)
            newDispatcher.setTrusted(new Boolean(false));
        if (newDispatcher.getPasswordsDomain() == null)
            newDispatcher.setPasswordsDomain("");
        if (newDispatcher.getUsersDomain() == null)
            newDispatcher.setUsersDomain("");
        if (newDispatcher.getGroups() == null)
            newDispatcher.setGroups("");
        if (newDispatcher.getPasswordsDomainId() == null)
            newDispatcher.setPasswordsDomainId(new Long(-1));
        if (newDispatcher.getClassName() == null)
            newDispatcher.setClassName("");
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
        if (newDispatcher.getUserTypes() == null)
            newDispatcher.setUserTypes("");
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

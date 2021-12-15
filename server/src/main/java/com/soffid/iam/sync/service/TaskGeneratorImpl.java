package com.soffid.iam.sync.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.orm.hibernate3.SessionFactoryUtils;
import org.springframework.transaction.annotation.Transactional;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Configuration;
import com.soffid.iam.api.System;
import com.soffid.iam.api.Task;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.Parameter;
import com.soffid.iam.model.SystemEntity;
import com.soffid.iam.model.TaskEntity;
import com.soffid.iam.model.TenantEntity;
import com.soffid.iam.model.criteria.CriteriaSearchConfiguration;
import com.soffid.iam.service.ConfigurationService;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.DispatcherHandlerImpl;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.TaskGeneratorBase;
import com.soffid.iam.sync.service.TaskQueue;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;

public class TaskGeneratorImpl extends TaskGeneratorBase implements ApplicationContextAware {

    org.slf4j.Logger log = LoggerFactory.getLogger("TaskGenerator");
    boolean firstRun;
    boolean firstOfflineRun;
    boolean active;
    Config config;
    
    ArrayList<DispatcherHandlerImpl> dispatchers = new ArrayList<DispatcherHandlerImpl>();
    HashSet<Long> activeTenants = new HashSet<Long>();
    
    Map<String,Map<String,DispatcherHandlerImpl>> dispatchersMap = new HashMap<String, Map<String, DispatcherHandlerImpl>>();
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
        memoryLimit = Runtime.getRuntime().maxMemory() * 90 / 100;
    }

    @Override
    protected String handleGetStatus() throws Exception {
        return status;
    }

    @Override
    protected void handleLoadTasks() throws Exception {
        Collection<TaskEntity> tasks;
        Runtime runtime = Runtime.getRuntime();
        if (runtime.totalMemory() - runtime.freeMemory() > memoryLimit)
        	runtime.gc();
        if (runtime.totalMemory() - runtime.freeMemory() > memoryLimit)
        {
        	log.warn("Free Memory too low. New tasks are not being scheduled");
        	return;
        }
    	CriteriaSearchConfiguration csc = new CriteriaSearchConfiguration();
    	csc.setMaximumResultSize(5000);
        log.info("Looking for new tasks to schedule");
        if (firstRun) {
            tasks = getTaskEntityDao().query("select distinct tasca "
            		+ "from com.soffid.iam.model.TaskEntity as tasca "
            		+ "left join tasca.tenant as tenant "
            		+ "where tasca.server = :server and "
            		+ "tenant.enabled=:true "
            		+ "order by tasca.id", 
            		new Parameter[]{
            				new Parameter("server", config.getHostName()),
            				new Parameter("true", true)});
        } else if (isMainServer()) {
            tasks = getTaskEntityDao().query("select distinct tasca "
            		+ "from com.soffid.iam.model.TaskEntity as tasca "
            		+ "left join tasca.tenant as tenant "
            		+ "left join tenant.servers as servers "
            		+ "left join servers.tenantServer as server "
            		+ "where (tasca.server is null or tasca.server=:server) and server.name=:server "
            		+ "and tasca.status='P'  AND tasca.hash is null and tenant.enabled=:true "
            		+ "order by tasca.priority, tasca.id",
            		new Parameter[]{
            				new Parameter("server", config.getHostName()),
            				new Parameter("true", true)},
            		csc);
        } else {
            tasks = getTaskEntityDao().query("select distinct tasca "
            		+ "from com.soffid.iam.model.TaskEntity as tasca "
            		+ "left join tasca.tenant as tenant "
            		+ "left join tenant.servers as servers "
            		+ "left join servers.tenantServer as server "
            		+ "where tasca.server=:server and server.name=:server "
            		+ "and   tasca.status='P' and tasca.hash is null "
            		+ "and   tenant.enabled=:true "
            		+ "order by tasca.priority, tasca.id",
            		new Parameter[]{
            				new Parameter("server", config.getHostName()),
            				new Parameter("true", true)},
            		csc);
        }
        TaskQueue taskQueue = getTaskQueue();
        int i = 0;
        flushAndClearSession();
        long lastUpdate = java.lang.System.currentTimeMillis();
        for (Iterator<TaskEntity> it = tasks.iterator(); active && it.hasNext(); ) {
            TaskEntity tasca = it.next();
            if ( activeTenants.contains( tasca.getTenant().getId()))
            {
	            taskQueue.addTask(tasca);
	            flushAndClearSession();
	            if (runtime.maxMemory() - runtime.freeMemory() > memoryLimit && !firstRun) {
	                runtime.gc();
	                return;
	            }
            } else {
            	if (tasca.getServer() != null)
            	{
            		tasca.setServer(null);
            		getTaskEntityDao().update(tasca);
            	}
            }
            if (java.lang.System.currentTimeMillis() - lastUpdate > 60000) {
            	lastUpdate = java.lang.System.currentTimeMillis();
                getSyncServerService().updatePendingTasks();
            }
        }
        getSyncServerService().updatePendingTasks();
   		firstRun = false;
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
    	String currentTenant = Security.getCurrentTenantName();
        LinkedList<DispatcherHandler> list = new LinkedList<DispatcherHandler>();
        for (Iterator<DispatcherHandlerImpl> it = dispatchers.iterator(); it.hasNext();)
        {
        	DispatcherHandler d = it.next();
        	if (currentTenant.equals(d.getSystem().getTenant()))
        		list.add(d);
        }
        return list;
    }

    @Override
    protected Collection<DispatcherHandler> handleGetAllTenantsDispatchers() throws Exception {
        LinkedList<DispatcherHandler> list = new LinkedList<DispatcherHandler>();
        list.addAll(dispatchers);
        return list;
    }

    @Override
    protected synchronized void handleUpdateAgents() throws Exception {
    	boolean anySharedThreadChange = false;
    	
        log.info("Looking for agent updates", null, null);
        ArrayList<DispatcherHandlerImpl> oldDispatchers = new ArrayList<DispatcherHandlerImpl>(
                dispatchers);
        Collection<SystemEntity> entities = new HashSet<SystemEntity>( getSystemEntityDao().findServerTenants( config.getHostName()) );
        
        // Reconfigurar dispatcher modificats
        for (Iterator<SystemEntity> it = entities.iterator(); it.hasNext(); ) {
            SystemEntity dispatcherEntity = it.next();
            if (dispatcherEntity.getUrl() == null || dispatcherEntity.getUrl().isEmpty() || ! dispatcherEntity.getTenant().isEnabled()) {
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
                        if (oldDispatcher.getTimeStamp() == null && newDispatcher.getTimeStamp() != null ||
                        	!oldDispatcher.getTimeStamp().equals(newDispatcher.getTimeStamp())) 
                        {// S'han produït canvis
                        	
                        	Security.nestedLogin(current.getSystem().getTenant(), 
                        			current.getSystem().getName(), 
                        			Security.ALL_PERMISSIONS);
                        	try
                        	{
	                        	
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
	                                Map<String, DispatcherHandlerImpl> dm = getDispatchersMap ( newDispatcher.getTenant());
	                                dm.put(newDispatcher.getName(), handler);
	                                if (newThread)
	                                	handler.start();
	                                anySharedThreadChange = true;
	                        	}
                            } finally {
                            	Security.nestedLogoff();
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
            	Security.nestedLogin(oldHandler.getSystem().getTenant(), 
            			"TaskGenerator", 
            			Security.ALL_PERMISSIONS);
            	try
            	{
	                oldHandler.gracefullyStop();
	                do {
	                	TaskHandler task = getTaskQueue().getPendingTask(oldHandler);
	                	if (task == null)
	                		break;
	                	getTaskQueue().notifyTaskStatus(task, oldHandler, true, null, null);
	                } while (true);
                } finally {
                	Security.nestedLogoff();
                }
            }
        }

        // Instanciar nous dispatchers
        for (Iterator<SystemEntity> it = entities.iterator(); it.hasNext(); ) {
            SystemEntity entity = it.next();
        	Security.nestedLogin(entity.getTenant().getName(), 
        			entity.getName(), 
        			Security.ALL_PERMISSIONS);
        	try
        	{
	            DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
	            int id = dispatchers.size();
	            System dis = getSystemEntityDao().toSystem(entity);
	            checkNulls(dis);
	            handler.setSystem(dis);
	            handler.setInternalId(id);
	            dispatchers.add(handler);
	            Map<String, DispatcherHandlerImpl> dm = getDispatchersMap ( entity.getTenant().getName());
	            dm.put(dis.getName(), handler);
	            if (dis.getSharedDispatcher() == null || ! dis.getSharedDispatcher().booleanValue())
	            	handler.start();
	            else
	                anySharedThreadChange = true;
            } finally {
            	Security.nestedLogoff();
            }
        }

        if (anySharedThreadChange)
        {
        	threadPool.updateThreads(dispatchers);
        }
        
        // Now, update tenants list
        HashSet<Long> tenants = new HashSet<Long>();
        for (TenantEntity tenant: getTenantEntityDao().findByServer( Config.getConfig().getHostName() ))
        {
        	tenants.add(tenant.getId());
        }
        this.activeTenants = tenants;
    }

    /**
     * Returns de dispatchers for a tenenat
     * 
     * @param tenant
     * @return dispatchers Map
     */
	private Map<String, DispatcherHandlerImpl> getDispatchersMap(String tenant) {
		Map<String, DispatcherHandlerImpl> dm = dispatchersMap.get(tenant);
		if (dm == null)
		{
			dm = new HashMap<String, DispatcherHandlerImpl>();
			dispatchersMap.put(tenant, dm);
		}
		return dm;
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
		return getDispatchersMap(Security.getCurrentTenantName()).get(id);
	}
	
	
	private void flushAndClearSession ()
	{
		Session session = SessionFactoryUtils.getSession(getSessionFactory(), false) ;
		session.flush();
		session.clear();
		SessionFactoryUtils.getSession(getSessionFactory(), true) ;
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

	@Override
	protected Set<Long> handleGetActiveTenants() throws Exception {
		return activeTenants;
	}

	public void handleFinishVirtualSourceTransaction(String virtualTransactionId)
			throws InternalErrorException, InternalErrorException {
		getTaskEntityDao().finishVirtualSourceTransaction(virtualTransactionId);
	}

	public String handleStartVirtualSourceTransaction() throws InternalErrorException, InternalErrorException {
		return getTaskEntityDao().startVirtualSourceTransaction();
	}

	@Override
	protected String handleStartVirtualSourceTransaction(boolean readonly) throws Exception {
		return getTaskEntityDao().startVirtualSourceTransaction(readonly);
	}

	@Override
	protected void handleUpdateClusterStatus() throws Exception {
		try
		{
			String hostName = Config.getConfig().getHostName();
			long now = java.lang.System.currentTimeMillis();
			long timeout = now - 300000; // 5 minutes ago
			ConfigurationService svc = ServiceLocator.instance().getConfigurationService();
			Configuration cfg = svc.findParameterByNameAndNetworkName("soffid.syncserver.main", null);
			if (cfg == null)
			{
				log.warn("Becoming master server");
				cfg = new Configuration();
				cfg.setCode("soffid.syncserver.main");
				cfg.setValue(hostName+" "+now);
				svc.create(cfg);
			}
			else
			{
				String [] split = cfg.getValue().isEmpty() ? new String[0]: cfg.getValue().split(" ");
				if (split.length != 2 ||
						split[0].equals(hostName) ||
						Long.parseLong(split[1]) < timeout)
				{
					log.warn("Setting as master server: "+hostName);
					cfg.setValue(hostName+" "+now);
					svc.update(cfg);
				}
						
			}
		} catch (Throwable e) {
//			e.printStackTrace();
			log.warn("Error on sync server cluster manager", e);
		}
	}
	
	public boolean isMainServer() throws InternalErrorException, FileNotFoundException, IOException {
		String hostName = Config.getConfig().getHostName();
		ConfigurationService svc = getConfigurationService();
		Configuration cfg = svc.findParameterByNameAndNetworkName("soffid.syncserver.main", null);
		if (cfg == null)
			return false;
		String [] split = cfg.getValue().isEmpty() ? new String[0]: cfg.getValue().split(" ");
		return split.length == 2 && split[0].equals(hostName);
	}
}

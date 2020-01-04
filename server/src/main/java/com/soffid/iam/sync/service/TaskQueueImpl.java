package com.soffid.iam.sync.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.Task;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.AccountEntityDao;
import com.soffid.iam.model.PasswordDomainEntity;
import com.soffid.iam.model.PasswordDomainEntityDao;
import com.soffid.iam.model.SystemEntity;
import com.soffid.iam.model.TaskEntity;
import com.soffid.iam.model.TaskEntityDao;
import com.soffid.iam.model.TaskLogEntity;
import com.soffid.iam.model.TaskLogEntityDao;
import com.soffid.iam.model.UserEntity;
import com.soffid.iam.model.UserEntityDao;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.service.InternalPasswordService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.TaskHandlerLog;
import com.soffid.iam.sync.engine.intf.DebugTaskResults;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownRoleException;

public class TaskQueueImpl extends TaskQueueBase implements ApplicationContextAware
{
	public static final int MAX_PRIORITY = 4;
	/**
	 * Priorized tasks for any tenant
	 */
	Hashtable<Long,PrioritiesList> globalTaskList;
	/**
	 * Current tasks for any tenant, indexed by task hash.
	 * It's used to detect duplicated tasks
	 */
	Map<Long, Hashtable<String, TaskHandler>> globalCurrentTasks;

	String hostname;
	private final Logger log = Log.getLogger("TaskQueue");
	private ApplicationContext applicationContext;

	private Hashtable<String,TaskHandler> getCurrentTasks ()
	{
		Long l = Security.getCurrentTenantId();
		Hashtable<String, TaskHandler> ht = globalCurrentTasks.get(l);
		if (ht == null)
		{
			ht = new Hashtable<String, TaskHandler>();
			globalCurrentTasks.put(l, ht);
		}
		return ht;
	}
	
	/***************************************************************************
	 * Constructor
	 * 
	 * @throws UnknownHostException
	 */
	public TaskQueueImpl ()
	{
		globalCurrentTasks = new Hashtable<Long, Hashtable<String, TaskHandler>>();
		globalTaskList = new Hashtable<Long, PrioritiesList>();
		try
		{
			hostname = Config.getConfig().getHostName();
		}
		catch (IOException e)
		{
			try
			{
				hostname = InetAddress.getLocalHost().getHostName();
			}
			catch (UnknownHostException e1)
			{
				throw new RuntimeException(e1);
			}
		}
	}

	@Override
	protected void handleAddTask (TaskHandler newTask) throws Exception
	{
		newTask.setOfflineTask(false);
	
		Security.nestedLogin(newTask.getTenant(), Config.getConfig().getHostName(), Security.ALL_PERMISSIONS);
		try
		{
			TaskEntity entity = getTaskEntityDao().load(newTask.getTask().getId());
			addTask(newTask, entity);
		} finally {
			Security.nestedLogoff();
		}
	}

	private void addTask(TaskHandler newTask, TaskEntity entity)
			throws InternalErrorException, UnknownGroupException, UnknownRoleException {
		TaskGenerator tg = getTaskGenerator();
		if (entity == null ||
			newTask.getTask().getServer() == null && !tg.isEnabled() ||
			newTask.getTask().getServer() != null &&
			!newTask.getTask().getServer().equals(hostname))
		{
			// Ignorar la transaccion
		}
		else if (newTask.getTask()
				.getTransaction().equals(TaskHandler.UPDATE_USER_PASSWORD) &&
			entity.getHash() == null)
		{
			if (newTask.getPassword() != null)
			{
				InternalPasswordService ps = getInternalPasswordService();
				UserEntityDao usuariDao = getUserEntityDao();
				UserEntity usuari = usuariDao.findByUserName(newTask.getTask().getUser());
				if (usuari == null) // Ignorar
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
					return;
				}
				PasswordDomainEntityDao dcDao = getPasswordDomainEntityDao();
				PasswordDomainEntity dc = dcDao.findByName(newTask.getTask().getPasswordDomain());
				if (dc == null)
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
					return; // Ignorar
				}

				storeDomainPassword (newTask);
				
				addAndNotifyDispatchers(newTask, entity);
			}
			else
			{
				newTask.cancel();
				pushTaskToPersist(newTask);
			}
		}
		else if (newTask.getTask().getTransaction()
						.equals(TaskHandler.UPDATE_PROPAGATED_PASSWORD))
		{
			if (newTask.getPassword() != null)
			{
				InternalPasswordService ps = getInternalPasswordService();
				UserEntityDao usuariDao = getUserEntityDao();
				UserEntity usuari = usuariDao.findByUserName(newTask.getTask().getUser());
				if (usuari == null) // Ignorar
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
					return;
				}
				
				PasswordDomainEntityDao dcDao = getPasswordDomainEntityDao();
				PasswordDomainEntity dc = dcDao.findByName(newTask.getTask().getPasswordDomain());
				if (dc == null)
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
					return; // Ignorar
				}

				if (ps.checkPassword(usuari, dc, newTask.getPassword(),
						false, false) == PasswordValidation.PASSWORD_WRONG)
				{
					ps.storePassword(usuari, dc, newTask.getPassword(), false);

					storeDomainPassword (newTask);

					addAndNotifyDispatchers(newTask, entity);
				}
				else
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
				}
			}
			else
			{
				newTask.cancel();
				pushTaskToPersist(newTask);
			}
		}
		else if (newTask.getTask().getTransaction()
						.equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD))
		{
			if (newTask.getPassword() != null)
			{
				InternalPasswordService ps = getInternalPasswordService();
				AccountEntityDao accDao = getAccountEntityDao();
				AccountEntity account = accDao.findByNameAndSystem(newTask.getTask().getUser(), newTask.getTask().getSystemName());
				if (account == null)
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
					return;
				}

				if (ps.checkAccountPassword(account, newTask.getPassword(),
						false, false) == PasswordValidation.PASSWORD_WRONG)
				{
					ps.storeAccountPassword(account, newTask.getPassword(),
						"S".equalsIgnoreCase(newTask.getTask().getPasswordChange()),
						null);
				}
				// Update for virtual dispatchers
				DispatcherHandler dispatcher =
					getTaskGenerator().getDispatcher(
						newTask.getTask().getSystemName());
				if (dispatcher != null && dispatcher.isActive()) 
				{
					addAndNotifyDispatchers(newTask, entity);
				}
				else
				{
					newTask.cancel();
					pushTaskToPersist(newTask);
					
					storeAccountPassword(newTask, account);
				}
			}
			else
			{
				newTask.cancel();
				pushTaskToPersist(newTask);
			}
		}
		else if (newTask.getTask().getTransaction()
						.equals(TaskHandler.UPDATE_ACCOUNT))
		{
			// Update for virtual dispatchers
			DispatcherHandler dispatcher =
				getTaskGenerator().getDispatcher(newTask.getTask().getSystemName());
			if (dispatcher == null || ! dispatcher.isActive()) 
			{
				newTask.cancel();
				pushTaskToPersist(newTask);
			}
			else
				addAndNotifyDispatchers(newTask, entity);
		}
		else if (newTask.getTask()
					.getTransaction().equals(TaskHandler.PROPAGATE_PASSWORD) ||
				newTask.getTask()
					.getTransaction().equals(TaskHandler.PROPAGATE_ACCOUNT_PASSWORD) ||
				newTask.getTask()
					.getTransaction().equals(TaskHandler.VALIDATE_PASSWORD))
		{
			if (newTask.getPassword() != null)
			{
				addAndNotifyDispatchers(newTask, entity);
			}
			else
			{
				newTask.cancel();
				pushTaskToPersist(newTask);
			}
		}
		else if (newTask.getTask()
					.getTransaction().equals(TaskHandler.UPDATE_USER) )
		{
			getAccountService()
				.generateUserAccounts(newTask.getTask().getUser());
			addAndNotifyDispatchers(newTask, entity);
		}
		
		else if (newTask.getTask().getTransaction()
			.equals(TaskHandler.NOTIFY_PASSWORD_CHANGE))
		{
			newTask.cancel();
			pushTaskToPersist(newTask);
			notifyChangePassword(entity);
			
			return;
		}
		else
		{
			addAndNotifyDispatchers(newTask, entity);
		}
	}

	private void notifySSOUsers(AccountEntity account) throws InternalErrorException {
		Account acc = getAccountEntityDao().toAccount(account);
        for (String user: getAccountService().getAccountUsers(acc))
        {
    	   getChangePasswordNotificationQueue().addNotification(user);
        }
	}

	private void storeAccountPassword(TaskHandler newTask,
			AccountEntity account) throws InternalErrorException {
		
		getSecretStoreService().setPassword(account.getId(),
			newTask.getPassword());
//		getAccountService().updateAccountPasswordDate(acc, null);

		for (String u :
			getAccountService().getAccountUsers(
				getAccountEntityDao().toAccount(account)))
		{
			ServerServiceLocator.instance()
				.getChangePasswordNotificationQueue()
				.addNotification(u);
		}
	}

	private void storeDomainPassword(TaskHandler task) throws InternalErrorException {
		
		PasswordDomainEntity dc = getPasswordDomainEntityDao().findByName(task.getTask().getPasswordDomain());
		
		if (dc != null)
		{
			UserEntityDao usuariDao = getUserEntityDao();
			UserEntity usuariEntity = usuariDao.findByUserName(task.getTask().getUser());
			if (usuariEntity == null) // Ignorar
				return;
			User usuari = usuariDao.toUser(usuariEntity);
			
			
			getSecretStoreService().putSecret(
					usuari,
					"dompass/" + dc.getId(), task.getPassword());
	
			boolean anyChange = false;
			for (SystemEntity de : dc.getSystems()) {
                if (de.getUrl() == null || de.getUrl().length() == 0) {
                    for (AccountEntity account : getAccountEntityDao().findByUserAndSystem(usuari.getUserName(), de.getName())) {
                        Account acc = getAccountEntityDao().toAccount(account);
                        getSecretStoreService().setPassword(account.getId(), task.getPassword());
                        anyChange = true;
                    }
                }
            }

			if (anyChange)
				getChangePasswordNotificationQueue().addNotification(usuari.getUserName());

		}	
	}



	/**
	 * @param entity
	 * @throws InternalErrorException 
	 * @throws UnknownGroupException 
	 * @throws NumberFormatException 
	 * @throws UnknownRoleException 
	 */
	private void notifyChangePassword(TaskEntity entity) throws InternalErrorException, NumberFormatException, UnknownGroupException, UnknownRoleException {
		notifyChangePasswordToUser(entity);
		
		notifyChangePasswordToGroup(entity);
		
		notifyChangePasswordToRole(entity);
	}

	/**
	 * @param entity
	 * @throws InternalErrorException
	 * @throws UnknownRoleException
	 * @throws NumberFormatException
	 */
	private void notifyChangePasswordToRole(TaskEntity entity) throws InternalErrorException, UnknownRoleException, NumberFormatException {
		// Check notify to role
		if (entity.getRole() != null)
		{
			// Notify to users of role
			for (Account account : getServerService()
					.getRoleAccounts(Long.parseLong(entity.getRole()), null))
			{
				if (account instanceof UserAccount)
				{
					String user = ((UserAccount) account).getUser();
					getChangePasswordNotificationQueue()
						.addNotification(user);
				}
			}
		}
	}

	/**
	 * @param entity
	 * @throws InternalErrorException
	 * @throws UnknownGroupException
	 * @throws NumberFormatException
	 */
	private void notifyChangePasswordToGroup(TaskEntity entity) throws InternalErrorException, UnknownGroupException, NumberFormatException {
		// Check notify to group
		if (entity.getGroup() != null)
		{
			// Notify to users of group
			for (User user : getServerService().getGroupUsers(Long.parseLong(entity.getGroup()), false, null)) {
                getChangePasswordNotificationQueue().addNotification(user.getUserName());
            }
		}
	}

	/**
	 * @param entity
	 * @throws InternalErrorException
	 */
	private void notifyChangePasswordToUser(TaskEntity entity) throws InternalErrorException {
		// Check notify to user
		if (entity.getUser() != null)
		{
			getChangePasswordNotificationQueue().addNotification(entity.getUser());
		}
	}

	private synchronized void addAndNotifyDispatchers(TaskHandler newTask, TaskEntity tasqueEntity) throws InternalErrorException {
		Hashtable<String, TaskHandler> currentTasks = getCurrentTasks();
		String hash = tasqueEntity.getHash();
		// Eliminar tareas similares
		if (hash == null || tasqueEntity.getServer() == null)
		{
			
			getSyncServerStatsService().register("queue", "scheduled", 1);
			hash = newTask.getHash();
			// Cancel local tasks
			TaskHandler oldTask = null;
			oldTask = currentTasks.get(hash);
			if (oldTask != null && !oldTask.getTask().getId().equals(newTask.getTask().getId()))
			{
				oldTask.cancel();
				pushTaskToPersist(oldTask);
			}
			// Cancel remote tasks
			TaskEntityDao dao = getTaskEntityDao();
			for (Iterator<TaskEntity> it = dao.findByHash(hash).iterator(); it.hasNext(); ) {
                TaskEntity remoteTask = it.next();
                cancelRemoteTask(remoteTask);
            }
			newTask.getTask().setStatus("P");
			newTask.getTask().setServer(hostname);
			newTask.getTask().setHash(hash);
			//  Now update database (it's really needed)
			tasqueEntity.setStatus("P");
			tasqueEntity.setServer(hostname);
			tasqueEntity.setHash(hash);
			getTaskEntityDao().update(tasqueEntity);
//			getTaskEntityDao().cancelUnscheduledCopies(tasqueEntity);
		} else {
			// Cancel local tasks
			TaskHandler oldTask = null;
			oldTask = currentTasks.get(hash);
			if (oldTask != null && !oldTask.getTask().getId().equals(newTask.getTask().getId()))
			{
				oldTask.cancel();
				pushTaskToPersist(oldTask);
			}
		}

		populateTaskLog(newTask, tasqueEntity);
		log.info("Added task {}", newTask.toString(), null);

		// Register new tasks
		currentTasks.put(hash, newTask);
		for ( DispatcherHandler dispatcherHandler : getTaskGenerator().getDispatchers())
		{
			if (! dispatcherHandler.isComplete(newTask))
			{
				PrioritiesList priorities = getPrioritiesList (dispatcherHandler);
				int priority = newTask.getPriority();
				if (priority >= MAX_PRIORITY)
					priority = MAX_PRIORITY - 1;
				TasksQueue queue = priorities.get(priority);
				synchronized (queue)
				{
					queue.addLast( newTask );
				}
			}
		}
	}

	private PrioritiesList getPrioritiesList(DispatcherHandler dispatcherHandler) {
		PrioritiesList p = globalTaskList.get(dispatcherHandler.getSystem().getId());
		if (p == null)
		{
			p = new PrioritiesList();
			for (int i = 0; i < MAX_PRIORITY; i++)
				p.add(new TasksQueue());
			globalTaskList.put(dispatcherHandler.getSystem().getId(), p);
		}
		return p;
	}

	private void populateTaskLog(TaskHandler newTask, TaskEntity tasque) throws InternalErrorException {
		// Instanciar los logs
		String targetDispatcher = tasque.getSystemName();
		if (targetDispatcher != null && targetDispatcher.trim().isEmpty())
			targetDispatcher = null;
		Collection<DispatcherHandler> dispatchers = getTaskGenerator().getDispatchers();
		ArrayList<TaskHandlerLog> logs = new ArrayList<TaskHandlerLog>(
						dispatchers.size());
		for (Iterator<DispatcherHandler> it = dispatchers.iterator(); it.hasNext();)
		{
			DispatcherHandler d = it.next();
			TaskHandlerLog log = new TaskHandlerLog();
			log.setDispatcher(d);
			log.setFirst(0);
			log.setNext(0);
			if (targetDispatcher == null)
				log.setComplete(false);
			else
				log.setComplete( ! targetDispatcher.equals(d.getSystem().getName()));				
			log.setNumber(0);
			while (logs.size() < d.getInternalId())
				logs.add(null);
			logs.add(log);
		}
		// Actualizar según base de datos
		long now = System.currentTimeMillis();
		for (Iterator<TaskLogEntity> it = tasque.getLogs().iterator(); it.hasNext(); ) {
            TaskLogEntity tl = it.next();
            for (Iterator<TaskHandlerLog> it2 = logs.iterator(); it2.hasNext(); ) {
                TaskHandlerLog thl = it2.next();
                if (thl != null &&
                		thl.getDispatcher().getSystem().getId().equals(tl.getSystem().getId())) {
                    thl.setComplete("S".equals(tl.getCompleted()));
                    thl.setReason(tl.getMessage());
                    thl.setFirst(tl.getCreationDate() == null ? 0 : tl.getCreationDate().getTime());
                    thl.setLast(tl.getLastExecution() == null ? 0 : tl.getLastExecution().longValue());
                    thl.setNext(now);
                    thl.setNumber(tl.getExecutionsNumber() == null ? 0 : tl.getExecutionsNumber().intValue());
                    thl.setStackTrace(tl.getStackTrace());
                    thl.setId(tl.getId());
                    break;
                }
            }
        }
		newTask.setLogs(logs);
		if (targetDispatcher != null)
			pushTaskToPersist(newTask);
	}

	private void cancelRemoteTask(TaskEntity task) throws InternalErrorException {
		TaskHandler th = new TaskHandler ();
		th.setTask(getTaskEntityDao().toTask(task));
		th.setTenantId(Security.getCurrentTenantId());
		th.setTenant(Security.getCurrentTenantName());
		th.cancel();
		pushTaskToPersist(th);
		
		if (task.getServer() != null && !task.getServer().equals(hostname))
		{
			RemoteServiceLocator rsl = new RemoteServiceLocator();
			try
			{
				rsl.setServer(task.getServer());
				ServerService server = rsl.getServerService();
				server.cancelTask(task.getId());
			}
			catch (Exception e)
			{
				log.warn("Error cancelling remote task", e);
			}
		}
	}

	@Override
    protected void handleRemoveTask(TaskEntity task) {
		throw new UnsupportedOperationException();
	}

	@Override
    protected TaskHandler handleAddTask(TaskEntity newTask) throws Exception {
		
		if (newTask.getId() == null)
		{
			newTask.setServer(hostname);
			newTask.setStatus("P");
			newTask.setDate(new Timestamp(System.currentTimeMillis()));
			if ( newTask.getTransaction().equals(TaskHandler.VALIDATE_PASSWORD))
				getTaskEntityDao().createForce(newTask);
			else
				getTaskEntityDao().create(newTask);
		} else {
			
			Long tenantId = newTask.getTenant().getId();
//			log.info("Loading task {} tenant {}", newTask.toString(), tenantId);
			String tenantName = Security.getTenantName (tenantId);  // Workaround lazy tenant loader
			Security.nestedLogin(tenantName, Config.getConfig().getHostName(), Security.ALL_PERMISSIONS);
			try
			{
				Long taskId = newTask.getId();
				newTask = getTaskEntityDao().load(taskId);
				if (newTask == null)
				{
					log.warn("Unable to load task {}",taskId,null);
					return null;
				}
			} finally {
				Security.nestedLogoff();
			}
		}

		String tenant = newTask.getTenant() == null || newTask.getTenant().getName() == null ? 
				Security.getCurrentTenantName(): 
				newTask.getTenant().getName();
		log.info("Tenant name {}", tenant, null);		
		Security.nestedLogin(tenant, Config.getConfig().getHostName(), Security.ALL_PERMISSIONS);
		try
		{
			TaskHandler th = new TaskHandler();
			th.setTenant( tenant );
			th.setTask(getTaskEntityDao().toTask(newTask));
			th.setTimeout(null);
			th.setValidated(false);
			if (newTask.getTenant() == null || newTask.getTenant().getName() == null)
				th.setTenantId(Security.getCurrentTenantId());
			else
				th.setTenantId( newTask.getTenant().getId());
			if (newTask.getId() != null)
				addTask(th, newTask);
			return th;
		} finally {
			Security.nestedLogoff();
		}
	}

	/**
	 * Devolver la primera tarea a realizar por un task dispatcher
	 * 
	 * @param taskDispatcher
	 *            agente que desea ejecutar la tarea
	 * @return null si no hay tareas pendientes o las que hay pendientes no se pueden
	 *         ejecutar todavía
	 */
	@Override
	protected TaskHandler handleGetPendingTask (DispatcherHandler taskDispatcher)
					throws Exception
	{
		int internalId = taskDispatcher.getInternalId();
		TaskHandler task;

		LinkedList<TaskHandler> tasksToNotify = new LinkedList<TaskHandler>();
		LinkedList<TaskHandler> tasksToRemove = new LinkedList<TaskHandler>();
		PrioritiesList priorities = getPrioritiesList(taskDispatcher);
		try {
			for ( int priority = 0; priority < priorities.size(); priority++)
			{
				TasksQueue priorityQueue = priorities.get(priority);
				synchronized (priorityQueue)
				{
//					log.info("Getting tasks for priority {}", priority, null);
					Iterator<TaskHandler> iterator = priorityQueue.iterator();
					while (iterator.hasNext())
					{
						task = iterator.next();
						TaskHandlerLog tl = task.getLog(internalId);
		    			if (tl != null && tl.isComplete()) {
		    				// Already processed
		    			}
		    			else if (task.isExpired())
			    		{
		    				iterator.remove();
			    			tasksToRemove.add(task);
			    		}
			    		else if (task.isComplete())
			    		{
			    			tasksToNotify.add(task);
			    			iterator.remove();
			    		}
			    		else if (taskDispatcher.isComplete(task))
			    		{
			    			iterator.remove();
			    			tasksToNotify.add(task);
			    		}
			    		else if (!taskDispatcher.applies(task))
		    			{
			    			iterator.remove();
			    			tasksToNotify.add(task);
						}
						else if (tl.getNext() < System.currentTimeMillis())
						{
			    			iterator.remove();
//							log.info("Got task {}", task.toString(), null);
							return task;
						}
	    			} 
//					log.info("No tasks for priority {}", priority, null);
	    		}
			}
		} finally {
    		removeTaskList(tasksToRemove);
    		notifyUnmanagedTasks(taskDispatcher, tasksToNotify);
		}
		return null;

	}

	@Override
	protected TaskHandler handleGetNextPendingTask (DispatcherHandler taskDispatcher,
					TaskHandler previousTask) throws Exception
	{
		return handleGetPendingTask(taskDispatcher);
	}

	private void notifyUnmanagedTasks (DispatcherHandler taskDispatcher,
					LinkedList<TaskHandler> tasksToNotify) throws Exception,
					InternalErrorException
	{
		for (TaskHandler taskToNotify: tasksToNotify)
		{
			notifyTaskStatus(taskToNotify, taskDispatcher, true, null, null);
		}
	}

	private void removeTaskList (LinkedList<TaskHandler> tasksToRemove)
					throws Exception, InternalErrorException
	{
		for (TaskHandler taskToRemove: tasksToRemove)
		{
			if (taskToRemove.getTimeout() != null)
			{
				synchronized (taskToRemove)
				{
					taskToRemove.notify();
				}
			}
		
			taskToRemove.cancel();
			pushTaskToPersist(taskToRemove);
		}
	}

	@Override
	protected int handleCountTasks () throws Exception
	{
		return globalCurrentTasks.size();
	}

	@Override
	protected int handleCountTasks (DispatcherHandler taskDispatcher) throws Exception
	{
		int size = 0;
		PrioritiesList priorities = getPrioritiesList(taskDispatcher);
		for ( int priority = 0; priority < priorities.size(); priority++)
		{
			TasksQueue priorityQueue = priorities.get(priority);
			size += priorityQueue.size();
		}
		return size;
	}

//	@Override
	protected int handleCountErrorTasks (DispatcherHandler taskDispatcher) throws Exception
	{
		int size = 0;
		PrioritiesList priorities = getPrioritiesList(taskDispatcher);
		for ( int priority = 0; priority < priorities.size(); priority++)
		{
			TasksQueue priorityQueue = priorities.get(priority);
			synchronized (priorityQueue) {
				for (TaskHandler task : priorityQueue)
				{
					if (taskDispatcher.isError(task))
						size++;
				}
			}
		}
		return size;
	}

	@Override
	protected void handleExpireTasks () throws Exception
	{
//		log.info("Expiring tasks", null, null);
		Set<Long> currentTenants = getTaskGenerator().getActiveTenants();
		Date now = new Date();
		for (Long tenant: globalCurrentTasks.keySet() )
		{
			Hashtable<String, TaskHandler> ct = globalCurrentTasks.get(tenant);
			try {
				for (Iterator<Entry<String, TaskHandler>> iterator = ct.entrySet().iterator(); iterator.hasNext();)
				{
					Entry<String, TaskHandler> entry = iterator.next();
					TaskHandler task = entry.getValue();
					if (task.getTimeout() != null && task.getTimeout().before(now))
					{
						task.cancel();
						iterator.remove();
						pushTaskToPersist(task);
						synchronized (task)
						{
							task.notify();
						}
					}
					else
					{
						boolean allOk = true;
						for (DispatcherHandler dh : getTaskGenerator().getDispatchers())
						{
							if (dh != null && dh.isActive())
							{
								if (task.getLogs().size() <= dh.getInternalId())
								{
									allOk = false;
									break;
								}
								else
								{
									TaskHandlerLog tasklog = task.getLogs().get(
											dh.getInternalId());
									if (tasklog == null || !tasklog.isComplete())
									{
										allOk = false;
										break;
									}
								}
							}
						}
						
						if (allOk)
						{
							task.cancel();
							pushTaskToPersist(task);
							iterator.remove();
							log.debug("Removing task {} from queue", task, null);
						} else if ( ! currentTenants.contains(  task.getTenantId() ) )
						{
							task.reject();
							pushTaskToPersist(task);
//							log.info("Removing task {} from queue", task, null);
//							log.info("Tenant task = {}", task.getTenantId(), null);
//							for ( Long t: currentTenants)
//								log.info(" Current tenant = {}", t, null);
							iterator.remove();
						}
					}
					
				}
			} catch (ConcurrentModificationException e) {
				log.info("Unable to purge expired tasks: "+ e.toString(), null, null);
			}
		}
	}

	@Override
	protected void handleNotifyTaskStatus (TaskHandler task,
					DispatcherHandler taskDispatcher, boolean bOK, String sReason,
					Throwable t) throws Exception
	{
		long now = System.currentTimeMillis();

		// Afegir (si cal) nous task logs
		while (task.getLogs().size() <= taskDispatcher.getInternalId())
		{
			task.getLogs().add(null);
		}

		// Actualitzar el tasklog
		TaskHandlerLog thl = task.getLogs().get(taskDispatcher.getInternalId());
		if (thl == null)
		{
			thl = new TaskHandlerLog();
			thl.setNumber(0);
			thl.setFirst(now);
			thl.setComplete(false);
			task.getLogs().set(taskDispatcher.getInternalId(), thl);
		}
		
		if (thl.isComplete())
		{
			// This task was already notified;
			return;
		}
		
		thl.setDispatcher(taskDispatcher);
		thl.setComplete(bOK);
		thl.setReason(sReason);
		thl.setStackTrace(dumpStrackTrace(t));
		thl.setNumber(thl.getNumber() + 1);
		thl.setLast(now);
		
		long elapsed;
		if (thl.getFirst() > 0)
			elapsed = thl.getLast() - thl.getFirst();
		else
		{
			elapsed = 0;
			thl.setFirst(now);
		}
		if (elapsed < 1000)
			elapsed = 1000; // Mínimo un segundo
		if (elapsed > 1000 * 60 * 60 * 8)
			elapsed = 1000 * 60 * 60 * 8; // Máximo 8 horas
		thl.setNext(thl.getLast() + elapsed);

		if ( ! bOK )
		{
			int nextPriority = task.getPriority() + thl.getNumber();
			if (nextPriority >= MAX_PRIORITY)
				nextPriority = MAX_PRIORITY - 1;
			PrioritiesList prioQueue = getPrioritiesList(taskDispatcher);
			TasksQueue queue = prioQueue.get(nextPriority);
			synchronized (queue)
			{
//				log.info("Returning task {} to the queue", task.toString(), null);
				queue.addLast(task);
			}
		}
		// Verificar si ha de cancellar la tasca
		boolean allOk = true;
		StringBuffer message = new StringBuffer();
		String status = "P";
//		log.info("Notify task status {} {}", task.toString(), bOK);
		synchronized (task)
		{
			for (DispatcherHandler dh : getTaskGenerator().getDispatchers())
			{
				if ((dh != null) && dh.isActive())
				{
					if ((task.getTask().getSystemName() == null) ||
							task.getTask().getSystemName()
								.equals(dh.getSystem().getName()))
					{
//						log.info("Dispatcher {} ", dh.getSystem().getName(), null);
						if (task.getLogs().size() <= dh.getInternalId())
						{
//							log.info("No log present ", null, null);
							allOk = false;
						}
						else
						{
							TaskHandlerLog tasklog = task.getLogs().get(dh.getInternalId());
							if (tasklog == null)
							{
//								log.info("Log is empty ", null, null);
								allOk = false;
							}
							else if (!tasklog.isComplete())
							{
//								log.info("Log is not finished ", null, null);
								allOk = false;
								if (tasklog.getNumber() >= 3)
									status = "E";
								if ((tasklog.getReason() != null) &&
										tasklog.getReason().length() > 0)
								{
									message.append(tasklog.getDispatcher()
													.getSystem().getName());
									message.append(": ");
									message.append(tasklog.getReason());
								}
							}
						}
					}
				}
			}
		}
		// //////////////// TASK COMPLETE
		if (allOk)
		{
//			log.info("Task is finished ", null, null);
			Hashtable<String, TaskHandler> currentTasks = getCurrentTasks();

			task.cancel();
			pushTaskToPersist(task);
			currentTasks.remove(task.getHash());
			// Confirmar la propagación de contraseñas
			String transaction = task.getTask().getTransaction();
			if (transaction.equals(TaskHandler.UPDATE_PROPAGATED_PASSWORD)
							|| transaction.equals(TaskHandler.UPDATE_USER_PASSWORD))
			{
				UserEntity usuari = getUserEntityDao().findByUserName(task.getTask().getUser());
				PasswordDomainEntity passDomain = getPasswordDomainEntityDao().findByName(task.getTask().getPasswordDomain());
				if (usuari != null && passDomain != null)
					getInternalPasswordService().confirmPassword(usuari, passDomain,
									task.getPassword());
			}

			synchronized (task)
			{
				task.notify();
			}
			// //////////////// TASK EXPIRED
		}
		else if (task.getTimeout() != null && task.getTimeout().before(new Date()))
		{
			Hashtable<String, TaskHandler> currentTasks = getCurrentTasks();

			pushTaskToPersist(task);
			currentTasks.remove(task.getHash());
			synchronized (task)
			{
				task.notify();
			}
			// //////////////// TAK PENDING
		}
		else
		{
			pushTaskToPersist(task);
		}
	}

	private static final String STACK_TRACE_FILTER = "es.caib.seycon";

	private String dumpStrackTrace (Throwable t)
	{
		if (t == null)
			return null;

		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(out);
		SoffidStackTrace.printStackTrace(t, ps);
		ps.close();
		String s = out.toString();
		if (s.length() > 2000)
			s = s.substring(0, 1990) + "... (more)";
		
		return s; 

	}

	private void printStackTraceAsCauseSeycon (Throwable th, PrintWriter s,
					StackTraceElement[] causedTrace)
	{
		// assert Thread.holdsLock(s);
		if (th == null)
			return;

		// Compute number of frames in common between this and caused
		StackTraceElement[] trace = th.getStackTrace();
		int m = trace.length - 1, n = causedTrace.length - 1;
		while (m >= 0 && n >= 0 && trace[m].equals(causedTrace[n]))
		{
			m--;
			n--;
		}
		int framesInCommon = trace.length - 1 - m;

		s.println("Caused by: " + th);
		for (int i = 0; i <= m; i++)
		{
			if (trace[i].getClassName().startsWith(STACK_TRACE_FILTER))
				s.println("\tat " + trace[i]);
		}
		if (framesInCommon != 0)
			s.println("\t... " + framesInCommon + " more");

		// Recurse if we have a cause
		Throwable ourCause = th.getCause();
		if (ourCause != null)
			printStackTraceAsCauseSeycon(ourCause, s, trace);
	}

	@Override
	protected void handleCancelTask (long taskId) throws Exception
	{
		TaskEntity taskEntity = getTaskEntityDao().load(taskId);
		if (taskEntity != null)
		{
			Hashtable<String, TaskHandler> ct = globalCurrentTasks.get(Security.getCurrentTenantId());
			if (ct != null)
			{
				TaskHandler task = ct.get(taskEntity.getHash());
				if (task != null)
				{
					if (task.getTask().getId().longValue() == taskId)
					{
						task.cancel();
						pushTaskToPersist(task);
						ct.remove(taskEntity.getHash());
					}
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handleUpdateTask(es.caib.seycon.ng.model.TasqueEntity)
	 */
	@Override
    protected void handleUpdateTask(TaskEntity task) throws Exception {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings ("rawtypes")
	@Override
	protected Map<String,Exception> handleProcessOBTask (TaskHandler task) throws Exception
	{
		return processOBTask(task, false);
	}

	@SuppressWarnings ("rawtypes")
	@Override
	protected Map<String, DebugTaskResults> handleDebugTask (TaskHandler task) throws Exception
	{
		return processOBTask(task, true);
	}

	protected Map processOBTask (TaskHandler task, boolean debug) throws Exception
	{
		Map<String, Exception> m = new HashMap<String, Exception>();
		Map<String, DebugTaskResults> debugMap = new HashMap<String, DebugTaskResults>();

		if (task.getTask().getTransaction().equals(TaskHandler.UPDATE_USER_PASSWORD))
		{
			if (task.getPassword() != null)
			{
				InternalPasswordService ps = getInternalPasswordService();
				UserEntityDao usuariDao = getUserEntityDao();
				UserEntity usuari = usuariDao.findByUserName(task.getTask().getUser());
				if (usuari == null) // Ignorar
				{
					return m;
				}
				PasswordDomainEntityDao dcDao = getPasswordDomainEntityDao();
				PasswordDomainEntity dc = dcDao.findByName(task.getTask().getPasswordDomain());
				if (dc == null)
				{
					return m; // Ignorar
				}

				storeDomainPassword(task);
			}
			else
			{
				return m;
			}
		}
		else if (task.getTask().getTransaction()
						.equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD))
		{
			if (task.getPassword() != null)
			{
				InternalPasswordService ps = getInternalPasswordService();
				AccountEntityDao accDao = getAccountEntityDao();
				AccountEntity account = accDao.findByNameAndSystem(task.getTask().getUser(), task.getTask().getSystemName());
				if (account == null)
				{
					return m;
				}

				// Update for virtual dispatchers
				DispatcherHandler dispatcher = getTaskGenerator().getDispatcher(task.getTask().getSystemName());
				if (dispatcher == null || ! dispatcher.isActive()) 
				{
					storeAccountPassword(task, account);
					notifySSOUsers(account);
   		            return m;
				}
			}
			else
			{
				return m;
			}
		}
		
		for (DispatcherHandler dispatcher: getTaskGenerator().getDispatchers())
		{
			String dispatcherName = dispatcher.getSystem().getName();
			if (task.getTask().getSystemName() == null || 
					dispatcherName.equals (task.getTask().getSystemName()))
			{
				DebugTaskResults  r = new DebugTaskResults();
				if (dispatcher.isConnected() && dispatcher.isActive())
				{
					if (debug)
					{
						r = dispatcher.debugTask(task);
					}
					else
					{
	    				try {
	    					dispatcher.processOBTask(task);
	    				} catch (Exception e) {
	    					if (task.getTask().getSystemName() == null)
	    						m.put(dispatcherName, dispatcher.getConnectException());
	    					else
	    						m.put(dispatcherName, e);
	    					r.setException(e);
	    				}
					}
				}
				else if (dispatcher.getConnectException() != null)
				{
					m.put(dispatcherName, dispatcher.getConnectException());
					r.setException(dispatcher.getConnectException());
					r.setStatus("System is offilne");
				}
				else
				{
					r.setStatus("System is offilne");
				}
				debugMap.put(dispatcherName, r);
			}
		}
		if (!m.isEmpty() && !debug)
		{
			Task tasca = task.getTask();
			tasca.setTaskDate(Calendar.getInstance());
			TaskEntity tasque = getTaskEntityDao().taskToEntity(tasca);
			getTaskEntityDao().createForce(tasque);
			tasca.setId(tasque.getId());
			addTask(task);
		}
		if (debug)
			return debugMap;
		else
			return m;
	}

	/* (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	public void setApplicationContext (ApplicationContext applicationContext)
					throws BeansException
	{
		this.applicationContext = applicationContext;
	}
	
	PlatformTransactionManager transactionManager = null;
	public PlatformTransactionManager getTransactionManager()
	{
		if (transactionManager == null)
			transactionManager = (PlatformTransactionManager) applicationContext.getBean("transactionManager");
		return transactionManager; 
	}
	
	class RequiresNewTransaction implements TransactionDefinition
	{

		/* (non-Javadoc)
		 * @see org.springframework.transaction.TransactionDefinition#getPropagationBehavior()
		 */
		public int getPropagationBehavior ()
		{
			return PROPAGATION_REQUIRES_NEW;
		}

		/* (non-Javadoc)
		 * @see org.springframework.transaction.TransactionDefinition#getIsolationLevel()
		 */
		public int getIsolationLevel ()
		{
			return ISOLATION_READ_COMMITTED;
		}

		/* (non-Javadoc)
		 * @see org.springframework.transaction.TransactionDefinition#getTimeout()
		 */
		public int getTimeout ()
		{
			return TIMEOUT_DEFAULT;
		}

		/* (non-Javadoc)
		 * @see org.springframework.transaction.TransactionDefinition#isReadOnly()
		 */
		public boolean isReadOnly ()
		{
			return false;
		}

		/* (non-Javadoc)
		 * @see org.springframework.transaction.TransactionDefinition#getName()
		 */
		public String getName ()
		{
			return "Custom nested short transaction";
		}
		
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handleNotifyTaskStatusNewTransaction(es.caib.seycon.ng.sync.engine.TaskHandler, es.caib.seycon.ng.sync.engine.DispatcherHandler, boolean, java.lang.String, java.lang.Throwable)
	 */
	@Override
	protected void handleNotifyTaskStatusNewTransaction (TaskHandler task,
					DispatcherHandler taskDispatcher, boolean bOK, String sReason,
					Throwable t) throws Exception
	{
		handleNotifyTaskStatus(task, taskDispatcher, bOK, sReason, t);
	}

	
	// Task to persist list
	LinkedList<TaskHandler> tasksToPersist = new LinkedList<TaskHandler>();
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handlePushTaskToPersist(es.caib.seycon.ng.sync.engine.TaskHandler)
	 */
	@Override
	protected void handlePushTaskToPersist (TaskHandler newTask) throws Exception
	{
		synchronized (tasksToPersist)
		{
			newTask.setChanged(true);
			tasksToPersist.push(newTask);
			tasksToPersist.notify();
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handlePeekTaskToPersist()
	 */
	@Override
	protected TaskHandler handlePeekTaskToPersist () throws Exception
	{
		synchronized (tasksToPersist)
		{
			if (tasksToPersist.isEmpty())
			{
				tasksToPersist.wait(5000);
			}
			if (tasksToPersist.isEmpty())
				return null;
			else
				return tasksToPersist.removeLast();
		}
	}

	private void persistLog(TaskEntity tasqueEntity, TaskHandlerLog thl, TaskLogEntity entity) {
		if (thl.getId() == null)
		{

			if (thl.getLast() == 0) return; // Still not done task
				
			entity = getTaskLogEntityDao().newTaskLogEntity();
			entity.setSystem(getSystemEntityDao().load(thl.getDispatcher().getSystem().getId()));
			entity.setTask(tasqueEntity);
			entity.setCreationDate(new Date());
		}
		else
		{
			entity = getTaskLogEntityDao().load(thl.getId().longValue());
		}
		entity.setExecutionsNumber(new Long(thl.getNumber()));
		entity.setNextExecution(thl.getNext());
		entity.setCompleted(thl.isComplete() ? "S" : "N");
		entity.setLastExecution(thl.getLast());
		entity.setMessage(thl.getReason());
		entity.setStackTrace(thl.getStackTrace());
		if (thl.getId() == null)
		{
			getTaskLogEntityDao().create(entity);
			thl.setId(entity.getId());
		}
		else
		{
			getTaskLogEntityDao().update(entity);
		}
	}
	
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handlePersistTask(es.caib.seycon.ng.sync.engine.TaskHandler)
	 */
	@Override
	protected void handlePersistTask (TaskHandler newTask) throws Exception
	{
//		log.info("Persist task {}", newTask.toString(), null);
		if (newTask.isRejected())
		{
			getSyncServerStatsService().register("queue", "rejected", 1);
			log.info("Task {} is rejected", newTask.toString(), null);
    		TaskEntityDao dao = getTaskEntityDao();
    		newTask.setChanged(false);
    		TaskEntity tasque = dao.load(newTask.getTask().getId());
    		if (tasque != null)
    		{
    			tasque.setServer(null);
    			dao.update(tasque);
    		}
		}
		else if (newTask.isChanged())
		{
			try {
	    		TaskEntityDao dao = getTaskEntityDao();
	    		TaskLogEntityDao tlDao = getTaskLogEntityDao();
	    		newTask.setChanged(false);
	    		TaskEntity tasque = dao.load(newTask.getTask().getId());
	    		if (tasque == null)
	    		{
	    			newTask.cancel();
	    			log.info("Task {} was previously removed", newTask.toString(), null);
	    		}
	    		else if (newTask.isComplete())
	    		{
	    			if (tasque != null)
	    			{
	        			tlDao.remove(tasque.getLogs());
	        			dao.remove(tasque);
	        			getSyncServerStatsService().register("queue", "finished", 1);
	    			}
//	    			log.info("Task {} is complete", newTask.toString(), null);
	    		}
	    		else
	    		{
	    			tasque.setStatus(newTask.getTask().getStatus());
	    			tasque.setHash(newTask.getTask().getHash());
	    			tasque.setServer(newTask.getTask().getServer());
	    			tasque.setPriority(new Long(newTask.getPriority()));
	    			dao.update(tasque);
	    
	    			Collection<TaskLogEntity> daoEntities = tasque.getLogs();
//	    			log.info("Task {} is pending", newTask.toString(), null);
	    			if (newTask.getLogs() != null)
	    			{
	        			for (TaskHandlerLog tasklog : newTask.getLogs()) {
	        				if (tasklog != null)
	        				{
	                            boolean found = false;
	                            for (TaskLogEntity logEntity : daoEntities) {
	                                if (logEntity != null &&
	                                		tasklog.getDispatcher() != null &&
	                                		logEntity.getSystem().getId().equals(tasklog.getDispatcher().getSystem().getId())) {
	                                    found = true;
	                                    persistLog(tasque, tasklog, logEntity);
	                                    break;
	                                }
	                            }
	                            if (!found) {
	                                TaskLogEntity logEntity = tlDao.newTaskLogEntity();
	                                persistLog(tasque, tasklog, logEntity);
	                            }
//	                            log.info(">> {}: {}", tasklog.getDispatcher().getSystem().getName(), tasklog.isComplete() ? "DONE": "PENDING");
	        				}
                        }
	    			}
	    		}
			} catch (Exception e) {
	    		newTask.setChanged(true);
	    		synchronized (tasksToPersist) {
	    			tasksToPersist.addFirst(newTask);
				}
				throw e;
			}
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handleFindTaskHandlerById(long)
	 */
	@Override
	protected TaskHandler handleFindTaskHandlerById (long taskId) throws Exception
	{
		TaskEntity taskEntity = getTaskEntityDao().load(taskId);
		if (taskEntity != null)
		{
			Hashtable<String, TaskHandler> ct = globalCurrentTasks.get(Security.getCurrentTenantId());
			if (ct != null)
			{
				TaskHandler task = ct.get(taskEntity.getHash());
				if (task != null)
				{
					if (task.getTask().getId().longValue() == taskId)
					{
						return task;
					}
				}
			}
		}
		return null;
	}
}

class TasksQueue extends LinkedList<TaskHandler> {
	
}

class PrioritiesList extends ArrayList<TasksQueue>{
	
}

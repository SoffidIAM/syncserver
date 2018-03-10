package com.soffid.iam.sync.service;

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
import com.soffid.iam.sync.engine.PriorityTaskQueue;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.TaskHandlerLog;
import com.soffid.iam.sync.engine.TaskQueueIterator;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.service.TaskQueueBase;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownRoleException;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Hibernate;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

public class TaskQueueImpl extends TaskQueueBase implements ApplicationContextAware
{
	public static final int MAX_PRIORITY = 2;
	/**
	 * Priorized tasks for any tenant
	 */
	Hashtable<Long,ArrayList<LinkedList<TaskHandler>>> globalTaskList;
	/**
	 * Current tasks for any tenant, indexed by task hash.
	 * It's used to detect duplicated tasks
	 */
	Map<Long, Hashtable<String, TaskHandler>> globalCurrentTasks;
	/**
	 * New incoming tasks not yet included in current tasks
	 */
	Map<Long, ArrayList<PriorityTaskQueue>> globalPriorityQueues;

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
	
	private ArrayList<PriorityTaskQueue> getPriorityQueues ()
	{
		Long l = Security.getCurrentTenantId();
		ArrayList<PriorityTaskQueue> ht = globalPriorityQueues.get(l);
		if (ht == null)
		{
			ht = new ArrayList<PriorityTaskQueue>();
			globalPriorityQueues.put(l, ht);
		}
		return ht;
	}

	private ArrayList<LinkedList<TaskHandler>> getTasksList ()
	{
		Long l = Security.getCurrentTenantId();
		ArrayList<LinkedList<TaskHandler>> ht = globalTaskList.get(l);
		if (ht == null)
		{
			ht = new ArrayList<LinkedList<TaskHandler>>();
			for (int i = 0; i <= MAX_PRIORITY; i++)
			{
				ht.add(i, new LinkedList<TaskHandler>());
			}
			globalTaskList.put(l, ht);
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
		globalTaskList = new Hashtable<Long,ArrayList<LinkedList<TaskHandler>>>();
		globalPriorityQueues = new Hashtable<Long, ArrayList<PriorityTaskQueue>>();
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
			// Actualizar la cache de passwords
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
				InternalPasswordService ps = getInternalPasswordService();
				AccountEntityDao accDao = getAccountEntityDao();
				AccountEntity account = accDao.findByNameAndSystem(newTask.getTask().getUser(), newTask.getTask().getSystemName());
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
		} finally {
			Security.nestedLogoff();
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
//			newTask.setTask(getTasqueEntityDao().toTasca(tasqueEntity));
			newTask.getTask().setStatus("P");
			newTask.getTask().setServer(hostname);
			newTask.getTask().setHash(hash);
			//  Now update database (it's really needed)
			tasqueEntity.setStatus("P");
			tasqueEntity.setServer(hostname);
			tasqueEntity.setHash(hash);
			getTaskEntityDao().update(tasqueEntity);
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

		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		synchronized (taskList)
		{
			LinkedList<TaskHandler> list = taskList.get(newTask.getPriority());
			list.add(newTask);
			// Register new tasks
			currentTasks.put(hash, newTask);
		}

		if (newTask.getPriority() < MAX_PRIORITY)
		{
			ArrayList<PriorityTaskQueue> priorityQueues = getPriorityQueues();
			synchronized (priorityQueues)
			{
				for (int i = 0; i < priorityQueues.size(); i++)
				{
					PriorityTaskQueue queue = priorityQueues.get(i);
					if (queue != null)
						queue.addTask(newTask);
				}
			}
		}
	}

	private void populateTaskLog(TaskHandler newTask, TaskEntity tasque) throws InternalErrorException {
		// Instanciar los logs
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
			log.setComplete(false);
			log.setNumber(0);
			logs.add(log);
		}
		// Actualizar según base de datos
		long now = System.currentTimeMillis();
		for (Iterator<TaskLogEntity> it = tasque.getLogs().iterator(); it.hasNext(); ) {
            TaskLogEntity tl = it.next();
            for (Iterator<TaskHandlerLog> it2 = logs.iterator(); it2.hasNext(); ) {
                TaskHandlerLog thl = it2.next();
                if (thl.getDispatcher().getSystem().getId().equals(tl.getSystem().getId())) {
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
			getTaskEntityDao().create(newTask);
		}
		TaskHandler th = new TaskHandler();
		Long id = newTask.getTenant().getId();
		th.setTenantId(id);
		th.setTenant( Security.getTenantName ( id ) );
		th.setTask(getTaskEntityDao().toTask(newTask));
		th.setTimeout(null);
		th.setValidated(false);
		addTask(th);
		return th;
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
		TaskHandler task;
		// Clean taskDispatcher priority queue

		if (taskDispatcher != null)
		{
			ArrayList<PriorityTaskQueue> priorityQueues = getPriorityQueues();
			synchronized (priorityQueues )
			{
				while (priorityQueues.size() <= taskDispatcher.getInternalId())
				{
					priorityQueues.add(null);
				}
				PriorityTaskQueue queue = priorityQueues.get(taskDispatcher
								.getInternalId());
				if (queue == null)
				{
					queue = new PriorityTaskQueue();
					priorityQueues.set(taskDispatcher.getInternalId(), queue);
				}
				else
				{
					queue.clear();
				}

			}
		}

		LinkedList<TaskHandler> tasksToNotify = new LinkedList<TaskHandler>();
		LinkedList<TaskHandler> tasksToRemove = new LinkedList<TaskHandler>();		 
						
		try {
			ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
    		synchronized (taskList)
    		{
    			boolean retry;
    			do
    			{
    				tasksToNotify.clear();
    				tasksToRemove.clear();
    				retry = false;
    				TaskQueueIterator iterator = new TaskQueueIterator(taskList);
    				try
    				{
    					while (iterator.hasNext())
    					{
    						task = iterator.next();
    						if (taskDispatcher == null)
    						{
    							return task;
    						}
    						else if (task.isExpired())
    						{
    							tasksToRemove.add(task);
    						}
    						else if (task.isComplete())
    						{
    							iterator.remove();
    						}
    						else if (taskDispatcher.isComplete(task))
    						{
    							// Ignore
    						}
    						else if (!taskDispatcher.applies(task))
    						{
    							tasksToNotify.add(task);
    						}
    						else
    						{
    							return task;
    						}
    					}
    				}
    				catch (ConcurrentModificationException e)
    				{
    					retry = true;
    				}
    			} while (retry);
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
		TaskHandler task;
		boolean found = false;

		LinkedList<TaskHandler> tasksToNotify = new LinkedList<TaskHandler>();
		LinkedList<TaskHandler> tasksToRemove = new LinkedList<TaskHandler>();		 
						
		try {
    		// First try with dispatcher priority task
    		if (taskDispatcher != null)
    		{
    			ArrayList<PriorityTaskQueue> priorityQueues = getPriorityQueues();
    			PriorityTaskQueue priorityQueue;
    			synchronized (priorityQueues)
    			{
    				priorityQueue = priorityQueues.get(taskDispatcher.getInternalId());
    			}
    			if (priorityQueue != null)
    			{
    				task = priorityQueue.getNextPendingTask(previousTask);
    				while (task != null)
    				{
    					if (!taskDispatcher.isComplete(task))
    					{
    						if (!taskDispatcher.applies(task))
    							tasksToNotify.add(task);
    						else
    							return task;
    					}
    					task = priorityQueue.getNextPendingTask(previousTask);
    				}
    
    			}
    		}
    
    		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
    		synchronized (taskList)
    		{
    			boolean retry;
    			do
    			{
    				retry = false;
    				try
    				{
    					TaskQueueIterator it = new TaskQueueIterator(taskList,
    									previousTask.getPriority());
    					while (it.hasNext())
    					{
    						task = it.next();
    						if (task == previousTask)
    							found = true;
    						else if (found)
    						{
    	    					if (taskDispatcher == null)
    							{
    								return task;
    							}
    							else if (task.isExpired())
    							{
    								tasksToRemove.add(task);
    							}
    							else if (task.isComplete())
    							{
    								it.remove();
    							}
    							else if (taskDispatcher.isComplete(task))
    							{
    								// Ignore 
    							}
    							else if (!taskDispatcher.applies(task))
    							{
    								tasksToNotify.add(task);
    							}
    							else
    							{
    								return task;
    							}
    						}
    					}
    				}
    				catch (ConcurrentModificationException e)
    				{
    					retry = true;
    				}
    			} while (retry);
    		}
    
    		if (!found)
    		{
    			boolean retry;
    			do
    			{
    				retry = false;
    				try
    				{
    					synchronized (taskList)
    					{
    						TaskQueueIterator it = new TaskQueueIterator(taskList,
    										previousTask.getPriority());
    						while (it.hasNext())
    						{
    							task = it.next();
    							if (taskDispatcher == null)
    							{
    								return task;
    							}
        						else if (task.isExpired())
        						{
        							tasksToRemove.add(task);
        						}
    							else if (task.isComplete())
    							{
    								it.remove();
    							}
    							else if (taskDispatcher.isComplete(task))
    							{
    								// IGNORE
    							}
    							else if (!taskDispatcher.applies(task))
    							{
    								tasksToNotify.add(task);
    							}
    							else
    							{
    								return task;
    							}
    						}
    					}
    				}
    				catch (ConcurrentModificationException e)
    				{
    					retry = true;
    				}
    			} while (retry);
    		}
		} finally {
    		removeTaskList(tasksToRemove);
    		notifyUnmanagedTasks(taskDispatcher, tasksToNotify);
		}
		return null;
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
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		int contador = 0;
		synchronized (taskList)
		{
			for (int priority = 0; priority < taskList.size(); priority++)
			{
				for (Iterator<TaskHandler> it = taskList.get(priority).iterator(); it
								.hasNext();)
				{
					TaskHandler task = it.next();
					if (task.isComplete())
						it.remove();
					else
						contador++;
				}
			}
		}
		return contador;
	}

	@Override
	protected int handleCountTasks (DispatcherHandler taskDispatcher) throws Exception
	{
		int contador = 0;
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		synchronized (taskList)
		{
			for (int priority = 0; priority < taskList.size(); priority++)
			{
				for (Iterator<TaskHandler> it = taskList.get(priority).iterator(); it
								.hasNext();)
				{
					TaskHandler task = it.next();
					if (task.isComplete())
						it.remove();
					else if (!taskDispatcher.isComplete(task))
						contador++;
				}
			}
		}
		return contador;
	}

	@Override
	protected int handleCountErrorTasks (DispatcherHandler taskDispatcher) throws Exception
	{
		int contador = 0;
		synchronized (taskList)
		{
			for (int priority = 0; priority < taskList.size(); priority++)
			{
				for (Iterator<TaskHandler> it = taskList.get(priority).iterator(); it
								.hasNext();)
				{
					TaskHandler task = it.next();
					if (task.isComplete())
						it.remove();
					else if (taskDispatcher.isError(task))
						contador++;
				}
			}
		}
		return contador;
	}

	@Override
	protected void handleExpireTasks () throws Exception
	{
		Set<Long> currentTenants = getTaskGenerator().getActiveTenants();
		TaskHandler task;
		Date now = new Date();
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		Hashtable<String, TaskHandler> currentTasks = getCurrentTasks();
		synchronized (taskList)
		{
			TaskQueueIterator it = new TaskQueueIterator(taskList);
			while (it.hasNext())
			{
				task = it.next();

				if (task.getTimeout() != null && task.getTimeout().before(now))
				{
					task.cancel();
					currentTasks.remove(task.getHash());
					it.remove();
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
						currentTasks.remove(task.getHash());
						log.debug("Removing task {} from queue", task, null);
						it.remove();
					} else if ( ! currentTenants.contains(  task.getTenantId() ) )
					{
						task.reject();
						pushTaskToPersist(task);
						currentTasks.remove(task.getHash());
						log.debug("Removing task {} from queue", task, null);
						it.remove();
					}
				}
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
			thl.setComplete(true);
			task.getLogs().set(taskDispatcher.getInternalId(), thl);
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

		// Verificar si ha de cancellar la tasca
		boolean allOk = true;
		StringBuffer message = new StringBuffer();
		String status = "P";
		for (DispatcherHandler dh : getTaskGenerator().getDispatchers())
		{
			if ((dh != null) && dh.isActive())
			{
				if ((task.getTask().getSystemName() == null) ||
						task.getTask().getSystemName()
							.equals(dh.getSystem().getName()))
				{
					if (task.getLogs().size() <= dh.getInternalId())
					{
						allOk = false;
					}
					
					else
					{
						TaskHandlerLog tasklog = task.getLogs().get(dh.getInternalId());
						if (tasklog == null)
						{
							allOk = false;
						}
						else if (!tasklog.isComplete())
						{
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

		// //////////////// TASK COMPLETE
		if (allOk)
		{
			ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
			Hashtable<String, TaskHandler> currentTasks = getCurrentTasks();

			task.cancel();
			pushTaskToPersist(task);
			currentTasks.remove(task.getHash());
			synchronized (taskList)
			{
				LinkedList<TaskHandler> queue = taskList.get(task.getPriority());
				queue.remove(task);
			}

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
			ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
			Hashtable<String, TaskHandler> currentTasks = getCurrentTasks();

			pushTaskToPersist(task);
			currentTasks.remove(task.getHash());
			synchronized (taskList)
			{
				LinkedList<TaskHandler> queue = taskList.get(task.getPriority());
				queue.remove(task);
			}
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
	protected boolean handleIsEmpty () throws Exception
	{
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		for (Iterator<LinkedList<TaskHandler>> it = taskList.iterator(); it.hasNext();)
		{
			if (!it.next().isEmpty())
				return false;
		}
		return true;
	}

	@Override
	protected Iterator handleGetIterator () throws Exception
	{
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		return new TaskQueueIterator(taskList);
	}

	@Override
	protected void handleCancelTask (long taskId) throws Exception
	{
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		Task taskToDelete = null;
		synchronized (taskList)
		{
			TaskQueueIterator it = new TaskQueueIterator(taskList);
			while (it.hasNext())
			{
				TaskHandler task = it.next();
				if (task.getTask().getId().longValue() == taskId)
				{
					task.cancel();
					pushTaskToPersist(task);
					it.remove();
					break;
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

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.TaskQueueBase#handleProcessOBTask(es.caib.seycon.ng.sync.engine.TaskHandler)
	 */
	@SuppressWarnings ("rawtypes")
	@Override
	protected Map handleProcessOBTask (TaskHandler task) throws Exception
	{
		Map<String, Exception> m = new HashMap<String, Exception>();

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
			if (dispatcher.isActive() && (
					task.getTask().getServer() == null || 
					dispatcher.getSystem().getName().equals (task.getTask().getServer())))
			{
				if (dispatcher.isConnected())
				{
    				try {
    					dispatcher.processOBTask(task);
    				} catch (Exception e) {
    					m.put(dispatcher.getSystem().getName(), e);
    				}
				}
				else if (dispatcher.getConnectException() != null)
				{
					m.put(dispatcher.getSystem().getName(), dispatcher.getConnectException());
				}
			}
		}
		if (!m.isEmpty())
		{
			Task tasca = task.getTask();
			tasca.setTaskDate(Calendar.getInstance());
			TaskEntity tasque = getTaskEntityDao().taskToEntity(tasca);
			getTaskEntityDao().create(tasque);
			tasca.setId(tasque.getId());
			addTask(task);
		}
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
		if (newTask.isRejected())
		{
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
	    			// Ignore
	    		}
	    		else if (newTask.isComplete())
	    		{
	    			if (tasque != null)
	    			{
	        			tlDao.remove(tasque.getLogs());
	        			dao.remove(tasque);
	    			}
	    		}
	    		else
	    		{
	    			tasque.setStatus(newTask.getTask().getStatus());
	    			tasque.setHash(newTask.getTask().getHash());
	    			tasque.setServer(newTask.getTask().getServer());
	    			tasque.setPriority(new Long(newTask.getPriority()));
	    			dao.update(tasque);
	    
	    			Collection<TaskLogEntity> daoEntities = tasque.getLogs();
	    			if (newTask.getLogs() != null)
	    			{
	        			for (TaskHandlerLog log : newTask.getLogs()) {
	        				if (log != null)
	        				{
	                            boolean found = false;
	                            for (TaskLogEntity logEntity : daoEntities) {
	                                if (log.getDispatcher() != null &&
	                                		logEntity.getSystem().getId().equals(log.getDispatcher().getSystem().getId())) {
	                                    found = true;
	                                    persistLog(tasque, log, logEntity);
	                                    break;
	                                }
	                            }
	                            if (!found) {
	                                TaskLogEntity logEntity = tlDao.newTaskLogEntity();
	                                persistLog(tasque, log, logEntity);
	                            }
	        				}
                        }
	    			}
	    		}
			} catch (Exception e) {
	    		newTask.setChanged(true);
				tasksToPersist.addFirst(newTask);
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
		ArrayList<LinkedList<TaskHandler>> taskList = getTasksList();
		synchronized (tasksToPersist)
		{
			for (TaskHandler t: tasksToPersist)
			{
				if (t.getTask().getId().longValue() == taskId)
					return t;
			}
		}
		synchronized (taskList)
		{
			TaskQueueIterator it = new TaskQueueIterator(taskList);
			while (it.hasNext())
			{
				TaskHandler task = it.next();
				if (task.getTask().getId().longValue() == taskId)
				{
					return task;
				}
			}
		}
		return null;
	}
}

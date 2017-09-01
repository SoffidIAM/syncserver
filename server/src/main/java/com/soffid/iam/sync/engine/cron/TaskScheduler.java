/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.ScheduledTaskHandler;
import com.soffid.iam.config.Config;
import com.soffid.iam.service.ScheduledTaskService;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Configuracio;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.servei.ConfiguracioService;
import it.sauronsoftware.cron4j.Scheduler;

/**
 * @author bubu
 *
 */
public class TaskScheduler
{
	 Set<Long> runningTasks = new HashSet<Long>();

	private final class ScheduledTaskRunnable implements Runnable
	{
		/**
		 * 
		 */
		private final TaskHandler handler;
		/**
		 * 
		 */
		private final ScheduledTaskService taskSvc;
		/**
		 * 
		 */
		private final ScheduledTask task;
		private boolean spawnThread;

		/**
		 * @param handler
		 * @param taskSvc
		 * @param task
		 */
		private ScheduledTaskRunnable (TaskHandler handler,
						ScheduledTaskService taskSvc, ScheduledTask task,
						boolean spawnThread)
		{
			this.handler = handler;
			this.taskSvc = taskSvc;
			this.task = task;
			this.spawnThread = spawnThread;
		}

		public void run ()
		{
			Runnable runnable = new Runnable () {
				public void run() {
					Security.nestedLogin(task.getTenant(),
							hostName,
							Security.ALL_PERMISSIONS);
					try {
						boolean ignore = false;
						try
						{
							synchronized (runningTasks) {
								if (runningTasks.contains(task.getId()))
									ignore = true;
								else
									runningTasks.add(task.getId());
							}
							if (!ignore) {
								log.info("Executing task " + task.getName());
								taskSvc.registerStartTask(task);
								task.setError(false);
								handler.setTask(task);
								handler.run();
								log.info("Task finished");
							} else 
							{
								log.info("Not executing task "+task.getName()+" as a previous instance is already running");
							}
						} catch (Throwable e) 
						{
							if (! ignore)
							{
								task.setError(true);
								task.getLastLog()
									.append("\nError executing task: ")
									.append(e.toString())
									.append("\n\nStack trace:\n")
									.append(SoffidStackTrace.getStackTrace(e));
								log.info("Finished task " + task.getName()+" with error:", e);
							}
						} finally {
							if (!ignore) {
								synchronized (runningTasks) {
									runningTasks.remove(task.getId());
								}
							}
		
						}
	      
						try
						{
							if (! ignore)
								taskSvc.registerEndTask(task);
						}
						catch (InternalErrorException e)
						{
							log.warn("Error registering scheduled task result ",e);
						}
					} finally {
						Security.nestedLogoff();
					}
				}
			};
			if (spawnThread)
			{
				Thread nt = new Thread ( runnable);
				nt.setName(task.getName());
				nt.setDaemon(true);
				nt.start();
			}
			else
			{
				runnable.run();
			}
		}
	}

	static TaskScheduler theScheduler = null ;
	String configTimeStamp;
	Log log = LogFactory.getLog(getClass());
	String hostName;
	
	Scheduler cronScheduler = new Scheduler();
	
	public static TaskScheduler getScheduler ()
	{
		if (theScheduler == null)
		{
			theScheduler = new TaskScheduler();
		}
		return theScheduler;
	}
	
	public void runNow (ScheduledTask task) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InternalErrorException
	{
		final ScheduledTaskService taskSvc = ServiceLocator.instance().getScheduledTaskService();
		
		for (ScheduledTaskHandler stk: taskSvc.listHandlers())
		{
			if (stk.getName().equals (task.getHandlerName()))
			{
				TaskHandler handlerObject = null;
				try
				{
					handlerObject = (TaskHandler) ServiceLocator.instance().getService(stk.getClassName());
				} catch (NoSuchBeanDefinitionException e)
				{
					Class<?> cl = Class.forName(stk.getClassName());
					handlerObject = (TaskHandler) cl.newInstance();
				}
				final TaskHandler h = handlerObject;
				Runnable r = new ScheduledTaskRunnable(h, taskSvc, task, false);
				r.run();
			}
		}
	}
	
	public List<ScheduledTask> getTasks () throws ClassNotFoundException, InstantiationException, IllegalAccessException, InternalErrorException, FileNotFoundException, IOException
	{
		final ScheduledTaskService taskSvc = ServiceLocator.instance().getScheduledTaskService();
		
		List<ScheduledTask> list = taskSvc.listTasks();
		List<ScheduledTask> result = new LinkedList<ScheduledTask>();
		String hostName = Config.getConfig().getHostName();
		
		for (final ScheduledTask task: list)
		{
			if (task.isEnabled() &&
						(task.getServerName() == null || 
							task.getServerName().equals("*") || 
							task.getServerName().equals (hostName)))
			{
				result.add (task);
			}
		}
		return result;
	}

	public void init () throws InternalErrorException, FileNotFoundException, IOException
	{
		final ScheduledTaskService taskSvc = ServiceLocator.instance().getScheduledTaskService();
		List<ScheduledTask> list = taskSvc.listTasks();
		
		String hostName = Config.getConfig().getHostName();
		
		for (final ScheduledTask task: list)
		{
			if (task.isActive() && task.getServerName().equals (hostName))
			{
				task.setActive(false);
				task.setError(true);
				StringBuffer sb = new StringBuffer();
				sb.append ("Server restarted during execution");
				task.setLastLog(sb);
				task.setLastEnd(Calendar.getInstance());
				taskSvc.registerEndTask(task);
			}
		}
		reconfigure ();
	}
	
	public void reconfigure () throws InternalErrorException, FileNotFoundException, IOException
	{
		hostName = Config.getConfig().getHostName();
		Scheduler newCronScheduler = new Scheduler();
		newCronScheduler.setDaemon(true);
		final ScheduledTaskService taskSvc = ServiceLocator.instance().getScheduledTaskService();
		final ConfiguracioService configSvc = ServiceLocator.instance().getConfiguracioService();
		
		Configuracio config = configSvc.findParametreByCodiAndCodiXarxa("soffid.schedule.timeStamp", null);
		if (config == null)
			return;
		
		if (config.getValor().equals(configTimeStamp))
			return;
		
		configTimeStamp = config.getValor();
		
		Map<String, ScheduledTaskHandler> handlers = new HashMap<String, ScheduledTaskHandler>();
		for (ScheduledTaskHandler handler: taskSvc.listHandlers())
		{
			handlers.put(handler.getName(), handler);
		}
		
		List<ScheduledTask> list = taskSvc.listTasks();
		
		String hostName = Config.getConfig().getHostName();
		
		for (final ScheduledTask task: list)
		{
			if (task.isEnabled() &&
						(task.getServerName() == null || 
							task.getServerName().equals("*") || 
							task.getServerName().equals (hostName)))
			{
    			try {
    				ScheduledTaskHandler handler = handlers.get(task.getHandlerName());
    				if (handler == null)
    					throw new InternalErrorException ("Unknown handler "+task.getHandlerName());
    				TaskHandler handlerObject = null;
    				try
    				{
    					handlerObject = (TaskHandler) ServiceLocator.instance().getService(handler.getClassName());
    				} catch (NoSuchBeanDefinitionException e)
    				{
    					Class<?> cl = Class.forName(handler.getClassName());
    					handlerObject = (TaskHandler) cl.newInstance();
    				}
    				final TaskHandler h = handlerObject;
    				Runnable r = new ScheduledTaskRunnable(h, taskSvc, task, true);
    				newCronScheduler.schedule(task.getMinutesPattern()+" "+task.getHoursPattern()+
    								" "+task.getDayPattern()+" "+task.getMonthsPattern()+
    								" "+task.getDayOfWeekPattern(), r);
    			} 
    			catch (Exception e)
    			{
    				task.setLastExecution(Calendar.getInstance());
    				task.setError(true);
    				task.setLastLog(new StringBuffer("Error creating handler: "). append(e.toString()));
    				taskSvc.registerEndTask(task);
    			}
			}
			
		}
		
		newCronScheduler.schedule("0,5,10,15,20,25,30,35,40,45,50,55 * * * *", new UpdateAgentsTask());
		newCronScheduler.schedule("1,6,11,16,21,26,31,36,41,46,51,56 * * * *", new RenewAuthTokenTask());

		if (cronScheduler != null && cronScheduler.isStarted())
			cronScheduler.stop();
		cronScheduler = newCronScheduler;
		cronScheduler.start();
	}
}

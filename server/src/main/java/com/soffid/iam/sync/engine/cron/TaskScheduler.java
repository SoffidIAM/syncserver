/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.ScheduledTaskHandler;
import com.soffid.iam.service.ScheduledTaskService;
import com.soffid.iam.service.TaskHandler;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Configuracio;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.ConfiguracioService;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;

import it.sauronsoftware.cron4j.Scheduler;

/**
 * @author bubu
 *
 */
public class TaskScheduler
{
	/**
	 * @author bubu
	 *
	 */
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

		/**
		 * @param handler
		 * @param taskSvc
		 * @param task
		 */
		private ScheduledTaskRunnable (TaskHandler handler,
						ScheduledTaskService taskSvc, ScheduledTask task)
		{
			this.handler = handler;
			this.taskSvc = taskSvc;
			this.task = task;
		}

		public void run ()
		{
			try {
				if (!ConnectionPool.isThreadOffline())
				{
					try
					{
						log.info("Executing task " +task.getName());
						taskSvc.registerStartTask(task);
						task.setError(false);
						handler.setTask(task);
						handler.run();
					} catch (Exception e) 
					{
						task.setError(true);
						task.getLastLog()
							.append("\nError executing task: ")
							.append(e.toString());
					}
      
					try
					{
						taskSvc.registerEndTask(task);
					}
					catch (InternalErrorException e)
					{
						log.warn("Error registering scheduled task result ",e);
					}
				}
			} catch (Exception e) {}
		}
	}

	static TaskScheduler theScheduler = null ;
	String configTimeStamp;
	Log log = LogFactory.getLog(getClass());
	
	Scheduler cronScheduler = new Scheduler();
	
	public static TaskScheduler getScheduler ()
	{
		if (theScheduler == null)
		{
			theScheduler = new TaskScheduler();
		}
		return theScheduler;
	}
	
	public void reconfigure () throws InternalErrorException, FileNotFoundException, IOException
	{
		Scheduler newCronScheduler = new Scheduler();
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
    				Runnable r = new ScheduledTaskRunnable(h, taskSvc, task);
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
		newCronScheduler.schedule("1,11,21,31,41,51 * * * *", new RenewAuthTokenTask());

		if (cronScheduler != null && cronScheduler.isStarted())
			cronScheduler.stop();
		cronScheduler = newCronScheduler;
		cronScheduler.start();
	}
}

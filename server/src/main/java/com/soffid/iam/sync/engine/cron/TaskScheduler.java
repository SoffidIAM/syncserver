/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
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
import com.soffid.iam.doc.api.CRLFPrintWriter;
import com.soffid.iam.doc.api.DocumentOutputStream;
import com.soffid.iam.doc.exception.DocumentBeanException;
import com.soffid.iam.doc.service.DocumentService;
import com.soffid.iam.service.ScheduledTaskService;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.engine.cert.UpdateCertsTask;
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
		private PrintWriter out;
		private boolean force;

		/**
		 * @param handler
		 * @param taskSvc
		 * @param task
		 * @param printWriter 
		 */
		private ScheduledTaskRunnable (TaskHandler handler,
						ScheduledTaskService taskSvc, ScheduledTask task,
						boolean spawnThread, PrintWriter printWriter, boolean force)
		{
			this.handler = handler;
			this.taskSvc = taskSvc;
			this.task = task;
			this.spawnThread = spawnThread;
			this.out = printWriter;
			this.force = force;
		}

		public void run ()
		{
			Runnable runnable = new Runnable () {
				public void run() {
					try {
						if (! force && !
							ServiceLocator.instance().getTaskQueue().isBestServer())
							return;
					} catch (InternalErrorException e1) {
						log.warn("Error guessing the best server instance");
						return;
					}
					PrintWriter actualOut = null;
					DocumentService ds = null;
					Security.nestedLogin(task.getTenant(),
							hostName,
							Security.ALL_PERMISSIONS);

					try {
						boolean ignore = false;
						String tx = null;
						try
						{
							ServiceLocator.instance().getTaskGenerator().startVirtualSourceTransaction();
							synchronized (runningTasks) {
								if (runningTasks.contains(task.getId()))
									ignore = true;
								else
									runningTasks.add(task.getId());
							}
							ScheduledTask t = findTask(task.getId());
							if (t.isActive())
								ignore = true;
							if (!ignore) {
								log.info("Executing task " + task.getName());
								taskSvc.registerStartTask(task);
								task.setError(false);
								handler.setTask(task);

								ds = ServiceLocator.instance().getDocumentService();
								ds.createDocument("text/plain", task.getName(), "taskmgr");
								actualOut = new PrintWriter(
										new DocumentOutputStream(ds)
										);
//								if (out != null)
//									actualOut = new SplitPrintWriter(out, actualOut);

								handler.run(actualOut);
								log.info("Task " + task.getName()+ " finished");
							} 
							else 
							{
								log.info("Not executing task "+task.getName()+" as a previous instance is already running");
							}
						} catch (Throwable e) 
						{
							if (! ignore)
							{
								task.setError(true);
								if (actualOut != null)
								{
									actualOut.println();
									actualOut.print("Error executing task: ");
									actualOut.println(e.toString());
									actualOut.println();
									actualOut.println("Stack trace:");
									SoffidStackTrace.printStackTrace(e, actualOut);
								}
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
							log.info("Sending log");
							if (actualOut != null)
							{
								actualOut.close();
								task.setLogReferenceID( ds.getReference().toString() );
							}
							if (out != null)
								out.close();
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
	
	public void runNow (ScheduledTask task, PrintWriter printWriter, boolean sync) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InternalErrorException
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
				Runnable r = new ScheduledTaskRunnable(h, taskSvc, task, ! sync,
						printWriter, true);
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

	public ScheduledTask findTask (long id) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InternalErrorException, FileNotFoundException, IOException
	{
		final ScheduledTaskService taskSvc = ServiceLocator.instance().getScheduledTaskService();
		
		List<ScheduledTask> list = taskSvc.listTasks();
		List<ScheduledTask> result = new LinkedList<ScheduledTask>();
		String hostName = Config.getConfig().getHostName();
		
		for (final ScheduledTask task: list)
		{
			if (task.getId().equals(id))
			{
				return task;
			}
		}
		return null;
	}

	public void init () throws InternalErrorException, FileNotFoundException, IOException, DocumentBeanException
	{
		final ScheduledTaskService taskSvc = ServiceLocator.instance().getScheduledTaskService();
		List<ScheduledTask> list = taskSvc.listServerTasks(Config.getConfig().getHostName());
		
		String hostName = Config.getConfig().getHostName();
		
		for (final ScheduledTask task: list)
		{
			if (task.isActive() && task.getServerName().equals (hostName))
			{
				Security.nestedLogin(task.getTenant(), hostName, Security.ALL_PERMISSIONS);
				try {
					task.setActive(false);
					task.setError(true);
					DocumentService ds = ServiceLocator.instance().getDocumentService();
					ds.createDocument("text/plain", task.getName(), "taskmgr");
					ds.openUploadTransfer();
					byte[] sb = ("Server restarted during execution").getBytes();
					ds.nextUploadPackage(sb, sb.length);
					ds.endUploadTransfer();
					task.setLogReferenceID( ds.getReference().toString() );
					task.setLastEnd(Calendar.getInstance());
					taskSvc.registerEndTask(task);
				} finally {
					Security.nestedLogoff();
				}
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
		
		List<ScheduledTask> list = taskSvc.listServerTasks(hostName);
		
		String hostName = Config.getConfig().getHostName();
		
		for (final ScheduledTask task: list)
		{
			if (task.isEnabled() &&
						(task.getServerName() == null || 
							task.getServerName().equals("*") || 
							task.getServerName().equals (hostName)))
			{
				Security.nestedLogin(task.getTenant(), hostName, Security.ALL_PERMISSIONS);
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
    				Runnable r = new ScheduledTaskRunnable(h, taskSvc, task, true, null, false);
    				newCronScheduler.schedule(task.getMinutesPattern()+" "+task.getHoursPattern()+
    								" "+task.getDayPattern()+" "+task.getMonthsPattern()+
    								" "+task.getDayOfWeekPattern(), r);
    			} 
    			catch (Exception e)
    			{
    				task.setLogReferenceID(null);
					try {
						DocumentService ds = ServiceLocator.instance().getDocumentService();
						ds.createDocument("text/plain", task.getName(), "taskmgr");
						PrintWriter out;
						out = new CRLFPrintWriter(
								new DocumentOutputStream(ds)
								);
	    				out.println("Error creating handler: ");
	    				SoffidStackTrace.printStackTrace(e, out);
	    				out.close();
	    				task.setLogReferenceID(ds.getReference().getId());
					} catch (DocumentBeanException e1) {
						log.warn("Error generating log file", e1);
					}

    				task.setLastExecution(Calendar.getInstance());
    				task.setError(true);
    				taskSvc.registerEndTask(task);
    			} finally {
    				Security.nestedLogoff();
    			}
			}
			
		}
		
		newCronScheduler.schedule("0,5,10,15,20,25,30,35,40,45,50,55 * * * *", new UpdateAgentsTask());
		newCronScheduler.schedule("1,6,11,16,21,26,31,36,41,46,51,56 * * * *", new RenewAuthTokenTask());
		newCronScheduler.schedule("0 5 * * *", new UpdateCertsTask());

		if (cronScheduler != null && cronScheduler.isStarted())
			cronScheduler.stop();
		cronScheduler = newCronScheduler;
		cronScheduler.start();
	}
}

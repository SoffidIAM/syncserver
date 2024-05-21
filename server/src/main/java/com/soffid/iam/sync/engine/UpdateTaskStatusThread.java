/**
 * 
 */
package com.soffid.iam.sync.engine;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.config.Config;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.service.TaskQueue;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class UpdateTaskStatusThread extends Thread
{

	@Override
	public void run ()
	{
		String hostname;
		try {
			hostname = Config.getConfig().getHostName();
		} catch (IOException e2) {
			hostname = "Syncserver";
		}
		Log log = LogFactory.getLog(getClass());
        TaskQueue taskQueue = ServerServiceLocator.instance().getTaskQueue();
        while (true)
        {
    		TaskHandler task = null;
        	try {
        		task = taskQueue.peekTaskToPersist();
        		if (task != null)
        		{
	        		persistTask(hostname, taskQueue, task);
        		} else {
   					Thread.sleep(2000);
        		}
        	} catch (Throwable e)
        	{
        		try {
	        		log.warn("Error on update tasks thread", e);
	        		if (task != null && task.getTask() != null)
	        		{
	        			notifyError(log, task);
	        		}
        		} catch (Exception e2) {
        			
        		}
        		try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {
				}
        	}
        }
	}

	protected void notifyError(Log log, TaskHandler task) {
		log.warn("Task details: "+task.getTask().toString());
		if (task.getLogs() != null)
		{
			for (TaskHandlerLog tl : task.getLogs())
			{
				if (tl != null && ! tl.isComplete())
				{
					StringBuffer sb = new StringBuffer();
					sb.append (">> ");
					if (tl.getDispatcher() != null && tl.getDispatcher().getSystem() != null)
						sb.append (tl.getDispatcher().getSystem().getName());
					else
						sb.append ("Unknown dispatcher");
					sb.append (": ");
					if (tl.isComplete())
						sb.append ("DONE");
					else 
					{
						sb.append ("ERROR ")
							.append (tl.getReason());
					}
							
					log.warn (sb.toString());
				}
			}
		}
	}

	protected void persistTask(String hostname, TaskQueue taskQueue, TaskHandler task) throws InternalErrorException {
		Security.nestedLogin(task.getTenant(),
				hostname,
				Security.ALL_PERMISSIONS);
		try {
			taskQueue.persistTask(task);
		} finally {
			Security.nestedLogoff();
			
		}
	}

	/**
	 * 
	 */
	public UpdateTaskStatusThread ()
	{
		setDaemon(true);
		setName("UpdateTaskStatus");
	}

}

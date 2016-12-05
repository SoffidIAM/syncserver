/**
 * 
 */
package es.caib.seycon.ng.sync.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.TaskQueue;

/**
 * @author bubu
 *
 */
public class UpdateTaskStatusThread extends Thread
{

	@Override
	public void run ()
	{
		Log log = LogFactory.getLog(getClass());
        TaskQueue taskQueue = ServerServiceLocator.instance().getTaskQueue();
        while (true)
        {
        	TaskHandler task = null;
        	try {
        		task = taskQueue.peekTaskToPersist();
        		taskQueue.persistTask(task);
        	} catch (Throwable e)
        	{
       			log.warn("Error on update tasks thread", e);
        		if (task != null && task.getTask() != null)
        		{
        			log.warn("Task details: "+task.getTask().toString());
        			if (task.getLogs() != null)
        			{
        				for (TaskHandlerLog tl : task.getLogs())
        				{
        					StringBuffer sb = new StringBuffer();
        					sb.append (">> ");
        					if (tl.getDispatcher() != null && tl.getDispatcher().getDispatcher() != null)
        						sb.append (tl.getDispatcher().getDispatcher().getCodi());
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
        		try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {
				}
        	}
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

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
        	try {
        		TaskHandler task = taskQueue.peekTaskToPersist();
        		taskQueue.persistTask(task);
        	} catch (Throwable e)
        	{
        		log.warn("Error on update tasks thread", e);
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

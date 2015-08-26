/**
 * 
 */
package com.soffid.iam.sync.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.service.TaskQueue;


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

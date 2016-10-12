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
		String[] permissions = new String[] {Security.AUTO_AUTHORIZATION_ALL};
		Log log = LogFactory.getLog(getClass());
        TaskQueue taskQueue = ServerServiceLocator.instance().getTaskQueue();
        while (true)
        {
        	try {
        		TaskHandler task = taskQueue.peekTaskToPersist();
        		Security.nestedLogin(task.getTenant(),
        				hostname,
        				permissions);
        		try {
        			taskQueue.persistTask(task);
        		} finally {
        			Security.nestedLogoff();
        			
        		}
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

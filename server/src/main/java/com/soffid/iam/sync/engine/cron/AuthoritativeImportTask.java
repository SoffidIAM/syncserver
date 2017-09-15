/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class AuthoritativeImportTask implements TaskHandler
{
	
	
	private ScheduledTask task;

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run (PrintWriter out) throws SQLException, InternalErrorException, IOException
	{
		TaskGenerator tg = ServiceLocator.instance().getTaskGenerator();
		boolean found = false;
		for (DispatcherHandler dispatcher: tg.getDispatchers())
		{
			if (dispatcher.getSystem().getId().toString().equals (task.getParams()))
			{
				found = true;
				dispatcher.doAuthoritativeImport(task, out);
			}
		}
		if (!found)
		{
			out.println(String.format("Dispatcher with id %s not found", task.getParams()));
		}
		
	}

	/* (non-Javadoc)
	 * @see com.soffid.iam.service.TaskHandler#setTask(com.soffid.iam.api.ScheduledTask)
	 */
	public void setTask (ScheduledTask task)
	{
		this.task = task;
	}

	/* (non-Javadoc)
	 * @see com.soffid.iam.service.TaskHandler#getTask()
	 */
	public ScheduledTask getTask ()
	{
		return task;
	}

}

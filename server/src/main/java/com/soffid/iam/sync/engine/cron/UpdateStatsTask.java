/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.io.PrintWriter;
import java.sql.SQLException;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class UpdateStatsTask implements TaskHandler
{
	
	
	private ScheduledTask task;

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run (PrintWriter out) throws SQLException, InternalErrorException
	{
		ServiceLocator.instance().getStatsService().updateStats();
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

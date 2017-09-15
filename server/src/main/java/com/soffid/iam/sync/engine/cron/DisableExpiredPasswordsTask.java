/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.io.PrintWriter;
import java.sql.SQLException;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.service.InternalPasswordService;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.db.ConnectionPool;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class DisableExpiredPasswordsTask implements TaskHandler
{
	
	
	private ScheduledTask task;

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run (PrintWriter out) throws SQLException, InternalErrorException
	{
	    InternalPasswordService ips = ServerServiceLocator.instance().getInternalPasswordService();
        
	    ips.disableExpiredPassword();
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

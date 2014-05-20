/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.sql.SQLException;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.service.TaskHandler;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;

/**
 * @author bubu
 *
 */
public class ExpireUntrustedPasswordsTask implements TaskHandler
{
	
	
	private ScheduledTask task;

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run () throws SQLException, InternalErrorException
	{
	    InternalPasswordService ips = ServerServiceLocator.instance().getInternalPasswordService();
        
	    ips.disableUntrustedPasswords();
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

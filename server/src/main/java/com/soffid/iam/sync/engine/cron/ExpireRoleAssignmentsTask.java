/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.sql.SQLException;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.db.ConnectionPool;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.AplicacioService;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.sync.servei.TaskGenerator;

/**
 * @author bubu
 *
 */
public class ExpireRoleAssignmentsTask implements TaskHandler
{
	
	
	private ScheduledTask task;

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run () throws InternalErrorException
	{
		AplicacioService appSvc = ServiceLocator.instance().getAplicacioService();
		appSvc.enableOrDisableAllOnDates();
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

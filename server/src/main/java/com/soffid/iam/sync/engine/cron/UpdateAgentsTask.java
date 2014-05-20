/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
public class UpdateAgentsTask implements Runnable
{
	Log log = LogFactory.getLog (getClass());

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run () 
	{
		try
		{
			if (!ConnectionPool.isThreadOffline())
				ServerServiceLocator.instance().getTaskGenerator().updateAgents();
		}
		catch (Exception e)
		{
			log.warn("Error updating agnets configuration", e);
		}
	}

}

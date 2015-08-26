/**
 * 
 */
package com.soffid.iam.sync.engine.cron;

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.db.ConnectionPool;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class RenewAuthTokenTask implements Runnable
{
	Log log = LogFactory.getLog(getClass());
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run () 
	{
		try
		{
			ServerServiceLocator.instance().getSecretConfigurationService().changeAuthToken();
    	}
    	catch (Exception e)
    	{
    		log.warn("Error updating agnets configuration", e);
    	}
	}

}

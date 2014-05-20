/**
 * 
 */
package es.caib.seycon.ng.sync.engine.db;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;

/**
 * @author bubu
 *
 */
public class OfflineDetectorThread extends Thread
{
	Log log = LogFactory.getLog(getClass());
	
	public OfflineDetectorThread()
	{
		setName ("Database monitor");
		setDaemon(true);
	}

	@Override
	public void run ()
	{
		ConnectionPool pool = ConnectionPool.getPool();
		Config config = null;
		try
		{
			config = Config.getConfig();
		}
		catch (Exception e)
		{
			log.fatal("Error getting configuration");
			System.exit(3);
		}
		while (true)
		{
			if (pool.isOfflineMode())
			{
				tryToGoOnline(pool, config);
			} else {
				testConnectionAlive (pool, config);
			}
			try
			{
				sleep(10000); // 10 seconds delay
			}
			catch (InterruptedException e)
			{
			} 
		}
	}

	/**
	 * @param pool
	 * @param config
	 */
	private void testConnectionAlive (ConnectionPool pool, Config config)
	{
		try {
			Connection conn = pool.getPoolConnection();
			conn.close ();
		} 
		catch (Exception e)
		{
			pool.setOffline();
			log.warn("Error connecting to database: "+ SoffidStackTrace.getStackTrace(e));
			log.warn("*****************************************");
			if (pool.isBootstrapMode())
				log.warn("* Switching Bootstrap to REPLICA database");
			else
				log.warn("*           Switching to REPLICA database");
			log.warn("*****************************************");
		}
	}

	private void tryToGoOnline (ConnectionPool pool, Config config)
	{
		try
		{
			Connection conn = pool.createDatabaseConnection(false);
			try {
				String type = pool.getDriverType(config.getDB());
				Statement stmt = conn.createStatement();
				try {
					stmt.executeQuery(pool.getDummyQuery(type)).close();
					if (pool.isBootstrapMode())
					{
						log.warn("***********************************************");
						log.warn("*   MASTER database is alive. Trying to recover");
						log.warn("***********************************************");
					}
					pool.setOnline();
					log.warn("*****************************************");
					if (pool.isBootstrapMode())
						log.warn("*    Switched Boostrap to MASTER database");
					else
						log.warn("*             Switched to MASTER database");
					log.warn("*****************************************");
				} finally {
					stmt.close();
				}
			} finally {
				conn.close();
			}
		}
		catch (Throwable e)
		{
			log.warn("Unable to connect to master database. System is working offline: "+e.getMessage());
		}
	}
	
	
}

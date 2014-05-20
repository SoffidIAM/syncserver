/**
 * 
 */
package es.caib.seycon.ng.sync.identity;

import java.sql.SQLException;

import org.hibernate.engine.SessionImplementor;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.identity.IdentityGeneratorThread;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool.ThreadBound;

/**
 * @author bubu
 *
 */
public class IdentityGeneratorBean extends es.caib.seycon.ng.model.identity.IdentityGeneratorBean
{
	IdentityGeneratorThread onlineThread = null;
	IdentityGeneratorThread offlineThread = null;

	protected void initializeThread ()
	{
		if (onlineThread == null)
		{
			onlineThread = new IdentityGeneratorThread(ThreadBound.MASTER);
			onlineThread.str1 = getStr1();
			onlineThread.str2 = getStr2();
			onlineThread.dataSource = getDataSource();
			onlineThread.tableName = getTableName();
			onlineThread.createTable = isCreateTable();
			onlineThread.setName("SoffidIdentityGenerator-MainDB");
			onlineThread.setDaemon(true);
			onlineThread.start();
		}
		if (offlineThread == null)
		{
			offlineThread = new IdentityGeneratorThread(ThreadBound.BACKUP);
			offlineThread.str1 = getStr1();
			offlineThread.str2 = getStr2();
			offlineThread.dataSource = getDataSource();
			offlineThread.tableName = getTableName();
			offlineThread.createTable = isCreateTable();
			offlineThread.setName("SoffidIdentityGenerator-BackupDB");
			offlineThread.setDaemon(true);
			offlineThread.start();
		}
		theBean = this;
	}

	public Long getNext (SessionImplementor session) throws SQLException, InterruptedException
	{
		try
		{
			if (ConnectionPool.isThreadOffline())
				return offlineThread.getNext(session);
			else
				return onlineThread.getNext(session);
		}
		catch (InternalErrorException e)
		{
			throw new SQLException(e);
		}
	}


}

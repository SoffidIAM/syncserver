/**
 * 
 */
package com.soffid.iam.sync.identity;

import java.sql.SQLException;

import org.hibernate.engine.SessionImplementor;

import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.db.ConnectionPool.ThreadBound;
import com.soffid.iam.sync.identity.IdentityGeneratorThread;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public class IdentityGeneratorBean extends com.soffid.iam.model.identity.IdentityGeneratorBean
{
	IdentityGeneratorThread onlineThread = null;

	protected void initializeThread ()
	{
		if (onlineThread == null)
		{
			onlineThread = new IdentityGeneratorThread();
			onlineThread.str1 = getStr1();
			onlineThread.str2 = getStr2();
			onlineThread.dataSource = getDataSource();
			onlineThread.tableName = getTableName();
			onlineThread.createTable = isCreateTable();
			onlineThread.setName("SoffidIdentityGenerator-MainDB");
			onlineThread.setDaemon(true);
			onlineThread.start();
		}
		theBean = this;
	}

	public Long getNext (SessionImplementor session) throws SQLException, InterruptedException
	{
		return onlineThread.getNext(session);
	}


}

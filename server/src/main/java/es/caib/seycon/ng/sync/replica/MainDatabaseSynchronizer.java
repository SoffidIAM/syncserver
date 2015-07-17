/**
 * 
 */
/**
 * 
 */
package es.caib.seycon.ng.sync.replica;

import com.soffid.iam.model.AccountEntityDao;
import com.soffid.iam.model.PasswordDomainEntityDao;
import com.soffid.iam.model.UserEntityDao;
import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.EstatContrasenya;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.servei.PasswordService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.bootstrap.QueryHelper;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.intf.DatabaseReplicaOfflineChangeRetriever;
import es.caib.seycon.ng.sync.intf.OfflineChange;
import es.caib.seycon.ng.sync.intf.OfflineDatabaseChange;
import es.caib.seycon.ng.sync.intf.OfflinePasswordChange;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;
import org.hibernate.classic.Session;

/**
 * @author bubu
 *
 */
public class MainDatabaseSynchronizer
{

	org.apache.commons.logging.Log log = LogFactory.getLog(getClass());
	DatabaseReplicaOfflineChangeRetriever agent;
	private InternalPasswordService ips;
	private AccountEntityDao accountEntityDao;
	private UserEntityDao usuariEntityDao;
	private PasswordDomainEntityDao dominiContrasenyaEntityDao;

	public MainDatabaseSynchronizer()
	{
		ips = ServiceLocator.instance().getInternalPasswordService(); 
		accountEntityDao = (AccountEntityDao) ServerServiceLocator.instance().getService("accountEntityDao");
		usuariEntityDao = (UserEntityDao) ServerServiceLocator.instance().getService("userEntityDao");
		dominiContrasenyaEntityDao = (PasswordDomainEntityDao) ServerServiceLocator.instance().getService("passwordDomainEntityDao");
	}
	
	public DatabaseReplicaOfflineChangeRetriever getAgent ()
	{
		return agent;
	}

	public void setAgent (DatabaseReplicaOfflineChangeRetriever agent)
	{
		this.agent = agent;
	}
	
	public void doSynchronize () throws SQLException, InternalErrorException
	{
		SessionFactory sessionFactory = (SessionFactory) ServiceLocator.instance().getService("sessionFactory");
		Connection conn = ConnectionPool.getPool().getPoolConnection();
		try {
    		if ( ConnectionPool.isThreadOffline())
    			return;
    		Long first = null;
    		List<OfflineChange> changes = new LinkedList<OfflineChange>();
    		List<OfflineChange> newChanges;
    		do 
    		{
    			newChanges = agent.getOfflineChanges(first);
    			changes.addAll( newChanges );
    			for (Iterator<OfflineChange> it = changes.iterator(); it.hasNext(); )
    			{
    				
    				OfflineChange change = it.next();
    				if (first == null || change.getId().longValue() >= first.longValue())
    					first = new Long (change.getId().longValue());
    				try {
        				if (change instanceof OfflineDatabaseChange)
        					doSynchronize (conn, (OfflineDatabaseChange) change);
        				else if (change instanceof OfflinePasswordChange)
        					doSynchronize (sessionFactory, conn, (OfflinePasswordChange) change);
        				agent.removeOfflineChange(change.getId());
        				it.remove();
    				} catch (Exception e) {
    					log.warn("Unable to apply offline change: "+e.toString());
    				}
    			}
    		} while (! newChanges.isEmpty());
		} finally {
			conn.close();
		}
	}

	/**
	 * @param sessionFactory 
	 * @param conn 
	 * @param change
	 * @throws InternalErrorException 
	 */
	private void doSynchronize (SessionFactory sessionFactory, Connection conn, OfflinePasswordChange change) throws InternalErrorException
	{
		Session session = sessionFactory.openSession();
		try {
    		if (change.getAccountId() != null)
    		{
    			
    			EstatContrasenya status = ips.getAccountPasswordsStatusById(change.getAccountId());
    			if (status == null || status.getData() == null || change.getDate().after(status.getData().getTime()))
    				ips.storeAndForwardAccountPasswordById(change.getAccountId(), change.getPassword(), change.isMustChange(), change.getExpiration());
    		}
    		else
    		{
    			EstatContrasenya status = ips.getPasswordsStatusById(change.getUserId(), change.getDomainId());
    			if (status == null || status.getData() == null || change.getDate().after(status.getData().getTime()))
    				ips.storeAndForwardPasswordById(change.getUserId(), change.getDomainId(), change.getPassword(), change.isMustChange());
    		}
		} finally {
			session.close();
		}
	}

	/**
	 * @param conn 
	 * @param change
	 * @throws SQLException 
	 */
	private void doSynchronize (Connection conn, OfflineDatabaseChange change) throws SQLException
	{
		QueryHelper qh = new QueryHelper(conn);
		if (change.getAction() == OfflineDatabaseChange.Action.DELETED_ROW)
		{
    		StringBuffer b = null;
    		if (change.getPrimaryKey() != null)
    		{
    			b = new StringBuffer("DELETE FROM ").append(change.getTable())
    							.append(" WHERE ").append (change.getPrimaryKey()).append("=?");
    			qh.execute (b.toString(), new Object [] {change.getPrimaryKeyValue()} );
    		}
		} else {
    		Object values2 [] = new Object[change.getColumns().size()+1];
    		StringBuffer b = null;
    		if (change.getPrimaryKey() != null)
    		{
    			b = new StringBuffer("UPDATE ").append(change.getTable());
    			for (int i = 0; i < change.getColumns().size(); i++)
    			{
    				if (i == 0)
    					b.append (" SET ");
    				else
    					b.append (", ");
    				b.append (change.getColumns().get(i))
    					.append ("=?");
    				values2[i] = change.getValues().get(i);
    			}
    			b.append(" WHERE ").append (change.getPrimaryKey()).append("=?");
    			values2[change.getColumns().size()] = change.getPrimaryKeyValue();
    		}
    		if (change.getPrimaryKey() == null || qh.executeUpdate (b.toString(), values2 ) == 0)
    		{
    			StringBuffer b2 = new StringBuffer(") VALUES (?");
    			StringBuffer b1 = new StringBuffer("INSERT INTO ").append(change.getTable())
    							.append (" (")
    							.append (change.getPrimaryKey());
    			values2[0] = change.getPrimaryKeyValue();
    			for (int i = 0; i < change.getColumns().size(); i++)
    			{
    				b1.append (", ");
    				b2.append (", ?");
    				b1.append (change.getColumns().get(i));
    				values2[i+1] = change.getValues().get(i);
    			}
    			b1.append (b2)
    				.append (")");
    			qh.executeUpdate (b1.toString(), values2 );
    		}
		}
	}
}

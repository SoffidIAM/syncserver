/**
 * 
 */
package es.caib.seycon.ng.sync.bootstrap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Properties;

import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.ServerRedirectException;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;

/**
 * @author bubu
 *
 */
public class ConfigurationManager
{
	public Properties getProperties (String serverName) throws InternalErrorException, SQLException, IOException, ServerRedirectException
	{
		final Properties prop = new Properties ();
		final Connection conn = ConnectionPool.getPool().getPoolConnection();
    	final QueryHelper qh = new QueryHelper(conn);
		final Config config = Config.getConfig();
		final Collection<String> servers = new LinkedList<String>(); 
		
    	try {
    		qh.select("SELECT CON_VALOR FROM SC_CONFIG WHERE CON_IDXAR IS NULL AND CON_CODI='seycon.https.port'",
    				new Object[0],
    				new QueryAction() {
						public void perform(ResultSet rset) throws SQLException, IOException {
							prop.setProperty(Config.PORT_PROPERTY, rset.getString(1));
						}
					});
    		qh.select("SELECT CON_VALOR FROM SC_CONFIG WHERE CON_IDXAR IS NULL AND CON_CODI='seycon.server.list'",
    	    				new Object[0],
    	    				new QueryAction() {
    							public void perform(ResultSet rset) throws SQLException, IOException {
    								prop.setProperty(Config.SERVERLIST_PROPERTY, rset.getString(1));
    							}
    						});
    		qh.select("SELECT SRV_USEMDB, SRV_DBA_ID, SRV_TYPE FROM SC_SERVER WHERE SRV_NOM=?",
					new Object[] {serverName},
					new QueryAction()
					{
						public void perform (ResultSet rset) throws SQLException, IOException
						{
							boolean useMainDataBase = rset.getBoolean(1);
							if (useMainDataBase )
							{
								if (config.getDB() == null) {
									servers.addAll(forwardToMainDBServer(conn, prop));
								} else {
									prop.put(Config.USER_PROPERTY, config.getDbUser());
									prop.put(Config.PASSWORD_PROPERTY, config.getPassword().toString());
									prop.put(Config.DB_PROPERTY, config.getDB());
								}
							}
							Long replicaId = rset.getLong(2);
							if (replicaId != null)
							{
								qh.select("SELECT RPL_USER, RPL_PASSWD, RPL_URL FROM SC_REPLDB WHERE RPL_ID=?", 
										new Object[] {replicaId},
										new QueryAction()
										{
											
											public void perform (ResultSet rset) throws SQLException, IOException
											{
												prop.setProperty(Config.BACKUPUSER_PROPERTY, rset.getString(1));
												prop.setProperty(Config.BACKUP_PASSWORD_PROPERTY, rset.getString(2));
												prop.setProperty(Config.BACKUPDB_PROPERTY, rset.getString(3));
											}
										});
							}
							prop.put(Config.ROL_PROPERTY, rset.getString(3));
						}

					});
    		if (! servers.isEmpty())
    			throw new ServerRedirectException(servers);
    	}
    	finally {
    		ConnectionPool.getPool().releaseConnection();
    	}
    	return prop;
	}

	private Collection<String> forwardToMainDBServer (Connection conn, Properties prop) throws SQLException, IOException
	{
		final Collection<String> servers = new LinkedList<String>(); 
    	QueryHelper qh = new QueryHelper(conn);
		qh.select("SELECT SRV_URL, SRV_MDB FROM SC_SERVER",
				new Object[0],
				new QueryAction()
				{
					public void perform (ResultSet rset) throws SQLException, IOException
					{
						String url = rset.getString(1);
						boolean useMainDataBase = rset.getBoolean(2);
						if (useMainDataBase)
						{
							servers.add(url);
						}
					}

				});
		return servers;
	}
}

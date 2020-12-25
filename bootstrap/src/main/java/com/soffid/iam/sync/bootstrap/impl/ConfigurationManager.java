/**
 * 
 */
package com.soffid.iam.sync.bootstrap.impl;

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

/**
 * @author bubu
 *
 */
public class ConfigurationManager
{
	Long masterTenant;
	
	public Properties getProperties (String serverName) throws SQLException, IOException
	{
		final Properties prop = new Properties ();
		final Connection conn = SQLConnectionFactory.getConnection();
    	final QueryHelper qh = new QueryHelper(conn);
		final Config config = Config.getConfig();
		final Collection<String> servers = new LinkedList<String>(); 
		
    	try {
    		qh.select("SELECT TEN_ID FROM SC_TENANT WHERE TEN_NAME='master'",
    				new Object[] {},
    				new QueryAction() {
						public void perform(ResultSet rset) throws SQLException, IOException {
							masterTenant = rset.getLong(1);
						}
					});
    		qh.select("select SRV_URL from SC_TENSER, SC_SERVER WHERE TNS_TEN_ID=? AND TNS_SRV_ID=SRV_ID AND SRV_TYPE='server'",
    	    				new Object[] {masterTenant},
    	    				new QueryAction() {
    							public void perform(ResultSet rset) throws SQLException, IOException {
    								if ( prop.getProperty(Config.SERVERLIST_PROPERTY) == null)
    									prop.setProperty(Config.SERVERLIST_PROPERTY, rset.getString(1));
    								else
    									prop.setProperty(Config.SERVERLIST_PROPERTY, 
    											prop.getProperty(Config.SERVERLIST_PROPERTY)+","+rset.getString(1));
    							}
    						});
    		qh.select("SELECT SRV_USEMDB, SRV_TYPE, SRV_JVMOPT, SRV_URL FROM SC_SERVER WHERE SRV_NOM=?",
					new Object[] {serverName},
					new QueryAction()
					{
						public void perform (ResultSet rset) throws SQLException, IOException
						{
							boolean useMainDataBase = rset.getBoolean(1);
							if (useMainDataBase )
							{
								if (config.getDB() != null) {
									prop.put(Config.USER_PROPERTY, config.getDbUser());
									prop.put(Config.PASSWORD_PROPERTY, config.getPassword().toString());
									prop.put(Config.DB_PROPERTY, config.getDB());
								}
							}
							prop.put(Config.ROL_PROPERTY, rset.getString(2));
							if (rset.getString(3) != null)
								prop.put(Config.JAVA_OPT_PROPERTY, rset.getString(3));
							else if ( "server".equals(rset.getString(2)))
								prop.put(Config.JAVA_OPT_PROPERTY, "-Xmx512m");
							else
								prop.put(Config.JAVA_OPT_PROPERTY, "-Xmx128m");
							prop.put(Config.URL_PROPERTY, rset.getString(4));
						}

					});
    	}
    	finally {
    		conn.close();
    	}
    	return prop;
	}

	private String fetchServers (Connection conn, Properties prop) throws SQLException, IOException
	{
		final StringBuffer servers = new StringBuffer(); 
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
							if (servers.length() > 0)
								servers.append(", ");
							servers.append(url);
						}
					}

				});
		return servers.toString();
	}
}

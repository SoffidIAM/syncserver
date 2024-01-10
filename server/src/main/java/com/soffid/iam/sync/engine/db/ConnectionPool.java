package com.soffid.iam.sync.engine.db;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Security;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EmptyStackException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.logging.Level;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.sync.engine.pool.AbstractPool;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.servei.TaskGenerator;

public class ConnectionPool extends AbstractPool<WrappedConnection> {
    private static ConnectionPool thePool = null;
    static java.util.logging.Logger log = java.util.logging.Logger.getLogger("DB-ConnectionPool");
	private boolean driversRegistered = false;
	static boolean debug = false;
	long lastEmptyTime = 0;
	private boolean oracle;
	
	public static ConnectionPool getPool() {
        if (thePool == null) {
        	debug = "true".equals(System.getenv("DBPOOL_DEBUG"));
        	try
			{
        		if (debug)
        			log.info("Starting connection pool");
				Config config = Config.getConfig();
	            thePool = new ConnectionPool();
	            if (System.getenv("DBPOOL_MIN_IDLE") != null) {
	            	thePool.setMinIdle(Integer.parseInt(System.getenv("DBPOOL_MIN_IDLE")));
	            }
	            if (System.getenv("DBPOOL_MAX_IDLE") != null) {
	            	thePool.setMaxIdle(Integer.parseInt(System.getenv("DBPOOL_MAX_IDLE")));
	            }
	            if (System.getenv("DBPOOL_MAX_IDLE_TIME") != null) {
	            	thePool.setMaxUnusedTime(Integer.parseInt(System.getenv("DBPOOL_MAX_IDLE_TIME")));
	            }
	            if (System.getenv("DBPOOL_MAX") != null) {
	            	thePool.setMaxSize(Integer.parseInt(System.getenv("DBPOOL_MAX")));
	            } else {
	            	thePool.setMaxSize(255);
	            }
	            if (System.getenv("DBPOOL_INITIAL") != null) {
	            	thePool.setMinSize(Integer.parseInt(System.getenv("DBPOOL_INITIAL")));
	            }
	            thePool.oracle = config.getDB().startsWith("jdbc:oracle:");
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
        }
        return thePool;
    }

    /**
	 * @param driver
	 * @return
	 */
	public String getDriverType (String driver)
	{
        String type = driver.substring(driver.indexOf(":")+1); //$NON-NLS-1$
        type = type.substring(0, type.indexOf(":")); //$NON-NLS-1$
        if (type.equals("mariadb")) type = "mysql";
        return type;
	}


	/**
	 * @param type
	 * @return
	 */
	public String getDummyQuery (String type)
	{
        if ("mysql".equals(type) || "postgresql".equals(type))  //$NON-NLS-1$
        {
        	return ("SELECT 1");
        } else if ("oracle".equals (type)) { //$NON-NLS-1$
        	return ("SELECT 1 FROM DUAL");
        } else if ("sqlserver".equals(type)) {
        	return ("SELECT 1 FROM sysobjects");
        } else {
            throw new RuntimeException("Unable to get dialect for database type ["+type+"]"); //$NON-NLS-1$ //$NON-NLS-2$
        }
	}


	@Override
	protected WrappedConnection createConnection() throws Exception {
		if (!driversRegistered ) {
			try {
				Class c = Class.forName("oracle.jdbc.driver.OracleDriver");
				DriverManager.registerDriver((java.sql.Driver) c.newInstance());
			} catch (Exception e) {
				log.log(Level.WARNING, "Error registering driver: " + e.getMessage(), e);
			}
			try {
				Class c = Class.forName("com.mysql.jdbc.Driver");
				DriverManager.registerDriver((java.sql.Driver) c.newInstance());
			} catch (Exception e) {
//				log.log(Level.WARNING, "Error registering driver: " + e.getMessage(), e);
			}
			try{
				Class c = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
				DriverManager.registerDriver((java.sql.Driver) c.newInstance());
			} catch (Exception e) {
				log.log(Level.WARNING, "Error registering driver: " + e.getMessage(), e);
			}
			try{
				Class c = Class.forName("org.postgresql.Driver");
				DriverManager.registerDriver((java.sql.Driver) c.newInstance());
			} catch (Exception e) {
				log.log(Level.WARNING, "Error registering driver: " + e.getMessage(), e);
			}
			driversRegistered = true;
		}
		Config config = Config.getConfig();
		// Connect to the database
		// You can put a database name after the @ sign in the connection URL.
		if (debug)
			log.fine("Connecting to "+config.getDB());
		WrappedConnection c = new WrappedConnection( 
				ConnectionPool.this,
				DriverManager.getConnection(
						config.getDB(), 
						config.getDbUser(),
						config.getPassword().getPassword()) );
		if (debug)
			log.fine("Connected "+c.hashCode());
		return c;
	}


	@Override
	protected void closeConnection(WrappedConnection connection) throws Exception {
		if (debug)
			log.fine("Closing "+connection.hashCode());
		connection.realClose();
	}

	public Connection getPoolConnection() throws InternalErrorException, SQLException {
		try {
			return getConnection();
		} catch (InternalErrorException e) {
			throw (InternalErrorException) e;
		} catch (SQLException e) {
			throw (SQLException) e;
		} catch (Exception e) {
			throw new InternalErrorException("Error getting connection", e);
		}
	}

	public void releaseConnection() {
		returnConnection();
	}

	public void releaseConnection(Connection c) {
		returnConnection();
	}

	@Override
	protected boolean isConnectionValid(WrappedConnection connection) throws Exception {
		if (debug)
			log.fine("Checking "+connection.hashCode());
		try {
			if (oracle) {
				try {
					Statement stmt = connection.createStatement();
					stmt.executeQuery("SELECT 1 FROM DUAL").close();
					stmt.close();
					return true;
				} catch (SQLException e) {
					log.fine("Connection "+connection.hashCode()+" is NOT valid "+e.getMessage());
					return false;
				}
			} else {
				boolean valid = connection.isValid(3);
				if (debug) {
					if (valid)
						log.fine("Connection "+connection.hashCode()+" is valid");
					else
						log.fine("Connection "+connection.hashCode()+" is NOT valid");
				}
				return valid;
			}
		} catch (Exception e) {
			if (debug)
				log.fine("Connection "+connection.hashCode()+" is ERROR");
			throw e;
		}
	}
	
	public void debug(String msg) {
		if (debug) {
			log.info(msg);
		}
	}

}

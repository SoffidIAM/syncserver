package com.soffid.iam.sync.engine.db;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import es.caib.seycon.ng.exception.InternalErrorException;

public class DataSource implements javax.sql.DataSource {

    PrintWriter logWriter;
    int loginTimeout;
    
    public PrintWriter getLogWriter() throws SQLException {
        return logWriter;
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
        logWriter = out;
    }

    public void setLoginTimeout(int seconds) throws SQLException {
        loginTimeout = seconds;
    }

    public int getLoginTimeout() throws SQLException {
        return loginTimeout;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public Connection getConnection() throws SQLException {
        try {
            return ConnectionPool.getPool().getConnection();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
		}
    }

    public Connection getConnection(String username, String password)
            throws SQLException {
        return getConnection();
        
    }

    public Logger getParentLogger() 
    {
    	return null;
    }
}

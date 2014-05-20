package es.caib.seycon.ng.sync.engine.db;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.caib.seycon.ng.comu.ReplicaDatabase;
import es.caib.seycon.ng.sync.engine.ReplicaConnection;

public class WrappedConnection implements Connection, ReplicaConnection {
    Connection wrappedConnection;
    Thread threadBound;
	private Logger log;
	boolean isMainDatabase;

    public Thread getThread() {
        return threadBound;
    }

    public void unsetThread() throws SQLException {
        checkThread();
        this.threadBound = null;
    	log.debug("Deassigned connection "+hashCode()+" to thread "+Thread.currentThread().getName()+"/"+Thread.currentThread().hashCode());
    }

    public void setThread() throws SQLException {
        checkThread();
        this.threadBound = Thread.currentThread();
    	log.debug("Assigned connection "+hashCode()+" to thread "+Thread.currentThread().getName()+"/"+Thread.currentThread().hashCode());
    }
    
    private void checkThread () throws SQLException {
        if (threadBound != null && ! threadBound.equals(Thread.currentThread()))
            throw new SQLException("SQL Connection shared between threads !!!");
    }
    

    public static List<WrappedPreparedStatement> openStatements;

    public WrappedConnection(Connection wrappedConnection, boolean isMainDatabase) {
        super();
        this.wrappedConnection = wrappedConnection;
        // Hacemos que esté sincronizada
        openStatements = Collections
                .synchronizedList(new LinkedList<WrappedPreparedStatement>());
    	log = LoggerFactory.getLogger(WrappedConnection.class);
    	this.isMainDatabase = isMainDatabase;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {// JSE6
    	if (iface == ReplicaConnection.class)
    		return (T) this;
    	else
    		return wrappedConnection.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {// JSE6
        return wrappedConnection.isWrapperFor(iface);
    }

    public Statement createStatement() throws SQLException {
        checkThread();
        return wrappedConnection.createStatement(); 
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkThread();
        WrappedPreparedStatement wstmt = new WrappedPreparedStatement(
                wrappedConnection.prepareStatement(sql));
        wstmt.openStatementSQL.add(sql); // Afegim el sql
        openStatements.add(wstmt);
        return wstmt;
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        checkThread();
        return wrappedConnection.prepareCall(sql);
    }

    public String nativeSQL(String sql) throws SQLException {
        checkThread();
        return wrappedConnection.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        wrappedConnection.setAutoCommit(autoCommit);
    }

    public boolean getAutoCommit() throws SQLException {
        return wrappedConnection.getAutoCommit();
    }

    public void commit() throws SQLException {
        checkThread();
        wrappedConnection.commit();
    }

    public void rollback() throws SQLException {
        checkThread();
        wrappedConnection.rollback();
    }

    public void realClose() throws SQLException {
        checkThread();
        wrappedConnection.close();
    }

    public boolean isClosed() throws SQLException {
        return wrappedConnection.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return wrappedConnection.getMetaData();
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        wrappedConnection.setReadOnly(readOnly);
    }

    public boolean isReadOnly() throws SQLException {
        return wrappedConnection.isReadOnly();
    }

    public void setCatalog(String catalog) throws SQLException {
        wrappedConnection.setCatalog(catalog);
    }

    public String getCatalog() throws SQLException {
        return wrappedConnection.getCatalog();
    }

    public void setTransactionIsolation(int level) throws SQLException {
        // wrappedConnection.setTransactionIsolation(level);
    }

    public int getTransactionIsolation() throws SQLException {
        return wrappedConnection.getTransactionIsolation();
    }

    public SQLWarning getWarnings() throws SQLException {
        return wrappedConnection.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        wrappedConnection.clearWarnings();
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency)
            throws SQLException {
        checkThread();
        return wrappedConnection.createStatement(resultSetType,
                resultSetConcurrency);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
            int resultSetConcurrency) throws SQLException {
        checkThread();
        WrappedPreparedStatement wstmt = new WrappedPreparedStatement(
                wrappedConnection.prepareStatement(sql, resultSetType,
                        resultSetConcurrency));
        wstmt.openStatementSQL.add(sql); // Afegim el sql
        return wstmt;
    }

    public CallableStatement prepareCall(String sql, int resultSetType,
            int resultSetConcurrency) throws SQLException {
        checkThread();
        return wrappedConnection.prepareCall(sql, resultSetType,
                resultSetConcurrency);
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return wrappedConnection.getTypeMap();
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        wrappedConnection.setTypeMap(map);
    }

    public void setHoldability(int holdability) throws SQLException {
        wrappedConnection.setHoldability(holdability);
    }

    public int getHoldability() throws SQLException {
        return wrappedConnection.getHoldability();
    }

    public Savepoint setSavepoint() throws SQLException {
        return wrappedConnection.setSavepoint();
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        return wrappedConnection.setSavepoint(name);
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        wrappedConnection.rollback(savepoint);
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        wrappedConnection.releaseSavepoint(savepoint);
    }

    public Statement createStatement(int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return wrappedConnection.createStatement(resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        checkThread();
        WrappedPreparedStatement wstmt = new WrappedPreparedStatement(
                wrappedConnection.prepareStatement(sql, resultSetType,
                        resultSetConcurrency, resultSetHoldability));
        wstmt.openStatementSQL.add(sql); // Afegim el sql
        return wstmt;
    }

    public CallableStatement prepareCall(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        checkThread();
        return wrappedConnection.prepareCall(sql, resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
            throws SQLException {
        checkThread();
        WrappedPreparedStatement wstmt = new WrappedPreparedStatement(
                wrappedConnection.prepareStatement(sql, autoGeneratedKeys));
        wstmt.openStatementSQL.add(sql); // Afegim el sql
        return wstmt;
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
            throws SQLException {
        checkThread();
        WrappedPreparedStatement wstmt = new WrappedPreparedStatement(
                wrappedConnection.prepareStatement(sql, columnIndexes));
        wstmt.openStatementSQL.add(sql); // Afegim el sql
        return wstmt;
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames)
            throws SQLException {

        checkThread();
        WrappedPreparedStatement wstmt = new WrappedPreparedStatement(
                wrappedConnection.prepareStatement(sql, columnNames));
        wstmt.openStatementSQL.add(sql); // Afegim el sql
        return wstmt;
    }

    public Clob createClob() throws SQLException {
        checkThread();
        return wrappedConnection.createClob();
    }

    public Blob createBlob() throws SQLException {
        checkThread();
        return wrappedConnection.createBlob();
    }

    public NClob createNClob() throws SQLException {
        checkThread();
        return wrappedConnection.createNClob();
    }

    public SQLXML createSQLXML() throws SQLException {
        return wrappedConnection.createSQLXML();
    }

    public boolean isValid(int timeout) throws SQLException {
        return wrappedConnection.isValid(timeout);
    }

    public void setClientInfo(String name, String value)
            throws SQLClientInfoException {
        wrappedConnection.setClientInfo(name, value);
    }

    public void setClientInfo(Properties properties)
            throws SQLClientInfoException {
        wrappedConnection.setClientInfo(properties);
    }

    public String getClientInfo(String name) throws SQLException {
        return wrappedConnection.getClientInfo(name);
    }

    public Properties getClientInfo() throws SQLException {
        return wrappedConnection.getClientInfo();
    }

    public Array createArrayOf(String typeName, Object[] elements)
            throws SQLException {
        return wrappedConnection.createArrayOf(typeName, elements);
    }

    public Struct createStruct(String typeName, Object[] attributes)
            throws SQLException {
        return wrappedConnection.createStruct(typeName, attributes);
    }

    /**
     * Obtenim el estat dels PreparedStatements que encara continuen oberts
     * 
     * @return
     */
    public static synchronized LinkedList<String> getOpenWrappedPreparedStatementsSQL() {
        LinkedList<String> openStmtsList = new LinkedList<String>();
        synchronized (openStatements) {
            int position = 0;
            for (Iterator it = openStatements.iterator(); it.hasNext();) {
                Object seguent = it.next();
                if (seguent != null) {
                    WrappedPreparedStatement wstmt = (WrappedPreparedStatement) seguent;

                    if (wstmt != null /* && !wstmt.isClosed() */) // TODO:
                                                                  // Statement.isClosed()
                                                                  // és de java
                                                                  // 6 (!!)
                        openStmtsList.add("#-#OpenStatement #" + (position++)/*
                                                                              * +": size= "
                                                                              * +
                                                                              * wstmt
                                                                              * .
                                                                              * getOpenStatements
                                                                              * (
                                                                              * )
                                                                              * .
                                                                              * size
                                                                              * (
                                                                              * )
                                                                              * +
                                                                              * " sql="
                                                                              */
                                + ": " + wstmt.getOpenStatements().toString());
                }
            }
        }
        return openStmtsList;
    }
    
    public void close() throws SQLException {
        ConnectionPool.getPool().releaseConnection();
    }

	/* (non-Javadoc)
	 * @see java.sql.Connection#setSchema(java.lang.String)
	 */
    // @Override
	public void setSchema (String schema) throws SQLException
	{
		try
		{
			Method m = wrappedConnection.getClass().getMethod("setSchema", new Class[] {String.class});
			m.invoke(wrappedConnection, schema);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}

	/**
	 * @return
	 * @throws SQLException
	 * @see java.sql.Connection#getSchema()
	 */
	public String getSchema () throws SQLException
	{
		Method m;
		try
		{
			m = wrappedConnection.getClass().getMethod("getSchema");
			return (String) m.invoke(wrappedConnection);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}

	/**
	 * @param executor
	 * @throws SQLException
	 * @see java.sql.Connection#abort(java.util.concurrent.Executor)
	 */
	public void abort (Executor executor) throws SQLException
	{
		try
		{
			Method m = wrappedConnection.getClass().getMethod("abort", new Class[] {Executor.class});
			m.invoke(wrappedConnection, executor);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}

	/**
	 * @param executor
	 * @param milliseconds
	 * @throws SQLException
	 * @see java.sql.Connection#setNetworkTimeout(java.util.concurrent.Executor, int)
	 */
	public void setNetworkTimeout (Executor executor, int milliseconds)
					throws SQLException
	{
		try
		{
			Method m = wrappedConnection.getClass().getMethod("setNetworkTimeout", new Class[] {Executor.class, Integer.class});
			m.invoke(wrappedConnection, executor, milliseconds);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}

	/**
	 * @return
	 * @throws SQLException
	 * @see java.sql.Connection#getNetworkTimeout()
	 */
	public int getNetworkTimeout () throws SQLException
	{
		Method m;
		try
		{
			m = wrappedConnection.getClass().getMethod("getNetworkTimeout");
			return (Integer) m.invoke(wrappedConnection);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.engine.ReplicaConnection#isMainDatabase()
	 */
	public boolean isMainDatabase ()
	{
		return isMainDatabase;
	}
}

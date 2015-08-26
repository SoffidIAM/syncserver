package com.soffid.iam.sync.engine.db;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.concurrent.Executor;

public class WrappedPreparedStatement implements PreparedStatement {
	
	PreparedStatement psmt;
	protected LinkedList<String> openStatementSQL;

	public WrappedPreparedStatement(PreparedStatement psmt) {
		super();
		this.psmt = psmt;
		openStatementSQL = new LinkedList<String>();
	}
	
	public ResultSet executeQuery(String sql) throws SQLException {
		openStatementSQL.add(sql);
		return psmt.executeQuery(sql);
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return psmt.unwrap(iface);
	}

	public ResultSet executeQuery() throws SQLException {
		return psmt.executeQuery();
	}

	public int executeUpdate(String sql) throws SQLException {
		openStatementSQL.add(sql);
		return psmt.executeUpdate(sql);
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return psmt.isWrapperFor(iface);
	}

	public int executeUpdate() throws SQLException {
		return psmt.executeUpdate();
	}

	public void close() throws SQLException {
		// Quitamos el sql correpondiente
		openStatementSQL.clear();
		try { // Eliminamos el statement de la lista de statements abiertos de las conexiones
			WrappedConnection.openStatements.remove(this);
		} catch (Throwable th) {}
		psmt.close();
	}

	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		psmt.setNull(parameterIndex, sqlType);
	}

	public int getMaxFieldSize() throws SQLException {
		return psmt.getMaxFieldSize();
	}

	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		psmt.setBoolean(parameterIndex, x);
	}

	public void setMaxFieldSize(int max) throws SQLException {
		psmt.setMaxFieldSize(max);
	}

	public void setByte(int parameterIndex, byte x) throws SQLException {
		psmt.setByte(parameterIndex, x);
	}

	public void setShort(int parameterIndex, short x) throws SQLException {
		psmt.setShort(parameterIndex, x);
	}

	public int getMaxRows() throws SQLException {
		return psmt.getMaxRows();
	}

	public void setInt(int parameterIndex, int x) throws SQLException {
		psmt.setInt(parameterIndex, x);
	}

	public void setMaxRows(int max) throws SQLException {
		psmt.setMaxRows(max);
	}

	public void setLong(int parameterIndex, long x) throws SQLException {
		psmt.setLong(parameterIndex, x);
	}

	public void setEscapeProcessing(boolean enable) throws SQLException {
		psmt.setEscapeProcessing(enable);
	}

	public void setFloat(int parameterIndex, float x) throws SQLException {
		psmt.setFloat(parameterIndex, x);
	}

	public int getQueryTimeout() throws SQLException {
		return psmt.getQueryTimeout();
	}

	public void setDouble(int parameterIndex, double x) throws SQLException {
		psmt.setDouble(parameterIndex, x);
	}

	public void setQueryTimeout(int seconds) throws SQLException {
		psmt.setQueryTimeout(seconds);
	}

	public void setBigDecimal(int parameterIndex, BigDecimal x)
			throws SQLException {
		psmt.setBigDecimal(parameterIndex, x);
	}

	public void cancel() throws SQLException {
		//TODO: eliminar statement?
		psmt.cancel();
	}

	public void setString(int parameterIndex, String x) throws SQLException {
		psmt.setString(parameterIndex, x);
	}

	public SQLWarning getWarnings() throws SQLException {
		return psmt.getWarnings();
	}

	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		psmt.setBytes(parameterIndex, x);
	}

	public void clearWarnings() throws SQLException {
		psmt.clearWarnings();
	}

	public void setDate(int parameterIndex, Date x) throws SQLException {
		psmt.setDate(parameterIndex, x);
	}

	public void setCursorName(String name) throws SQLException {
		psmt.setCursorName(name);
	}

	public void setTime(int parameterIndex, Time x) throws SQLException {
		psmt.setTime(parameterIndex, x);
	}

	public void setTimestamp(int parameterIndex, Timestamp x)
			throws SQLException {
		psmt.setTimestamp(parameterIndex, x);
	}

	public boolean execute(String sql) throws SQLException {
		openStatementSQL.add(sql);
		return psmt.execute(sql);
	}

	public void setAsciiStream(int parameterIndex, InputStream x, int length)
			throws SQLException {
		psmt.setAsciiStream(parameterIndex, x, length);
	}

	public ResultSet getResultSet() throws SQLException {
		return psmt.getResultSet();
	}

	public void setUnicodeStream(int parameterIndex, InputStream x, int length)
			throws SQLException {
		psmt.setUnicodeStream(parameterIndex, x, length);
	}

	public int getUpdateCount() throws SQLException {
		return psmt.getUpdateCount();
	}

	public boolean getMoreResults() throws SQLException {
		return psmt.getMoreResults();
	}

	public void setBinaryStream(int parameterIndex, InputStream x, int length)
			throws SQLException {
		psmt.setBinaryStream(parameterIndex, x, length);
	}

	public void setFetchDirection(int direction) throws SQLException {
		psmt.setFetchDirection(direction);
	}

	public void clearParameters() throws SQLException {
		psmt.clearParameters();
	}

	public int getFetchDirection() throws SQLException {
		return psmt.getFetchDirection();
	}

	public void setObject(int parameterIndex, Object x, int targetSqlType)
			throws SQLException {
		psmt.setObject(parameterIndex, x, targetSqlType);
	}

	public void setFetchSize(int rows) throws SQLException {
		psmt.setFetchSize(rows);
	}

	public int getFetchSize() throws SQLException {
		return psmt.getFetchSize();
	}

	public void setObject(int parameterIndex, Object x) throws SQLException {
		psmt.setObject(parameterIndex, x);
	}

	public int getResultSetConcurrency() throws SQLException {
		return psmt.getResultSetConcurrency();
	}

	public int getResultSetType() throws SQLException {
		return psmt.getResultSetType();
	}

	public void addBatch(String sql) throws SQLException {
		openStatementSQL.add(sql);
		psmt.addBatch(sql);
	}

	public void clearBatch() throws SQLException {
		psmt.clearBatch();
	}

	public boolean execute() throws SQLException {
		return psmt.execute();
	}

	public int[] executeBatch() throws SQLException {
		return psmt.executeBatch();
	}

	public void addBatch() throws SQLException {
		psmt.addBatch();
	}

	public void setCharacterStream(int parameterIndex, Reader reader, int length)
			throws SQLException {
		psmt.setCharacterStream(parameterIndex, reader, length);
	}

	public void setRef(int parameterIndex, Ref x) throws SQLException {
		psmt.setRef(parameterIndex, x);
	}

	public Connection getConnection() throws SQLException {
		return psmt.getConnection();
	}

	public void setBlob(int parameterIndex, Blob x) throws SQLException {
		psmt.setBlob(parameterIndex, x);
	}

	public void setClob(int parameterIndex, Clob x) throws SQLException {
		psmt.setClob(parameterIndex, x);
	}

	public boolean getMoreResults(int current) throws SQLException {
		return psmt.getMoreResults(current);
	}

	public void setArray(int parameterIndex, Array x) throws SQLException {
		psmt.setArray(parameterIndex, x);
	}

	public ResultSetMetaData getMetaData() throws SQLException {
		return psmt.getMetaData();
	}

	public ResultSet getGeneratedKeys() throws SQLException {
		return psmt.getGeneratedKeys();
	}

	public void setDate(int parameterIndex, Date x, Calendar cal)
			throws SQLException {
		psmt.setDate(parameterIndex, x, cal);
	}

	public int executeUpdate(String sql, int autoGeneratedKeys)
			throws SQLException {
		openStatementSQL.add(sql);
		return psmt.executeUpdate(sql, autoGeneratedKeys);
	}

	public void setTime(int parameterIndex, Time x, Calendar cal)
			throws SQLException {
		psmt.setTime(parameterIndex, x, cal);
	}

	public int executeUpdate(String sql, int[] columnIndexes)
			throws SQLException {
		openStatementSQL.add(sql);
		return psmt.executeUpdate(sql, columnIndexes);
	}

	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal)
			throws SQLException {
		psmt.setTimestamp(parameterIndex, x, cal);
	}

	public void setNull(int parameterIndex, int sqlType, String typeName)
			throws SQLException {
		psmt.setNull(parameterIndex, sqlType, typeName);
	}

	public int executeUpdate(String sql, String[] columnNames)
			throws SQLException {
		openStatementSQL.add(sql);
		return psmt.executeUpdate(sql, columnNames);
	}

	public boolean execute(String sql, int autoGeneratedKeys)
			throws SQLException {
		openStatementSQL.add(sql);
		return psmt.execute(sql, autoGeneratedKeys);
	}

	public void setURL(int parameterIndex, URL x) throws SQLException {
		psmt.setURL(parameterIndex, x);
	}

	public ParameterMetaData getParameterMetaData() throws SQLException {
		return psmt.getParameterMetaData();
	}

	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		psmt.setRowId(parameterIndex, x);
	}

	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		openStatementSQL.add(sql);
		return psmt.execute(sql, columnIndexes);
	}

	public void setNString(int parameterIndex, String value)
			throws SQLException {
		psmt.setNString(parameterIndex, value);
	}

	public void setNCharacterStream(int parameterIndex, Reader value,
			long length) throws SQLException {
		psmt.setNCharacterStream(parameterIndex, value, length);
	}

	public boolean execute(String sql, String[] columnNames)
			throws SQLException {
		openStatementSQL.add(sql);
		return psmt.execute(sql, columnNames);
	}

	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		psmt.setNClob(parameterIndex, value);
	}

	public void setClob(int parameterIndex, Reader reader, long length)
			throws SQLException {
		psmt.setClob(parameterIndex, reader, length);
	}

	public int getResultSetHoldability() throws SQLException {
		return psmt.getResultSetHoldability();
	}

	public void setBlob(int parameterIndex, InputStream inputStream, long length)
			throws SQLException {
		psmt.setBlob(parameterIndex, inputStream, length);
	}

	public boolean isClosed() throws SQLException {
		return psmt.isClosed();
	}

	public void setPoolable(boolean poolable) throws SQLException {
		psmt.setPoolable(poolable);
	}

	public void setNClob(int parameterIndex, Reader reader, long length)
			throws SQLException {
		psmt.setNClob(parameterIndex, reader, length);
	}

	public boolean isPoolable() throws SQLException {
		return psmt.isPoolable();
	}

	public void setSQLXML(int parameterIndex, SQLXML xmlObject)
			throws SQLException {
		psmt.setSQLXML(parameterIndex, xmlObject);
	}

	public void setObject(int parameterIndex, Object x, int targetSqlType,
			int scaleOrLength) throws SQLException {
		psmt.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
	}

	public void setAsciiStream(int parameterIndex, InputStream x, long length)
			throws SQLException {
		psmt.setAsciiStream(parameterIndex, x, length);
	}

	public void setBinaryStream(int parameterIndex, InputStream x, long length)
			throws SQLException {
		psmt.setBinaryStream(parameterIndex, x, length);
	}

	public void setCharacterStream(int parameterIndex, Reader reader,
			long length) throws SQLException {
		psmt.setCharacterStream(parameterIndex, reader, length);
	}

	public void setAsciiStream(int parameterIndex, InputStream x)
			throws SQLException {
		psmt.setAsciiStream(parameterIndex, x);
	}

	public void setBinaryStream(int parameterIndex, InputStream x)
			throws SQLException {
		psmt.setBinaryStream(parameterIndex, x);
	}

	public void setCharacterStream(int parameterIndex, Reader reader)
			throws SQLException {
		psmt.setCharacterStream(parameterIndex, reader);
	}

	public void setNCharacterStream(int parameterIndex, Reader value)
			throws SQLException {
		psmt.setNCharacterStream(parameterIndex, value);
	}

	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		psmt.setClob(parameterIndex, reader);
	}

	public void setBlob(int parameterIndex, InputStream inputStream)
			throws SQLException {
		psmt.setBlob(parameterIndex, inputStream);
	}

	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		psmt.setNClob(parameterIndex, reader);
	}
	
	
	public LinkedList<String> getOpenStatements() {
		return openStatementSQL;
	}

	/**
	 * @throws SQLException
	 * @see java.sql.Statement#closeOnCompletion()
	 */
	public void closeOnCompletion () throws SQLException
	{
		try
		{
			Method m = psmt.getClass().getMethod("closeOnCompletion");
			m.invoke(psmt);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}

	/**
	 * @return
	 * @throws SQLException
	 * @see java.sql.Statement#isCloseOnCompletion()
	 */
	public boolean isCloseOnCompletion () throws SQLException
	{
		try
		{
			Method m = psmt.getClass().getMethod("isCloseOnCompletion");
			return (Boolean) m.invoke(psmt);
		}
		catch (Exception e)
		{
			throw new SQLException(e);
		}
	}
	

}

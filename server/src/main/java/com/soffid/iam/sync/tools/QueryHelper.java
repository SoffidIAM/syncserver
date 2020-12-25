package com.soffid.iam.sync.tools;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Timestamp;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;

public class QueryHelper {
	Connection conn;
	
	boolean enableNullSqlObject  = false;

	List<String> columnNames;
	
    public List<String> getColumnNames() {
		return columnNames;
	}

	/**
	 * @return the enableNullSqlObject
	 */
	public boolean isEnableNullSqlObject ()
	{
		return enableNullSqlObject;
	}

	/**
	 * @param enableNullSqlObject the enableNullSqlObject to set
	 */
	public void setEnableNullSqlObject (boolean enableNullSqlObject)
	{
		this.enableNullSqlObject = enableNullSqlObject;
	}

	public void select (String sql, Object params[] , QueryAction query) throws SQLException, IOException
    {
    	PreparedStatement stmt = conn.prepareStatement(sql);
    	int num = 0;
    	for ( Object param: params) 
    	{
    		num ++;
    		if (param == null)
    		{
    			stmt.setNull(num, Types.VARCHAR);
    		} 
    		else if (param instanceof Long)
    		{
    			stmt.setLong(num, (Long) param);
    		}
    		else if (param instanceof Integer)
    		{
    			stmt.setInt(num, (Integer) param);
    		}
    		else if (param instanceof Date)
    		{
    			stmt.setDate(num, (Date) param);
    		}
    		else if (param instanceof java.sql.Timestamp)
    		{
    			stmt.setTimestamp(num, (java.sql.Timestamp) param);
    		}
    		else if (param instanceof Boolean)
    		{
    			stmt.setBoolean(num, (Boolean) param);
    		}
    		else 
    		{
    			stmt.setString(num, param.toString());
    		}
    	}
    	try {
    		ResultSet rset = stmt.executeQuery();
    		try {
	    		while (rset.next())
	    		{
	    			query.perform(rset);
	    		}
    		} finally {
    			rset.close ();
    		}
    	} finally {
    		stmt.close();
    	}
    }

    public List<Object[]> select (String sql, Object... params) throws SQLException
    {
    	return selectLimit (sql, null, params);
    }
    
	public List<Object[]> selectLimit (String sql, Long maxRows,
		Object... params) throws SQLException
	{
		columnNames = new LinkedList<String>();
		List<Object[]> result = new LinkedList<Object[]>();
		PreparedStatement stmt = conn.prepareStatement(sql);
		int num = 0;
		for (Object param : params)
		{
			num++;
			if (param == null)
			{
				stmt.setNull(num, Types.VARCHAR);
			}
			else if (param instanceof Long)
			{
				stmt.setLong(num, (Long) param);
			}
			else if (param instanceof Integer)
			{
				stmt.setInt(num, (Integer) param);
			}
			else if (param instanceof Boolean)
			{
				stmt.setBoolean(num, (Boolean) param);
			}
			else if (param instanceof Date)
			{
				stmt.setDate(num, (Date) param);
			}
    		else if (param instanceof java.sql.Timestamp)
    		{
    			stmt.setTimestamp(num, (java.sql.Timestamp) param);
    		}
			else
			{
				stmt.setString(num, param.toString());
			}
		}
		try
		{
			long rows = 0;
			ResultSet rset = stmt.executeQuery();
			try
			{
				int cols = rset.getMetaData().getColumnCount();
				for (int i = 0; i < cols; i++)
				{
					columnNames.add (rset.getMetaData().getColumnLabel(i+1));
				}
				while (rset.next() && (maxRows == null || rows < maxRows.longValue()))
				{
					rows++;
					Object[] row = new Object[cols];
					for (int i = 0; i < cols; i++)
					{
						Object obj = rset.getObject(i + 1);
						if (obj == null && enableNullSqlObject)
						{
							int type = rset.getMetaData().getColumnType(i+1);
							if (type == Types.BINARY ||
								type == Types.LONGVARBINARY ||
								type == Types.VARBINARY || type == Types.BLOB ||
								type == Types.DATE || type == Types.TIMESTAMP ||
								type == Types.TIME || type == Types.BLOB)
									row [i] = new NullSqlObjet(type);
						}
						else if (obj instanceof Date)
						{
							row[i] = rset.getTimestamp(i+1);
						}
						else if (obj instanceof BigDecimal)
						{
							row[i] = rset.getLong(i+1);
						}
						else
							row[i] = obj;
					}
					result.add(row);
				}
				return result;
			}
			finally
			{
				rset.close();
			}
		}
		finally
		{
			stmt.close();
		}
	}

    public void execute (String sql, Object... params) throws SQLException
    {
    	PreparedStatement stmt = conn.prepareStatement(sql);
    	int num = 0;
    	for ( Object param: params) 
    	{
    		num++;
    		if (param == null)
    		{
    			stmt.setNull(num, Types.VARCHAR);
    		} 
    		else if (param instanceof NullSqlObjet)
    		{
    			stmt.setNull(num, ((NullSqlObjet) param).getSqlType());
    		} 
    		else if (param instanceof Long)
    		{
    			stmt.setLong(num, (Long) param);
    		}
    		else if (param instanceof Integer)
    		{
    			stmt.setInt(num, (Integer) param);
    		}
    		else if (param instanceof java.sql.Date)
    		{
    			stmt.setDate(num, (java.sql.Date) param);
    		}
    		else if (param instanceof java.util.Date)
    		{
    			stmt.setDate(num, new java.sql.Date ( ((java.util.Date) param).getTime() ));
    		}
    		else if (param instanceof Timestamp)
    		{
    			stmt.setTimestamp(num, (java.sql.Timestamp) param);
    		}
    		else 
    		{
    			stmt.setString(num, param.toString());
    		}
    	}
    	try {
    		stmt.execute();
    	} finally {
    		stmt.close();
    	}
    	
    }

    public int executeUpdate (String sql, Object... params) throws SQLException
    {
    	PreparedStatement stmt = conn.prepareStatement(sql);
    	int num = 0;
    	for ( Object param: params) 
    	{
    		num ++;
    		if (param == null)
    		{
    			stmt.setNull(num, Types.VARCHAR);
    		} 
    		else if (param instanceof NullSqlObjet)
    		{
    			stmt.setNull(num, ((NullSqlObjet) param).getSqlType());
    		} 
    		else if (param instanceof Long)
    		{
    			stmt.setLong(num, (Long) param);
    		}
    		else if (param instanceof Integer)
    		{
    			stmt.setInt(num, (Integer) param);
    		}
    		else if (param instanceof Date)
    		{
    			stmt.setDate(num, (Date) param);
    		}
    		else if (param instanceof java.util.Date)
    		{
    			stmt.setDate(num, new java.sql.Date( ((java.util.Date) param).getTime()));
    		}
    		else if (param instanceof java.sql.Timestamp)
    		{
    			stmt.setTimestamp(num, (java.sql.Timestamp) param);
    		}
    		else if (param instanceof Boolean)
    		{
    			stmt.setBoolean(num, ((Boolean) param).booleanValue());
    		}
    		else if (param instanceof byte[])
    		{
    			stmt.setBytes(num, (byte[]) param);
    		}
    		else 
    		{
    			stmt.setString(num, param.toString());
    		}
    	}
    	try {
    		return stmt.executeUpdate();
    	} finally {
    		stmt.close();
    	}
    }

	public QueryHelper(Connection conn) {
		super();
		this.conn = conn;
	}   
	
}

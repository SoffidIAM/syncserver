package com.soffid.iam.sync.engine.pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import com.soffid.iam.sync.bootstrap.QueryHelper;

public class JDBCPool extends AbstractPool<Connection> {
	String user;
	String password;
	String url;
	Properties p;

	public Properties getProperties() {
		return p;
	}

	public void setProperties(Properties p) {
		if (this.p == null || ! this.p.equals(p))
		{
			this.p = p;
			reconfigure();
		}
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		if (this.user == null || ! this.user.equals(user))
		{
			this.user = user;
			reconfigure();
		}
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		if (this.password == null || ! this.password.equals(password))
		{
			this.password = password;
			reconfigure();
		}
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		if (this.url == null || ! this.url.equals(url))
		{
			this.url = url;
			reconfigure();
		}
	}

	@Override
	protected Connection createConnection() throws SQLException {
		if (p != null)
			return DriverManager.getConnection(url, p);
		else
			return DriverManager.getConnection(url, user, password);
	}

	@Override
	protected void closeConnection(Connection conn) throws SQLException {
		conn.close();
	}

	public String toString() {
		return "JDBC Connection to " + url + " (user=" + user + ")";
	}

	@Override
	protected boolean isConnectionValid(Connection conn) throws Exception {
		try {
			if (url.startsWith("jdbc:informix")) {
				QueryHelper qh = new QueryHelper(conn);
				qh.select("SELECT count(*) FROM systables");
			} else if (url.startsWith("jdbc:oracle")) {
				QueryHelper qh = new QueryHelper(conn);
				qh.select("SELECT 1 FROM DUAL");
			} else if (url.startsWith("jdbc:postgresql")) {
				QueryHelper qh = new QueryHelper(conn);
				qh.select("SELECT 1");
			} else if (url.startsWith("jdbc:sqlserver")) {
				QueryHelper qh = new QueryHelper(conn);
				qh.select("SELECT 1");
			} else if (url.startsWith("jdbc:mysql") && !conn.isValid(5)) {
				return false;
			}
		} catch (SQLException e) {
			conn = null;
			return false;
		}
		return true;
	}

}

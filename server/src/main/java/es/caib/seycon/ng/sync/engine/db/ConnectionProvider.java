package es.caib.seycon.ng.sync.engine.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.hibernate.HibernateException;

import es.caib.seycon.ng.exception.InternalErrorException;

public class ConnectionProvider implements org.hibernate.connection.ConnectionProvider {

    public void configure(Properties props) throws HibernateException {
    }

    public Connection getConnection() throws SQLException {
        try {
            return ConnectionPool.getPool().getPoolConnection();
        } catch (InternalErrorException e) {
            throw new SQLException(e);
        }
    }

    public void closeConnection(Connection conn) throws SQLException {
        conn.close();
    }

    public void close() throws HibernateException {
    }

    public boolean supportsAggressiveRelease() {
        return false;
    }

}

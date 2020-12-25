package com.soffid.iam.sync.bootstrap.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLConnectionFactory {
    private static SQLConnectionFactory thePool = null;
	private static boolean driversRegistered = false;
	
	public static Connection getConnection() throws FileNotFoundException, IOException, SQLException {
		Config config = Config.getConfig();
        String url = config.getDB();
        String type;

        if (!driversRegistered ) {
            try {
                Class c = Class.forName("oracle.jdbc.driver.OracleDriver");
                DriverManager.registerDriver((java.sql.Driver) c.newInstance());
            } catch (Exception e) {
                System.out.println("WARNING: Cannot register Oracle driver");
            }
            try {
                Class c = Class.forName("com.mysql.jdbc.Driver");
                DriverManager.registerDriver((java.sql.Driver) c.newInstance());
            } catch (Exception e) {
                System.out.println("WARNING: Cannot register MYSQL driver");
            }
            try{
            	Class c = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            	DriverManager.registerDriver((java.sql.Driver) c.newInstance());
            } catch (Exception e) {
                System.out.println("WARNING: Cannot register SQL Server driver");
            }
            try{
            	Class c = Class.forName("org.postgresql.Driver");
            	DriverManager.registerDriver((java.sql.Driver) c.newInstance());
            } catch (Exception e) {
                System.out.println("WARNING: Cannot register PostgreSQL driver");
                e.printStackTrace();
            }
            driversRegistered = true;
        }

       	return DriverManager.getConnection(config.getDB(), config.getDbUser(),
        	                config.getPassword().getPassword());
    }
}

package com.soffid.iam.sync.bootstrap.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;

import javax.crypto.NoSuchPaddingException;

public class DatabaseConfig {
	public void loadFromDatabase() throws SQLException, IOException, InterruptedException {
        Connection conn = getConnection();
        try {
	       	QueryHelper qh = new QueryHelper(conn);
	   		qh.select("SELECT * FROM "+getTableName(),
	   				new Object[0],
	   				(rset) -> {
	   					String name = rset.getString("NAME");
	   					File f = new File (Config.getConfig().getHomeDir(), "conf/"+name);
	   					FileOutputStream out = new FileOutputStream(f);
						boolean encrypted = false;
						try {
							encrypted = "1".equals(rset.getString("ENCRYPTED"));
						} catch (SQLException ee) { //Column is missing
							new QueryHelper(conn).execute("ALTER TABLE "+getTableName()+" ADD ENCRYPTED VARCHAR(1)");
						}
						InputStream in = rset.getBinaryStream("DATA");
						if (encrypted) {
							try {
								in = new DecryptionInputStream(in, System.getenv("DB_CONFIGURATION_CRYPT"));
							} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
								throw new IOException(e);
							}
						}
						byte b[] = new byte [2048];
						int read;
						for (;;) {
							read = in.read(b);
							if (read <= 0)
								break;
							out.write (b,0, read);
						}
						out.close();
	
	   				});
        } finally {
        	conn.close();
        }
	}

	private Connection getConnection() throws InterruptedException {
		try {
            Class c = Class.forName("oracle.jdbc.driver.OracleDriver");
            DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
        }
        try {
            Class c = Class.forName("com.mysql.jdbc.Driver");
            DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
        }
        try{
        	Class c = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        	DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
        }
        try{
        	Class c = Class.forName("org.postgresql.Driver");
        	DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
        }

        // Connect to the database
        // You can put a database name after the @ sign in the connection URL.
       	Connection conn = null;
       	do {
       		try {
	       		conn = DriverManager.getConnection(
	       						System.getenv("DB_URL"), 
	       						System.getenv("DB_USER"),
	        	                System.getenv("DB_PASSWORD"));
       		} catch (SQLException e) {
       			System.out.println("Cannot connect database: "+e.getMessage());
       			Thread.sleep(5000);
       		}
       	} while (conn == null);
		return conn;
	}

	public void saveToDatabase() throws InterruptedException, SQLException, IOException {
        Connection conn = getConnection();
        try {
	       	QueryHelper qh = new QueryHelper(conn);
	       	try {
	       		String url = System.getenv("DB_URL").toLowerCase();
	       		String blobtype;
	       		if (url.startsWith("jdbc:sqlserver:")) blobtype = "varbinary(max)";
	       		else if (url.startsWith("jdbc:oracle")) blobtype = "blob";
	       		else if (url.contains("jdbc:postgres")) blobtype = "bytea";
	       		else blobtype = "longblob";
	       		qh.execute("CREATE TABLE "+getTableName()+" (NAME VARCHAR(64), ENCRYPTED VARCHAR(1), DATA "+blobtype+")");
	       	} catch (SQLException e) { // Ignore
	       	}
       		qh.execute("DELETE FROM "+getTableName());
			File dir = new File (Config.getConfig().getHomeDir(), "conf");
			for (File f: dir.listFiles()) {
				byte[] d = readBinaryFile(f.getPath());
				qh.executeUpdate("INSERT INTO "+getTableName()+"(NAME, ENCRYPTED, DATA) VALUES (?,?)", 
						new Object[] {
								f.getName(),
								System.getenv("DB_CONFIGURATION_CRYPT") != null ? "1": "0",
								d
						});
			}
        } finally {
        	conn.close();
        }
	}

	private String getTableName() {
		return "DB_"+System.getenv("DB_CONFIGURATION_TABLE").toUpperCase();
	}

	byte[] readBinaryFile(String path) throws IOException {
		InputStream r = new FileInputStream(path);
		if (System.getenv("DB_CONFIGURATION_CRYPT") != null) {
			try {
				r = new DecryptionInputStream(r, System.getenv("DB_CONFIGURATION_CRYPT"));
			} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
				throw new IOException(e);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = r.read();  i >= 0; i = r.read()) {
			out.write(i);
		}
		return out.toByteArray();
	}

}

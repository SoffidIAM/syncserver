package com.soffid.iam.sync.bootstrap.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JarExtractor {
    Logger log = new Logger("JarExtractor");
            
    public List<String> getActiveModules () throws SQLException, IOException 
    {
    	final List<String> modules = new LinkedList<String>();
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
    		qh.select("SELECT SPM_ID FROM SC_SEPLMO, SC_SERPLU " +
    				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_TYPE IN ('C','V')",
    				new Object[] { Boolean.FALSE} ,
    				new QueryAction() {
						public void perform(ResultSet rset) throws SQLException, IOException {
							long id = rset.getLong(1);
							modules.add (Long.toString(id));
						}
					});
    	}
    	finally {
    		conn.close();
    	}
    	
    	return modules;
    }
    

    public void extractModule (String id, final OutputStream out) throws SQLException, IOException 
    {
    	final List<String> modules = new LinkedList<String>();
    	Long l = Long.decode(id);
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
    		qh.select("SELECT SPM_DATA FROM SC_SEPLMO, SC_SERPLU " +
    				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_ID=?",
    				new Object[] {Boolean.FALSE, l},
    				new QueryAction() {
						public void perform(ResultSet rset) throws SQLException, IOException {
							InputStream in = rset.getBinaryStream(3);
							byte b[] = new byte [2048];
							int read;
							for (;;) {
								read = in.read(b);
								if (read <= 0)
									break;
								out.write (b,0, read);
							}
						}
					});
    	}
    	finally {
    		conn.close();
    	}
    }
    
    public void extractModules (final File modulesDir) throws SQLException, IOException 
    {
    	modulesDir.mkdirs();
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
    		qh.select("SELECT SPM_ID, SPM_RESNAM, SPM_DATA FROM SC_SEPLMO, SC_SERPLU " +
    				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_TYPE IN ('C','V')",
    				new Object[] {Boolean.FALSE},
    				new QueryAction() {
						public void perform(ResultSet rset) throws SQLException, IOException {
							long id = rset.getLong(1);
							String name = rset.getString(2);
							String fileName = "plugin-"+id;
							if (name != null && name.length() > 0)
							{
								fileName = fileName + "-"+name;
							}
							File fileObject = new File (modulesDir, fileName + ".jar");
							FileOutputStream out  = new FileOutputStream(fileObject);
							InputStream in = rset.getBinaryStream(3);
							byte b[] = new byte [2048];
							int read;
							for (;;) {
								read = in.read(b);
								if (read <= 0)
									break;
								out.write (b,0, read);
							}
							out.close ();
						}
					});
    	}
    	
    	catch (Throwable e)
    	{
    		log.warn("Error retrieving plugins", e);
    	}
    	finally {
    		conn.close();
    	}
    }
    

    public String getComponentVersion (String name) throws SQLException, IOException
    {
        PreparedStatement stmt = null;
        java.sql.ResultSet rset = null;
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
            stmt = conn.prepareStatement("SELECT BCO_VERSIO FROM SC_BLOCON, SC_TENANT WHERE BCO_NAME=? AND TEN_ID=BCO_TEN_ID AND TEN_NAME='master'");
            stmt.setString(1, name);
            rset = stmt.executeQuery();
            if (rset.next()) {
            	String r = rset.getString(1);
            	if (r == null || r.startsWith("Unk"))
            		return null;
            	else
            		return r;
            } else {
            	throw new FileNotFoundException();
            }
        } finally {
            if (rset != null)  try { rset.close();} catch (Exception e) {}
            if (stmt != null)  try { stmt.close();} catch (Exception e) {}
            conn.close();
        }
    	
    }

    public boolean generateJar (String name, OutputStream out) throws SQLException, IOException {
        PreparedStatement stmt = null;
        java.sql.ResultSet rset = null;
    	Connection connection = SQLConnectionFactory.getConnection();
    	try {
            stmt = connection.prepareStatement("SELECT BCO_VALUE FROM SC_BLOCON, SC_TENANT WHERE BCO_NAME=? AND TEN_ID=BCO_TEN_ID AND TEN_NAME='master'");
            stmt.setString(1, name);
            rset = stmt.executeQuery();
            if (rset.next()) {
                InputStream in = rset.getBinaryStream(1);
                byte buffer [] = new byte[1024];
                do {
                    int read = in.read(buffer);
                    if (read < 0)
                        break;
                    out.write (buffer, 0, read);
                } while (true);
                return true;
            } else {
                return false;
            }
        } finally {
            if (rset != null)  try { rset.close();} catch (Exception e) {}
            if (stmt != null)  try { stmt.close();} catch (Exception e) {}
            connection.close();
        }
    }

    public void generateMergedLibrary(OutputStream out) throws IOException, SQLException {
        final ZipOutputStream zipout = new ZipOutputStream(out);

      	dump(zipout, getJarForClass(JarExtractor.class));
      	dump(zipout, getJarForClass(Config.class));
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
    		qh.select("SELECT SPM_ID, SPM_RESNAM, SPM_DATA FROM SC_SEPLMO, SC_SERPLU " +
				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_TYPE = 'V'",
				new Object[] {Boolean.FALSE},
				new QueryAction() {
					public void perform(ResultSet rset) throws SQLException, IOException {
						long id = rset.getLong(1);
						String name = rset.getString(2);
						String fileName = "plugin-"+id;
						if (name != null && name.length() > 0)
						{
							fileName = fileName + "-"+name;
						}
						dump (zipout, new ZipInputStream(rset.getBinaryStream(3)));
					}
				});
    	} finally {
    		conn.close();
    	}
    }

    private void dump(ZipOutputStream out, URL inFile)
            throws IOException {
        ZipInputStream in = new ZipInputStream(inFile.openStream());
    }
    
    private void dump(ZipOutputStream out, ZipInputStream in)
                 throws IOException {
        ZipEntry entry = in.getNextEntry();
        while (entry != null) {
        	try {
	            out.putNextEntry(entry);
	        	
	            byte buffer[] = new byte[2048];
	            int read = in.read(buffer);
	            while (read > 0) {
	                out.write(buffer, 0, read);
	                read = in.read(buffer);
	            }
	            out.closeEntry();
	            in.closeEntry();
	            entry = in.getNextEntry();
        	} catch (ZipException e ) {
        		// Ignorem les entrades duplicades
        		if (!e.getMessage().startsWith("duplicate entry:"))
        			throw e;        				
        		log.warn("Detectada una duplicate entry al proces dump - s'ignora ", e);
				entry = in.getNextEntry(); // ignorem aquesta entrada
        	}            
        }
    }

    public boolean generateBaseJar (final OutputStream out) throws SQLException, IOException {
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
    		List<Object[]> result = qh.select("SELECT SPM_ID FROM SC_SEPLMO, SC_SERPLU " +
    				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_TYPE='S'",
    				new Object[] {Boolean.FALSE});
    		if (result.isEmpty())
    		{
                log.info("Unable to get syncserver component from database ");
    			return false;
    		}
    			
    		qh.select("SELECT SPM_DATA FROM SC_SEPLMO, SC_SERPLU " +
    				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_TYPE='S'",
    				new Object[] {Boolean.FALSE},
    				new QueryAction() {
						public void perform(ResultSet rset) throws SQLException, IOException {
							InputStream in = rset.getBinaryStream(1);
							byte b[] = new byte [2048];
							int read;
							for (;;) {
								read = in.read(b);
								if (read <= 0)
									break;
								out.write (b,0, read);
							}
							out.close ();
						}
					});
    		return true;
    	} finally {
    		conn.close();
    	}
    }

    public String getBaseJarVersion () throws SQLException, IOException {
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
    		List<Object[]> result = qh.select("SELECT SPL_VERSION FROM SC_SEPLMO, SC_SERPLU " +
    				"WHERE SPL_ENABLE != ? AND SPM_SPL_ID = SPL_ID AND SPM_TYPE='S'",
    				new Object[] {Boolean.FALSE});
    		if (result.isEmpty())
    		{
            	throw new FileNotFoundException();
    		}
    		else
    		{
    			return (String) result.get(0)[0];
    		}
    	} finally {
    		conn.close();
    	}
    }


    public byte [] getPluginJar (String className) throws SQLException, IOException {
        PreparedStatement stmt = null;
        java.sql.ResultSet rset = null;
    	Connection conn = SQLConnectionFactory.getConnection();
    	try {
    		QueryHelper qh = new QueryHelper(conn);
            stmt = conn.prepareStatement("SELECT SPM_DATA FROM SC_SERPLU, SC_SEPLMO, SC_AGEDES "+
                    "WHERE ADE_CLASS=? AND ADE_SPM_ID=SPM_ID AND SPM_SPL_ID = SPL_ID AND SPL_ENABLE != ?");
            stmt.setString(1, className);
            stmt.setBoolean(2, Boolean.FALSE);
            rset = stmt.executeQuery();
            if (rset.next()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InputStream in = rset.getBinaryStream(1);
                byte buffer [] = new byte[1024];
                do {
                    int read = in.read(buffer);
                    if (read < 0)
                        break;
                    out.write (buffer, 0, read);
                } while (true);
                out.close ();
                return out.toByteArray();
            }
            else
                return null;
        } finally {
            if (rset != null)  try { rset.close();} catch (Exception e) {}
            if (stmt != null)  try { stmt.close();} catch (Exception e) {}
            conn.close();
        }
    }
    
    public URL getJarForClass (Class clazz) throws IOException
    {
		String path = clazz.getName().replace('.', '/').concat(".class");
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null)
        	throw new IOException("Unable to locate seycon-library.jar");
        if (url.getProtocol().equals("jar"))
        {
        	String u = url.getFile();
        	int l = u.lastIndexOf("!");
        	if ( l > 0)
        		u = u.substring(0, l);
        	return new URL(u);
        }
        else
        	throw new IOException("Unable to locate jar containing "+clazz.getName());
    }
}

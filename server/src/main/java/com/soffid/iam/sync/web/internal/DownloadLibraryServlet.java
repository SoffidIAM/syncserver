package com.soffid.iam.sync.web.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.User;
import com.soffid.iam.config.Config;
import com.soffid.iam.sync.tools.JarExtractor;

import es.caib.seycon.ng.exception.InternalErrorException;

public class DownloadLibraryServlet extends HttpServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("Upgrade Servlet");
    private static File mergeFile = null;

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        //
        // Stream to the requester.
        //
        String component = request.getParameter("component");
        String plugin = request.getParameter("plugin");
        //
        // Set the response and go!
        //
        //
        response.setContentType("application/jar");
        // response.setContentLength((int) f.length());
        response.setHeader("Content-Disposition", "attachment;filename=\""
                + ( component==null ?"seycon-library" : component) + ".jar" + "\"");
        
        log.info("Generating component {}", component, null);

        ServletOutputStream op = response.getOutputStream();
        try {
        	if (plugin != null)
        	{
                new JarExtractor().extractModule(plugin, op);
        	}
        	else if (component == null || component.equals("seycon-base") ) 
        	{
                if (mergeFile == null || mergeFile.length() == 0 || ! mergeFile.canRead())
                {
                    mergeFile = merge();
                }
                long size = mergeFile.length();
                response.setContentLength((int)size);
                InputStream in = new FileInputStream(mergeFile);
                byte b [] = new byte[4096];
                int read;
                do {
                    read = in.read(b);
                    if (read < 0) break;
                    op.write (b, 0, read);
                } while (true);
                in.close ();
            } 
        	else if (component.equals("seycon-common") || component.equals("iam-common"))
            {
            	ZipOutputStream zipout = new ZipOutputStream(op);
            	zipout.close();
            }
            else
            {
                if (!new JarExtractor().generateJar("component."+component, op))
                {
                	response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
            }
            op.close();
        } catch (InternalErrorException e) {
            throw new ServletException(e);
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }

    public File merge() throws IOException, InternalErrorException, SQLException {
        File tmpFileMerge = File.createTempFile("seycon-library", ".jar");

		log.info ("Generating seycon-library.jar stream", null, null);

        FileOutputStream stream = new FileOutputStream(tmpFileMerge);
        ZipOutputStream out = new ZipOutputStream(stream);

        HashSet<String> names = new HashSet<>();
        
        JarExtractor je = new JarExtractor();
        URL url1 = je.getJarForClass(User.class);
		log.info ("Dumping {}", url1.getPath(), null);
		InputStream in1 = url1.openStream();
       	dump (out, url1.getPath(), in1, names);
        in1.close();

        URL url2 = je.getJarForClass(getClass());
		log.info ("Dumping {}", url2.getPath(), null);
		InputStream in2 = url2.openStream();
       	dump (out, url2.getPath(), in2, names);
        in2.close();

        File modulesDir = new File(Config.getConfig().getHomeDir(), "addons");
        if (modulesDir.isDirectory())
        {
	        for (File module: modulesDir.listFiles())
	        {
	        	try {
	    			log.info ("Dumping plugin "+module.getName(), null, null);
	    			FileInputStream in = new FileInputStream(module.getPath());
	        		dump (out, module.getPath(), in, names);
	        		in.close();
	        	} 
	        	catch (Exception e)
	        	{
	        		log.warn("Error uncompressing file " + module.getAbsolutePath(), e);
	        	}
	        }
        }

        out.close();
        stream.close();
        tmpFileMerge.deleteOnExit();
        return tmpFileMerge;
    }

    private void dump(ZipOutputStream out, String inFileName, InputStream inFile, HashSet<String> names)
            throws IOException {
        ZipInputStream in = new ZipInputStream(inFile);
        ZipEntry entry = in.getNextEntry();
        while (entry != null) {
        	try {
	            byte buffer[] = new byte[2048];
        		if ( names.contains(entry.getName())) {
    	            int read = in.read(buffer);
    	            while (read > 0) {
    	                read = in.read(buffer);
    	            }
        		} else {
		            out.putNextEntry(entry);
		            int read = in.read(buffer);
		            while (read > 0) {
		                out.write(buffer, 0, read);
		                read = in.read(buffer);
		            }
		            out.closeEntry();
		            names.add(entry.getName());
        		}
	            in.closeEntry();
	            entry = in.getNextEntry();
        	} catch (ZipException e ) {
        		// Ignorem les entrades duplicades
				log.warn("ZIP error in "+inFileName+" file: "+entry.getName()+" "+e.toString(), null, null);
				entry = in.getNextEntry(); // ignorem aquesta entrada
        	}            
        }
    }

}

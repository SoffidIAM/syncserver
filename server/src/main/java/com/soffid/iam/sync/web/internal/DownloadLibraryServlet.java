package com.soffid.iam.sync.web.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
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

import com.soffid.iam.config.Config;
import com.soffid.iam.sync.bootstrap.JarExtractor;

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
                if (mergeFile == null || ! mergeFile.canRead())
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
        File tmpFile1 = File.createTempFile("iam-common", ".jar");
        File tmpFile2 = File.createTempFile("iam-sync", ".jar");
        File tmpFileMerge = File.createTempFile("seycon-library", ".jar");

		log.info ("Generating seycon-library.jar stream", null, null);

        FileOutputStream stream = new FileOutputStream(tmpFileMerge);
        ZipOutputStream out = new ZipOutputStream(stream);

        JarExtractor je = new JarExtractor();
        if (je.generateJar("component.iam-common", new FileOutputStream (tmpFile1)))
        {
			log.info ("Dumping iam-common content", null, null);
        	dump (out, tmpFile1);
        }
        else
        {
        	out.close();
        	stream.close();
        	throw new InternalErrorException ("Cannot find iam-common component");
        }
        if (je.generateBaseJar(new FileOutputStream (tmpFile2)))
        {
			log.info ("Dumping syncserver content", null, null);
        	dump (out, tmpFile2);
        }
        else
        {
        	out.close();
        	stream.close();
        	throw new InternalErrorException ("Cannot find syncserver component");
        }

        File modulesDir = new File(Config.getConfig().getHomeDir(), "addons");
        if (modulesDir.isDirectory())
        {
	        for (File module: modulesDir.listFiles())
	        {
	        	try {
	        		dump (out, module);
	        	} 
	        	catch (Exception e)
	        	{
	        		log.warn("Error uncompressing file " + module.getAbsolutePath(), e);
	        	}
	        }
        }

        out.close();
        stream.close();
        tmpFile1.delete();
        tmpFile2.delete();
        tmpFileMerge.deleteOnExit();
        return tmpFileMerge;
    }

    private void dump(ZipOutputStream out, File inFile)
            throws IOException {
        ZipInputStream in = new ZipInputStream(new FileInputStream(inFile));
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
        		{
					log.warn("ZIP error in "+inFile.getPath()+" file: "+entry.getName()+" "+e.toString(), null, null);
				} else {
					log.warn("Duplicate entry in {} file: {} ", inFile.getPath(), entry.getName());
				}
				entry = in.getNextEntry(); // ignorem aquesta entrada
        	}            
        }
    }

}

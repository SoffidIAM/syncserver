package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.jetty.JettyServer;

public class DiagnosticServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;


    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	String token = System.getenv("DIAG_TOKEN");
    	if (token != null) {
    		String auth = req.getHeader("Authorization");
    		if (auth == null || !auth.equals("Bearer "+token))
    		{
    			resp.setStatus(HttpServletResponse.SC_OK);
    			ServletOutputStream out = resp.getOutputStream();
    			out.write("Not authorized".getBytes());
    			return;
    		}
    	}
        resp.setContentType("text/plain; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        // Dump server status
        JettyServer jetty = SoffidApplication.getJetty();
        QueuedThreadPool mqtp = (QueuedThreadPool) jetty.getServer().getThreadPool();
        mqtp.dump(writer);
        writer.write("--------------------------------------------------\n");
        Map m = Thread.getAllStackTraces();
        for (Iterator it = m.keySet().iterator(); it.hasNext();)
        {
            Thread t = (Thread) it.next();
            writer.write (t.toString());
            writer.write (" "+t.getState().toString());
            writer.write (" priority "+t.getPriority());
            writer.write ("\n");
            StackTraceElement elements[] = (StackTraceElement[]) m.get(t);
            for (int i = 0 ; elements != null && i < elements.length; i++)
            {
                writer.write("  "+elements[i].toString()+"\n");
            }
            writer.write("\n");
        }
        writer.close();
    }

}

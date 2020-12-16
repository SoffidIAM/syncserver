package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.jetty.MyQueuedThreadPool;

public class TraceIPServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;


    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        // Dump server status
        JettyServer jetty = SoffidApplication.getJetty();
        MyQueuedThreadPool mqtp = (MyQueuedThreadPool) jetty.getServer().getThreadPool();
        writer.write("Remote address: ");
        writer.write(req.getRemoteAddr());
        writer.write("\n");
        for (Enumeration e = req.getHeaderNames(); e.hasMoreElements(); )
        {
        	String n = (String) e.nextElement();
        	writer.write ( n + ": "+req.getHeader(n));
        	writer.write("\n");
        }
        writer.write("\n");
        writer.close();
    }

}

package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.jetty.MyQueuedThreadPool;

public class DiagnosticServlet extends HttpServlet {

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
        writer.write(mqtp.getStatus());
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

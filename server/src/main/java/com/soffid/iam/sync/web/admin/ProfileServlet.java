package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.DispatcherHandlerImpl;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.TaskHandlerLog;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.PrioritiesList;
import com.soffid.iam.sync.service.TaskQueueImpl;
import com.soffid.iam.sync.service.TasksQueue;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.exception.InternalErrorException;

public class ProfileServlet extends HttpServlet {

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
        
        String task = req.getParameter("task");
        String system = req.getParameter("system");
        try {
	        dumpSystem(system, task, req, writer);
        } catch (Exception e) {
        	e.printStackTrace(new PrintWriter(writer));
        }
        writer.close();
    }


	private void dumpSystem(String system, String task, HttpServletRequest req, BufferedWriter writer) throws InternalErrorException, IOException {
		Hashtable<Long, PrioritiesList> tl = TaskQueueImpl.globalTaskList;
		for ( DispatcherHandler dh: ServiceLocator.instance().getTaskGenerator().getDispatchers()) {
			com.soffid.iam.api.System d = dh.getSystem();
			if (system == null || system.equals(d.getName())) {
				DispatcherHandlerImpl dhi = (DispatcherHandlerImpl) dh;
				writer.append("System ").append( d.getName()).append(" ").append(dhi.getStatus()).append(dh.isConnected()? "": " OFFLINE").append("\n");
				PrioritiesList pl = tl.get(d.getId());
				int prio = 0;
				for (TasksQueue tq: pl) {
					writer.append(" Priority ").append(Integer.toString(prio)).append("\n");
					for (TaskHandler th: tq) {
						if (task == null || task.isEmpty() || task.equals(th.toString())) {
							writer.append("  Task ").append(th.toString()).append("");
							TaskHandlerLog log = th.getLog(dh.getInternalId());
							if (log != null)
								writer.append(" "+log.isComplete()+" "+(log.getReason() == null? "": log.getReason()).replace("\n", "\n     "));
							writer.append("\n");
						}
					}
					prio ++;
				}
			}
		}
	}


}

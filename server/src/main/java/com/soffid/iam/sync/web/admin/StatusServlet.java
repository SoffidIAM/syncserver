package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.Engine;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.TaskHandlerLog;
import com.soffid.iam.sync.engine.pool.AbstractPool;
import com.soffid.iam.sync.service.TaskQueue;

import es.caib.seycon.ng.exception.InternalErrorException;

public class StatusServlet extends HttpServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public StatusServlet() {

    }

    @SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
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
        String type = req.getParameter("type");
        if (type == null)
            type = "tasks";
        resp.setContentType("text/plain; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        try {
        	if ("pools".equals (type))
                writer.write(getPoolStatus());
        	else
        		writer.write(getTasksStatus());
        } catch (InternalErrorException e) {
            throw new ServletException(e);
        }
        writer.close();
    }

    private String getPoolStatus() {
    	StringBuffer sb = new StringBuffer();
    	for (AbstractPool<?> ap: AbstractPool.getPools())
    	{
    		sb.append (ap.getStatus());
    		sb.append ("*****************************************\n");
    	}
    	return sb.toString();
	}

	/**
     * Generar información de tareas
     * 
     * @return cadena describiendo el estado de cada agente y el número de
     *         tareas pendientes
     * @throws InternalErrorException
     */
    private String getTasksStatus() throws InternalErrorException {
        String result = "";
        Engine engine = Engine.getEngine();
        if (engine.isAlive()) {
            result = result + "Engine: running ";
        } else {
            result = result + "Engine: stopped ";
        }
        TaskQueue taskQueue = ServiceLocator.instance().getTaskQueue();
        result = result + "Tasks: " + Integer.toString(taskQueue.countTasks()) + "\n";

        Collection<DispatcherHandler> dispatchers = ServiceLocator.instance().getTaskGenerator().getDispatchers();
        int max = 0;
        for ( DispatcherHandler d: dispatchers)
        	if ( d.getInternalId() > max) max = d.getInternalId();
        
        int[] counters = new int[max+1];
        for ( DispatcherHandler dis: dispatchers)
        {
       		counters[dis.getInternalId()] = taskQueue.countTasks(dis);
        }
        
        for (DispatcherHandler disp : dispatchers) {
            if (disp != null && disp.isActive()) {
                result = result + disp.getSystem().getName() + ": ";
                if (disp.getRemoteAgent() != null) {
                    result = result + "connected";
                } else {
                    result = result + "running";
                }

                result = result + " Tasks: " + Integer.toString(counters[disp.getInternalId()]) + "\n";
            }
        }
        return result;
    }

}

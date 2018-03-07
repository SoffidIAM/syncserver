package es.caib.seycon.ng.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.Engine;
import es.caib.seycon.ng.sync.engine.pool.AbstractPool;
import es.caib.seycon.ng.sync.servei.TaskQueue;

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

        for (DispatcherHandler disp : ServiceLocator.instance().getTaskGenerator().getDispatchers()) {
            if (disp != null && disp.isActive()) {
                result = result + disp.getDispatcher().getCodi() + ": ";
                if (disp.getRemoteAgent() != null) {
                    result = result + "connected";
                } else {
                    result = result + "running";
                }

                int contador = taskQueue.countErrorTasks(disp);
                result = result + " Tasks: " + Integer.toString(contador) + "\n";
            }
        }
        return result;
    }

}

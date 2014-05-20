package es.caib.seycon.ng.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.ServeiService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.Engine;
import es.caib.seycon.ng.sync.servei.SyncStatusService;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.ng.sync.servei.TaskQueue;

public class StatusServlet extends HttpServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private TaskGenerator taskGenerator;
    private TaskQueue taskQueue;

    public StatusServlet() {
        taskGenerator = ServerServiceLocator.instance().getTaskGenerator();
        taskQueue = ServerServiceLocator.instance().getTaskQueue();

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
            writer.write(getTasksStatus());
        } catch (InternalErrorException e) {
            throw new ServletException(e);
        }
        writer.close();
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
        result = result + "Tasks: " + Integer.toString(taskQueue.countTasks()) + "\n";

        for (DispatcherHandler disp : taskGenerator.getDispatchers()) {
            if (disp != null && disp.isActive()) {
                result = result + disp.getDispatcher().getCodi() + ": ";
                if (disp.getRemoteAgent() != null) {
                    result = result + "connected";
                } else {
                    result = result + "running";
                }

                int contador = taskQueue.countTasks(disp);
                result = result + " Tasks: " + Integer.toString(contador) + " Round-Trip: 0\n";
            }
        }
        return result;
    }

}

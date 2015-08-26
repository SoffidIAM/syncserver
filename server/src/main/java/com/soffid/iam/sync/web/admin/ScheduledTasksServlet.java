package com.soffid.iam.sync.web.admin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteInvokerFactory;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.agent.AgentManager;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.cron.TaskScheduler;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.exception.InternalErrorException;

public class ScheduledTasksServlet extends HttpServlet {
    Logger log = Log.getLogger("ScheduledTasks Servlet");
    private TaskGenerator taskGenerator;
    private static String HOME_PATH = null;
    private static String FILE_SEPARATOR = File.separator;

    static {
        try {
            HOME_PATH = Config.getConfig().getHomeDir().getAbsolutePath();
        } catch (IOException ioe) {
            Log.getLog().warn("No s'ha pogut carregar la configuració", ioe);
        }
    }

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        try {
        	TaskScheduler ts = TaskScheduler.getScheduler();
        	
            response.setContentType("text/html; charset=UTF-8");
            String id = request.getParameter("id");
            PrintWriter printWriter = response.getWriter();
            if (id != null) {
            	printWriter.print("<html><body><p>Executing ...</p>");
            	printWriter.flush();
            	for (ScheduledTask task: ts.getTasks())
            	{
            		if (task.getId().toString().equals(id))
            		{
            			ts.runNow(task);
            			printWriter.print ("<p>Executed "+task.getName()+"</p>");
            			if (task.isError())
            				printWriter.print ("<p>Result: ERROR</p>");
            			else
            				printWriter.print ("<p>Result: SUCCESS</p>");
            			printWriter.print("<PRE>");
            			printWriter.print(task.getLastLog());
            			printWriter.print("</PRE>");
            			printWriter.flush();
            		}
            	}
            	printWriter.println("</body></html>");
            } else {
            	printWriter.print("<html><body><p>Tasks to execute</p>");
            	printWriter.flush();
            	for (ScheduledTask task: ts.getTasks())
            	{
           			printWriter.print ("<p><a href='"+request.getRequestURI()+"?id="+task.getId()+"'>"+task.getName()+"</a></p>");
            	}
            	printWriter.println("</body></html>");
            }
        } catch (Throwable t) {
            PrintWriter printWriter = response.getWriter();
            printWriter
                    .println("<p>Error generando p&#225;gina. Revise el LOG.</p>");
            log.warn("Error invoking " + request.getRequestURI(), t);
        }
    }

    private void reset(HttpServletRequest request,
            HttpServletResponse response, String action) throws IOException {
        PrintWriter printWriter = response.getWriter();

        printWriter
                .print("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>"
                        + "<html>");
        printWriter.print("Reiniciant " + action + "...<br>");
        printWriter.flush();
        try {
            RemoteServiceLocator rsl = new RemoteServiceLocator(action);
            AgentManager agentMgr = rsl.getAgentManager();
            agentMgr.reset();
            printWriter.append("REINICIAT!");
        } catch (Exception e) {
            printWriter.append("Error reiniciant " + action + ": "
                    + e.toString());

        }
        printWriter.append("<br><a href='main'>Tornar</a></html>");


    }

    private String ask() throws FileNotFoundException, IOException,
            InternalErrorException {
        StringBuffer b = new StringBuffer();
        b
                .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>"
                        + "<html>"
                        + "<a href='reset?action=agents'>Reiniciar tots els agents</a><br><br><br>");
        Vector<String> hosts = new Vector<String>();
        Config config = Config.getConfig();
        String servidors[] = config.getSeyconServerHostList();
        for (int i = 0; i < servidors.length; i++) {
            String host = new URLManager(servidors[i]).getAgentURL().getHost();
            if (!hosts.contains(host))
                hosts.add(host);
        }

        for (DispatcherHandler td : taskGenerator.getDispatchers()) {
            if (td != null && td.getSystem() != null && td.getSystem().getUrl() != null) {
                String host = new URLManager(td.getSystem().getUrl()).getAgentURL()
                        .getHost();
                if (!hosts.contains(host) && !host.equals("local"))
                    hosts.add(host);
            }
        }

        for (int i = 0; i < hosts.size(); i++) {
            b.append("<a href='reset?action=" + hosts.get(i) + "'>Reiniciar "
                    + hosts.get(i) + "</a><br>");
        }
        b.append("<a href='main'>Cancel&#149;lar</a></html>");
        return b.toString();

    }

    private void resetAgents(HttpServletRequest request,
            HttpServletResponse response) throws IOException,
            InternalErrorException {
        PrintWriter printWriter = response.getWriter();
        printWriter
                .print("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>"
                        + "<html>");

        RemoteInvokerFactory factory = new RemoteInvokerFactory();
        Vector v = new Vector ();
        for (DispatcherHandler td : taskGenerator.getDispatchers()) {
            if (td != null && td.isConnected()) {
                URL url = new URLManager(td.getSystem().getUrl()).getAgentURL();
                String host = url.getHost();
                if ( !v.contains(host) && !host.equals("local"))
                {
                    v.add(host);
                    printWriter.print("Reiniciant "+host+"...<br>");
                    printWriter.flush();
                    if (!host.equals("local")
                            && !host.equals(Config.getConfig().getHostName())) {
                        try {
                            RemoteServiceLocator rsl  = new RemoteServiceLocator(td.getSystem().getUrl());
                            AgentManager agentMgr = rsl.getAgentManager();
                            agentMgr.reset();
                            printWriter.append("REINICIAT!<br><br>");
                        } catch (Exception e) {
                            printWriter.append("Error reiniciant " + host + ": "
                                    + e.toString()+"<br><br>");

                        }
                    }
                }
            }
        }
        printWriter.append("<br><a href='main'>Tornar</a></html>");

    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }

}

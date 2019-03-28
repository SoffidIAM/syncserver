package com.soffid.iam.sync.web.admin;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Request;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.agent.AgentManagerImpl;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.TaskHandler;
import com.soffid.iam.sync.engine.TaskHandlerLog;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.service.TaskQueue;

import es.caib.seycon.ng.exception.InternalErrorException;

public class AgentsServlet extends HttpServlet {
    Logger log = Log.getLogger("Agents Servlet");

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            response.setContentType("text/html; charset=UTF-8");
            try {
                PrintWriter printWriter = response.getWriter();
                printWriter
                        .println("<html><head><style type=\"text/css\">.st0 {margin:0 0 5px 0;cursor:pointer;}.st {text-decoration:underline;color:blue;}</style><script type=\"text/javascript\">function mostra(control) {fills = control.childNodes;var elvis = null;var eldiv = null;for (i=0; i < fills.length; i++) {if (fills[i].nodeName == \"DIV\") {if (elvis==null) elvis=fills[i]; else eldiv=fills[i];}}if (elvis!=null && eldiv!=null) {if(eldiv.style.display == \"block\" || eldiv.style.display == \"\") {eldiv.style.display=\"none\";elvis.innerHTML=\"[+] <span class='st'>mostra stack trace</span>\";}else {eldiv.style.display=\"block\";elvis.innerHTML=\"[-] <span class='st'>amaga stack trace</span>\";}}}</script></head>");
                String agent = request.getParameter("agent");
                String certificats = request.getParameter("certificats");
                if (agent == null) {
                    printWriter
                            .println("<p>[<a href=\"main\">main</a>] -- [<a href=\"tasquesPendents\">tasques</a>] -- Agents</p>");
                    if (certificats == null)
                        printWriter
                                .println("<p><a href=\"agents?certificats=true\">Mostra caducitat dels certificats</a></p>");
                    else
                        printWriter
                                .println("<p><a href=\"agents\">Amaga caducitat dels certificats</a></p>");
                    printWriter.println(getTasquesPendentsAgents(certificats != null));
                } else {
                    printWriter
                            .println("<p>[<a href=\"main\">main</a>] -- [<a href=\"tasquesPendents\">tasques</a>] -- [<a href=\"agents\">agents</a>] -- Agent "
                                    + agent + "</p>");
                    printWriter.println(getTasquesPendentsAgent(agent));
                }
                printWriter.print("</html>");
                printWriter.flush();
                response.flushBuffer();
            } catch (Throwable t) {
                PrintWriter printWriter = response.getWriter();
                printWriter.println("<p>Error generando página: </p>" + t.toString());
                log.warn("Error invoking " + request.getRequestURI(), t);
            }
        } catch (Exception e) {
            log.warn("Error invoking " + request.getRequestURI(), e);
        }
    }

    private String getTasquesPendentsAgent(String agent) throws InternalErrorException {
        StringBuffer toReturn = new StringBuffer();

        return toReturn.toString();
    }

    private String getTasquesPendentsAgents(boolean mostraCertificats) throws IOException, InternalErrorException {
        StringBuffer toReturn = new StringBuffer();
        toReturn.append("<table border=\"1\">");
        toReturn.append("<th>Agent</th>");
        toReturn.append("<th>Tasques pendents</th>");
        toReturn.append("<th>Estat</th>");
        toReturn.append("<th>Nom</th>");
        if (mostraCertificats)
            toReturn.append("<th>Caducitat Certificat</th>");
        TaskGenerator taskGenerator = ServerServiceLocator.instance().getTaskGenerator();
        TaskQueue queue = ServerServiceLocator.instance().getTaskQueue();

        for (DispatcherHandler taskDispatcher : taskGenerator.getDispatchers()) {
            if (taskDispatcher.isActive()) {
                toReturn.append("<tr>");
                toReturn.append("<td> <a href=agents?agent=" + URLEncoder.encode(taskDispatcher.getSystem().getName(), "UTF-8") + " >");
                toReturn.append(taskDispatcher.getSystem().getName());
                toReturn.append("</a> </td>");
                toReturn.append("<td>");
                toReturn.append(queue.countTasks(taskDispatcher));
                toReturn.append("</td>");
                toReturn.append("<td>");
                if (!taskDispatcher.isConnected()) {
                    toReturn.append("DISCONNECTED");
                    if (taskDispatcher.getConnectException() != null) {
                        toReturn.append("<BR>");
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        PrintStream print = new PrintStream(out);
                        taskDispatcher.getConnectException().printStackTrace(print);
                        print.close();
                        out.close();
                        toReturn.append(new String(out.toByteArray()).replaceAll("\n", "<BR>"));
                    }
                } else if (taskDispatcher.isActive()){
                    toReturn.append("Running");
                } else {
                    toReturn.append("STOPPED");
                }
                toReturn.append("</td>");
                toReturn.append("<td>" + taskDispatcher.getSystem().getClassName() + "</td>");
                // Obtenim la caducitat del certificat
                if (mostraCertificats) {
                    if (taskDispatcher.isConnected()) {
//                       try {
//                            String s_notValidAfter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
//                                    .format(taskDispatcher.getCertificateNotValidAfter());
//                            toReturn.append("<td>" + s_notValidAfter + "</td>");
//                        } catch (Throwable th) {
//                            toReturn.append("<td> </td>");
//                        }
                    } else
                        toReturn.append("<td> </td>");
                }
                toReturn.append("</tr>");
            }
        }
        toReturn.append("</table>");
        return toReturn.toString();
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }

}

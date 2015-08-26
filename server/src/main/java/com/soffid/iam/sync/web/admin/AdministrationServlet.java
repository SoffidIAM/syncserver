package com.soffid.iam.sync.web.admin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.Engine;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.service.TaskQueue;

import es.caib.seycon.ng.exception.InternalErrorException;

public class AdministrationServlet extends HttpServlet {
    Logger log = Log.getLogger("Administration Servlet");

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");
        try {
            try {
                String version = Config.getConfig().getVersion();
                PrintWriter printWriter = response.getWriter();
                printWriter
                        .println("<form id=\"tasquesPendents\" action=\"tasquesPendents\" method=\"post\"><input type=\"hidden\" id=\"rang\" name=\"rang\" value=\"0\"></input>");
                printWriter.println("<b>SEYCON " + version + "</b><br>");
                printWriter.println(getSSOThreadStatus());
                printWriter.println(getJettyThreadStatus());
                printWriter.println(getSSODaemonThreadStatus());
                printWriter.println(getTaskGeneratorThreadStatus());
                printWriter.println ("<p><a href='/scheduledTasks'>Scheduled tasks</a></p>");
                Date rootCertNotAfter = getRootCertificateNotValidAfter();
                if (rootCertNotAfter != null) {
                    String s_notAfter = new SimpleDateFormat(
                            "dd-MMM-yyyy HH:mm:ss").format(rootCertNotAfter);
                    Calendar c_cert = Calendar.getInstance();
                    c_cert.setTime(rootCertNotAfter);
                    Calendar c_now = Calendar.getInstance();
                    long diff = c_cert.getTimeInMillis()
                            - c_now.getTimeInMillis();
                    printWriter.println("<p>Caducitat certificat root: "
                            + s_notAfter + "</p>");
                    if (diff / (24 * 60 * 60 * 1000) <= 365)
                        printWriter
                                .println("<p style='color:red;font-weight:bold;'>AVÍS: <span style='text-decoration:blink;'>Queda menys <u>d'un any</u> perquè caduque el certificat ROOT</span></p>");
                }
                Date mainCertNotAfter = getServerCertificateNotValidAfter();
                if (mainCertNotAfter != null) {
                    String s_notAfter = new SimpleDateFormat(
                            "dd-MMM-yyyy HH:mm:ss").format(mainCertNotAfter);
                    Calendar c_cert = Calendar.getInstance();
                    c_cert.setTime(mainCertNotAfter);
                    Calendar c_now = Calendar.getInstance();
                    long diff = c_cert.getTimeInMillis()
                            - c_now.getTimeInMillis();
                    printWriter.println("<p>Caducitat certificat main: "
                            + s_notAfter + "</p>");
                    if (diff / (24 * 60 * 60 * 1000) <= 365)
                        printWriter
                                .println("<p style='color:red;font-weight:bold;'>AVÍS: <span style='text-decoration:blink;'>Queda menys <u>d'un any</u> perquè caduque el certificat PRINCIPAL</span></p>");
                }
				// Afegim informació de l'hora del servidor
                printWriter.println("Current date: "
						+ new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
								.format(new Date()));
                
                printWriter.println(getTasquesPendents());
                printWriter.println(getAgentsInformation());
                printWriter.println(getDBConnectionPoolStatus());
//                if (request.isUserInRole("SEU_ADMINISTRADOR")) {
//                    printWriter.println(getUpgrade());
//                }
                printWriter.println(getViewLog());
                printWriter.println(getRecodificarSecrets());
                printWriter.println("</form>");
                printWriter.flush();
                response.flushBuffer();
            } catch (Throwable t) {
                PrintWriter printWriter = response.getWriter();
                printWriter.println("<p>Error generando página: </p>"
                        + t.getMessage());
            }
        } catch (Exception e) {
            log.warn("Error invoking " + request.getRequestURI(), e);
        }
    }

    private String getUpgrade() throws FileNotFoundException, IOException,
            InternalErrorException {
        if (Config.getConfig().isMainServer()) {
            return "<a href=\"upgrade\">Upgrade Seycon</a><br>"
                    + "<a href=\"reset\">Reiniciar serveis</a><br>";
        } else
            return "";

    }

    private String getViewLog() {
        return "<a href=\"viewLog\">Ver LOG</a><br>";
    }

    private String getRecodificarSecrets() {
        return "<br><a href=\"reencodesecrets\">Recodificar claus secretes</a><br>";
    }

    private boolean threadRunning(Thread thread) {
        return thread != null && thread.getState() != Thread.State.TERMINATED
                && thread.getState() != Thread.State.NEW;
    }

    public String getSSOThreadStatus() {
        Thread SSOServer = SoffidApplication.getSso();
        if (threadRunning(SSOServer)) {
            return "<p>SSO RUNNING</p>";
        }
        return "<p>SSO FAILED</p>";
    }

    public String getDBConnectionPoolStatus() {
        try {
            ConnectionPool pool = ConnectionPool.getPool();
            return "<p><a href=dbpool>Database connections</a>: using "
                    + pool.getNumberOfLockedConnections() + " of "
                    + pool.getNumberOfConnections() + " allocated ("
                    + pool.getNumberOfConnections() + " max)" + "</p>";
        } catch (Exception e) {
            return "<p>Database Pool FAILED: " + e.getMessage() + " </p>";
        }
    }

    public String getJettyThreadStatus() {
        JettyServer jetty = SoffidApplication.getJetty();
        if (jetty != null) {
            if (jetty.isRunning()) {
                return "<p>Jetty RUNNING</p>";
            }
        }
        return "<p>Jetty FAILED </p>";
    }

    public String getSSODaemonThreadStatus() {
        Thread ssoDaemon = SoffidApplication.getSsoDaemon();
        if (threadRunning(ssoDaemon)) {
            return "<p>SSO Daemon RUNNING</p>";
        }
        return "<p>SSO Daemon FAILED </p>";
    }

    public String getTaskGeneratorThreadStatus() {
        if (Engine.getEngine() != null && Engine.getEngine().isAlive()) {
            return "<p>Engine RUNNING</p>";
        }
        return "<p>Engine STOPPED </p>";
    }

    public String getTasquesPendents() throws InternalErrorException {
        TaskQueue taskQueue = ServerServiceLocator.instance().getTaskQueue();
        int tasquesPendents = taskQueue.countTasks();
        return "<p><a href=\"tasquesPendents\" onclick=\"document.getElementById('tasquesPendents').submit();return false;\">Tasks:</a> "
                + tasquesPendents + "</p>";
    }

    public String getAgentsInformation() throws InternalErrorException {
        TaskGenerator taskGenerator = ServerServiceLocator.instance().getTaskGenerator();
        int workingThreads = 0;
        for ( DispatcherHandler dispatcher: taskGenerator.getDispatchers()) {
            if (dispatcher.isActive()) {
                workingThreads++;
            }
        }
        return "<p><a href=agents>Agents:</a> " + workingThreads + "</p>";
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }

    public Date getRootCertificateNotValidAfter() {
        try {
            File f = SeyconKeyStore.getRootKeyStoreFile();
            if (f.canRead()) {
                KeyStore s = SeyconKeyStore.loadKeyStore(f);
                X509Certificate cert = (X509Certificate) s
                        .getCertificate(SeyconKeyStore.ROOT_KEY);
                Date notAfter = cert.getNotAfter();
                if (notAfter != null)
                    return notAfter;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public Date getServerCertificateNotValidAfter() {
        try {
            File f = SeyconKeyStore.getKeyStoreFile();
            if (f.canRead()) {
                KeyStore s = SeyconKeyStore.loadKeyStore(f);
                X509Certificate cert = (X509Certificate) s
                        .getCertificate(SeyconKeyStore.MY_KEY);
                Date notAfter = cert.getNotAfter();
                if (notAfter != null)
                    return notAfter;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

}

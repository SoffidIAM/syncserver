package es.caib.seycon.ng.sync.web.internal;

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

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.SeyconApplication;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.Engine;
import es.caib.seycon.ng.sync.engine.cert.CertificateServer;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.jetty.JettyServer;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.ng.sync.servei.TaskQueue;
import es.caib.seycon.ssl.SeyconKeyStore;

public class PublicCertServlet extends HttpServlet {
    Logger log = Log.getLogger("PublicCertServlet");

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("binary/octet-stream");
        try {
            CertificateServer cs = new CertificateServer();
            X509Certificate cert = cs.getRoot();
            
            response.setContentLength(cert.getEncoded().length);
            response.getOutputStream().write (cert.getEncoded());
        } catch (Exception e) {
            log.warn("Error invoking " + request.getRequestURI(), e);
        }
    }


}

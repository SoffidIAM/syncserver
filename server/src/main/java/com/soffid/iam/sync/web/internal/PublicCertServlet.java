package com.soffid.iam.sync.web.internal;

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
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.jetty.JettyServer;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.ng.sync.servei.TaskQueue;

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

package com.soffid.iam.sync.web.internal;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.sync.engine.cert.CertificateServer;

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

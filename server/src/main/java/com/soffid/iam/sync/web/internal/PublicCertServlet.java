package com.soffid.iam.sync.web.internal;

import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.engine.cert.CertificateServer;

import es.caib.seycon.util.Base64;

public class PublicCertServlet extends HttpServlet {
    Logger log = Log.getLogger("PublicCertServlet");

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("binary/octet-stream");
        try {
        	CertificateServer cs = new CertificateServer();
        	if ("2".equals(request.getParameter("version"))) {
        		List<X509Certificate> certs = cs.loadTrustedCertificates();
        		StringBuffer sb = new StringBuffer();
        		sb.append("----- CERTS _-----\n");
        		for (X509Certificate cert: certs) {
        			sb.append(Base64.encodeBytes(cert.getEncoded(), Base64.DONT_BREAK_LINES));
        			sb.append("\n");
        		}
        		byte[] data = sb.toString().getBytes();
	            response.setContentLength(data.length);
	            response.getOutputStream().write (data);
        	} else {
	            X509Certificate cert = cs.getRoot();
	            if (cert == null) {
	            	KeyStore ks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getKeyStoreFile());
	            	cert = (X509Certificate) ks.getCertificate(SeyconKeyStore.MY_KEY);
	            }
	            response.setContentLength(cert.getEncoded().length);
	            response.getOutputStream().write (cert.getEncoded());
        	}
        } catch (Exception e) {
            log.warn("Error invoking " + request.getRequestURI(), e);
        }
    }


}

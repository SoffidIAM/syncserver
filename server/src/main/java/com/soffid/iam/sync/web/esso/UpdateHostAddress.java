package com.soffid.iam.sync.web.esso;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.service.NetworkService;
import com.soffid.iam.sync.ServerServiceLocator;

import es.caib.seycon.ng.exception.UnknownNetworkException;


public class UpdateHostAddress extends HttpServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UpdateHostAddress.class);

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");
        try {
            String name = request.getParameter("name");
            String serial = request.getParameter("serial");
            String ip = com.soffid.iam.utils.Security.getClientIp();
            NetworkService xs = ServerServiceLocator.instance().getNetworkService();
            xs.registerDynamicIP(name, ip, serial);
            response.getOutputStream().println("OK");
        } catch (Exception e) {
            if (! (e instanceof UnknownNetworkException)) {
            	log.warn("Error invoking " + request.getRequestURI(), e);
            	response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getOutputStream().println("ERROR|"+e.toString()); 
            } else { 
            	log.warn("Error registering host: "+e.getMessage());
                response.getOutputStream().println("OK");
            }
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }
}

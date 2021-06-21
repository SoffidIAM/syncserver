package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.sync.hub.server.HubQueue;

public class GatewayDiagnosticServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;


    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	String token = System.getenv("DIAG_TOKEN");
    	if (token != null) {
    		String auth = req.getHeader("Authorization");
    		if (auth == null || !auth.equals("Bearer "+token))
    		{
    			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    			return;
    		}
    	}
        resp.setContentType("text/plain; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        HubQueue.instance().setDebug(req.getParameter("debug") != null);
        HubQueue.instance().dump(writer);
        writer.close();
    }

}

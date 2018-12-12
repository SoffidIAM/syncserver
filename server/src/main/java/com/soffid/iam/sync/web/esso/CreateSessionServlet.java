package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.Session;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.engine.session.SessionManager;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class CreateSessionServlet extends HttpServlet {

    Logger log = Log.getLogger("CreateSessionServlet");
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String user = req.getParameter("user");
        String clientIP = req.getParameter("clientIP");
        String port = req.getParameter("port");
        resp.setContentType("text/plain; charset=UTF-8");
        log.info("CreateSession: user="+user+" host="+req.getRemoteAddr()+" client="+clientIP+" port="+port,
                null, null);
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        try {
        	NetworkService xs = ServiceLocator.instance().getNetworkService();
        	Host maq = xs.findHostByIp(req.getRemoteAddr());
        	if (maq == null)
        	{
        		throw new InternalErrorException("Unknown host "+req.getRemoteAddr());
        	}
        	Host client = null;
        	if (clientIP != null)
        		
        	{
        		client = xs.findHostByIp(clientIP);
            	if (client == null)
            	{
            		client = new Host();
            		client.setName(clientIP);
            		client.setIp(clientIP);
            	}
        	}
            Session session = SessionManager.getSessionManager().addSession(
                    maq,
                    Integer.parseInt(port),
                    user,
                    null, // Password,
                    client,
                    null,  // Sense clau
                    false, false, null); // Sense tancar altres sessions
            writer.write("OK|");
            writer.write(Long.toString(session.getId()));
            writer.write("\n");
        } catch (Exception e) {
            writer.write(e.getClass().getName() + "|" + e.getMessage()+"\n");
        }
        writer.close ();
    }

}

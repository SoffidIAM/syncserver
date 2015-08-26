package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Host;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.service.AuthorizationService;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.engine.session.SessionManager;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.utils.Security;

public class KeepaliveSessionServlet extends HttpServlet {

    Logger log = Log.getLogger("KeepaliveSessionServlet");
    
    private SessionService sessioService;
    private NetworkService xarxaService;
    private AuthorizationService autoritzacioService;
    private UserService usuariService;

    public KeepaliveSessionServlet() {
        sessioService = ServerServiceLocator.instance().getSessionService();
        xarxaService = ServerServiceLocator.instance().getNetworkService();
        autoritzacioService = ServerServiceLocator.instance().getAuthorizationService();
        usuariService = ServerServiceLocator.instance().getUserService();
    }
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        String user = req.getParameter("user");
        String key = req.getParameter("key");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        String[] auths = null;
        try {
            Security.nestedLogin(user, new String [] { 
                Security.AUTO_HOST_ALL_QUERY+Security.AUTO_ALL
            });

            try {
                Session sessio = null;
                User usuari = usuariService.findUserByUserName(user);
                for (Session s: sessioService.getActiveSessions(usuari.getId())) {
                    if (key.equals (s.getKey()))
                    {
                        sessio = s;
                        break;
                    }
                }
                if (sessio == null) {
                	writer.write("EXPIRED|Invalid session");
                }
                else
                {
                    Host maq = xarxaService.findHostByName(sessio.getServerHostName());
                    if (maq == null || !maq.getIp().equals(req.getRemoteAddr())) {
                        writer.write("EXPIRED|Invalid host");
                    } else {
                    	sessioService.sessionKeepAlive(sessio);
                    	writer.write("OK|");
                    }
                }
            } catch (Exception e) {
                log("Error keeping alive session", e);
                writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
            } finally {
                Security.nestedLogoff();
            }
        } catch (Exception e1) {
            throw new ServletException(e1);
        }
        writer.close();
    }

}

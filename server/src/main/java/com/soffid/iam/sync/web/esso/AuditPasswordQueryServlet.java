package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Calendar;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Audit;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;

import es.caib.seycon.ng.exception.UnknownUserException;

public class AuditPasswordQueryServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Logger log = Log.getLogger("AuditPasswordQueryServlet");
	
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String user = req.getParameter("user");
        String key = req.getParameter("key");
        String account = req.getParameter("account");
        String system = req.getParameter("system");
        String url = req.getParameter ("url");
        String app = req.getParameter ("application");

        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        SessionService ss = ServerServiceLocator.instance().getSessionService();
        UserService usuariService = ServerServiceLocator.instance().getUserService();
        try {
            User usuari = usuariService.findUserByUserName(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Session sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getKey().equals(key) ) {
                	Audit audit = new Audit ();
               		audit.setAction( url == null ? "E" : "W");
               		audit.setAccount(account);
               		audit.setApplication(app == null ? url: app);
               		audit.setAuthor(sessio.getUserName());
               		audit.setDatabase(system);
               		audit.setCalendar(Calendar.getInstance());
               		audit.setObject("SSO");
               		ServiceLocator.instance().getAuditService().create(audit);
                    writer.write("OK");
                    return;
                }
            }
            writer.write("ERROR|Invalid key");
            log.warn("Invalid key {} for user {}", key, user);
        } catch (Exception e) {
            log.warn("Error getting keys", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        } finally {
        	writer.close ();
        }
    }

}

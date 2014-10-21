package es.caib.seycon.ng.sync.web.internal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.PolicyCheckResult;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.PasswordService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.jetty.Invoker;
import es.caib.seycon.ng.sync.servei.LogonService;

public class PropagatePasswordServlet extends HttpServlet {
    
    Logger log = Log.getLogger("PropagatePasswordServlet");
    private LogonService logonService;
	private PasswordService passwordService;
    
    public PropagatePasswordServlet () {
        logonService = ServerServiceLocator.instance().getLogonService();
        passwordService = ServerServiceLocator.instance().getPasswordService();
    }
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
            String user = req.getParameter("user");
            String pass = req.getParameter("password");
            String domain = req.getParameter("domain");
            boolean testOnly = "true".equals(req.getParameter("test"));
            
            resp.setContentType("text/plain; charset=UTF-8");
            log.info("PropagatePassword: user={} domain={}", user, domain);
            BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
            try {
            	if (testOnly)
            	{
            		// When modifying a.d. passwod, a.d. will ask for password correctnes, but this password
            		// does not apply to password policy as it is the current password
            		if ( passwordService.checkPassword(user, domain, new Password(pass), false, true) )
            			writer.write ("OK");
            		// 
            		else
            		{
	            		PolicyCheckResult r = passwordService.checkPolicy(user, domain, new Password(pass));
	            		if (r == null)
	            			writer.write("IGNORE");
	            		else if (r.isValid())
	            			writer.write ("OK");
	            		else
	            			writer.write ("ERROR|"+r.getReason());
            		}
            	}
            	else
            	{
            		logonService.propagatePassword(user, domain, pass);
            		writer.write("ok");
            	}
            } catch (InternalErrorException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                log ("Error propagating password ", e);
                writer.write("ERROR: "+e.toString());
            }
            writer.close ();
    }

}

package com.soffid.iam.sync.web.internal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.rmi.RemoteException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PolicyCheckResult;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.PasswordService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.service.Messages;
import com.soffid.iam.utils.ConfigurationCache;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class PropagatePasswordServlet extends HttpServlet {
    
    Logger log = Log.getLogger("PropagatePasswordServlet");
    private LogonService logonService;
	private PasswordService passwordService;
	private AccountService accountService;
    
    public PropagatePasswordServlet () {
        logonService = ServiceLocator.instance().getLogonService();
        accountService = ServiceLocator.instance().getAccountService();
        passwordService = ServiceLocator.instance().getPasswordService();
    }
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
            String user = req.getParameter("user");
            String pass = req.getParameter("password");
            String domain = req.getParameter("domain");
            boolean testOnly = "true".equals(req.getParameter("test"));
            
            resp.setContentType("text/plain; charset=UTF-8");
            if (testOnly)
            	log.info("CheckPasswordPolicy: user={} domain={} source="+req.getRemoteHost()+"("+com.soffid.iam.utils.Security.getClientIp()+")", user, domain);
            else
            	log.info("PropagatePassword: user={} domain={} source="+req.getRemoteHost()+"("+com.soffid.iam.utils.Security.getClientIp()+")", user, domain);
            BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
            try {
            	if (domain == null) {
            		log.info("Ignoring request without domain name for account {}", user, null);
            	} else {
	        		Account acc = accountService.findAccount(user, domain);
	        		if (acc == null) {
	    	       		String prefix = ConfigurationCache.getProperty("soffid.propagatepassword.prefix");
	    	       		if (prefix != null)
	    	       			user = prefix + user;
		        		acc = accountService.findAccount(user, domain);
	        		}
	        		if (acc == null)
	        		{
	        			boolean first = true;
	        			for (Account account: accountService.findAccountByJsonQuery("system eq \""+quote(domain)+"\" and name ew \"\\\\"+quote(user)+"\"")) {
	        				doPropagate(account.getName(), pass, domain, testOnly, first ? writer: null);
	        				first = false;
	        			}
	        			if (first && testOnly)
			            	doPropagate(user, pass, domain, testOnly, writer);
	        		}
	        		else
	        		{
		            	doPropagate(user, pass, domain, testOnly, writer);
	        		}
            	}
            } catch (InternalErrorException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                log ("Error propagating password ", e);
                writer.write("ERROR: "+e.toString());
            }
            writer.close ();
    }

	public void doPropagate(String user, String pass, String domain, boolean testOnly, BufferedWriter writer) 
			throws InternalErrorException, IOException, RemoteException {
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
				if (writer != null) {
					if (r == null)
						writer.write("IGNORE");
					else if (r.isValid())
						writer.write ("OK");
					else
						writer.write ("ERROR|"+r.getReason());
				}
			}
		}
		else
		{
			logonService.propagatePassword(user, domain, pass);
			if (writer != null)
				writer.write("ok");
		}
	}

	private String quote(String user) {
		return user.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}
}

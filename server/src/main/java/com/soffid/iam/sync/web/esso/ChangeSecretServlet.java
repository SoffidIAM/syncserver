package com.soffid.iam.sync.web.esso;

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
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.System;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class ChangeSecretServlet extends HttpServlet {

	private AccountService accountSvc;
	private SessionService ss;
	private UserService usuariService;

	public ChangeSecretServlet ()
	{
		accountSvc = ServiceLocator.instance().getAccountService();
		ss = ServerServiceLocator.instance().getSessionService();
		usuariService = ServerServiceLocator.instance().getUserService();
	}
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetSecretsServlet");

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {

        String user = req.getParameter("user");
        String key = req.getParameter("key");
        String secret = req.getParameter("secret");
        String account = req.getParameter("account");
        String system = req.getParameter("system");
        String value = req.getParameter("value");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                        "UTF-8"));
        try {
            User usuari = usuariService.findUserByUserName(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Session sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getKey().equals(key) ) {
                    writer.write(doChangeSecret(usuari, secret, account, system, value));
                    writer.close();
                    return;
                }
            }
            writer.write("ERROR|Invalid key");
            log.warn("Invalid key {} for user {}", key, user);
        } catch (Exception e) {
            log.warn("Error getting keys", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        }
        writer.close();

    }

    /**
	 * @param usuari.getCodi()
	 * @param secret
	 * @param account
	 * @param system
     * @param value 
	 * @return
     * @throws InternalErrorException 
     * @throws RemoteException 
	 */
	private String doChangeSecret (User usuari, String secret, String account,
					String system, String value) throws InternalErrorException, RemoteException
	{

        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
        if (secret != null)
        	sss.putSecret(usuari, secret, new Password(value));
        else if (system != null && account != null)
        {
        	Account acc = ServiceLocator.instance().getAccountService().findAccount(account, system);
        	
        	if (acc instanceof UserAccount)
        	{
        		if (! ((UserAccount) acc).getUser().equals(usuari.getUserName()))
        			return "ERROR|Not authorized";
        	}
        	else
        	{
        		boolean found = false;
        		for (Account acc2: ServiceLocator.instance().getAccountService().getUserGrantedAccounts(usuari))
        		{
        			if (acc2.getId().equals(acc.getId()))
        			{
        				found = true;
        				break;
        			}
        		}
        		if (! found)
        			return "ERROR|Not authorized";
        	}
           	sss.setPassword(acc.getId(), new Password(value));

           	AccountService acs = ServiceLocator.instance().getAccountService();
           	UserDomainService dominiService = ServiceLocator.instance().getUserDomainService();
           	DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
           	System dispatcher = dispatcherService.findDispatcherByName(system);
        	PasswordPolicy politica = dominiService.findPolicyByTypeAndPasswordDomain(
        			acc.getPasswordPolicy(), dispatcher.getPasswordsDomain());
    		Long l = null;
    		
    		if (politica != null && politica.getMaximumPeriod() != null && politica.getType().equals("M"))
    		    l = politica.getMaximumPeriod();
    		else if (politica != null && politica.getRenewalTime() != null && politica.getType().equals("A"))
    			l = politica.getRenewalTime();

           	acs.updateAccountPasswordDate(acc, l);

           	ServiceLocator.instance().getLogonService().propagatePassword(system, account, value);
    		
        }
        return "OK";
    }

}

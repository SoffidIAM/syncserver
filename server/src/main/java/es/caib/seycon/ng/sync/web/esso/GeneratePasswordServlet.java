package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.sso.Secret;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.SecretStoreService;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ng.comu.Password;

public class GeneratePasswordServlet extends HttpServlet {

	private AccountService accountSvc;
	private SessioService ss;
	private UsuariService usuariService;
	private ServerService passwordService;

	public GeneratePasswordServlet ()
	{
		accountSvc = ServiceLocator.instance().getAccountService();
		ss = ServerServiceLocator.instance().getSessioService();
		usuariService = ServerServiceLocator.instance().getUsuariService();
		passwordService = ServerServiceLocator.instance().getServerService();
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
        String account = req.getParameter("account");
        String system = req.getParameter("system");
        String value = req.getParameter("value");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                        "UTF-8"));
        try {
            Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Sessio sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getClau().equals(key) ) {
                    writer.write(generatePassword(usuari, account, system));
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
     * @return
     * @throws InternalErrorException 
	 */
	private String generatePassword (Usuari usuari, String account,
					String system) throws InternalErrorException
	{
        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
        if (system != null && account != null)
        {
        	Account acc = ServiceLocator.instance().getAccountService().findAccount(account, system);
        	
        	if (acc instanceof es.caib.seycon.ng.comu.UserAccount)
        	{
        		if (! ((es.caib.seycon.ng.comu.UserAccount) acc).getUser().equals(usuari.getCodi()))
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
        	Password password = passwordService.generateFakePassword(account, system);
            return "OK|"+password.getPassword();
        } else {
        	return "ERROR|Missing arguments";
        }
    }

}

package es.caib.seycon.ng.sync.web.esso;

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

import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.PoliticaContrasenya;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.sso.Secret;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.DominiUsuariService;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.SecretStoreService;
import es.caib.seycon.ng.comu.Password;

public class ChangeSecretServlet extends HttpServlet {

	private AccountService accountSvc;
	private SessioService ss;
	private UsuariService usuariService;

	public ChangeSecretServlet ()
	{
		accountSvc = ServiceLocator.instance().getAccountService();
		ss = ServerServiceLocator.instance().getSessioService();
		usuariService = ServerServiceLocator.instance().getUsuariService();
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
            Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Sessio sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getClau().equals(key) ) {
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
	private String doChangeSecret (Usuari usuari, String secret, String account,
					String system, String value) throws InternalErrorException, RemoteException
	{

        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
        if (secret != null)
        	sss.putSecret(usuari, secret, new Password(value));
        else if (system != null && account != null)
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
           	sss.setPassword(acc.getId(), new Password(value));

           	AccountService acs = ServiceLocator.instance().getAccountService();
           	DominiUsuariService dominiService = ServiceLocator.instance().getDominiUsuariService();
           	DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
           	Dispatcher dispatcher = dispatcherService.findDispatcherByCodi(system);
        	PoliticaContrasenya politica = dominiService.findPoliticaByTipusAndDominiContrasenyas(
        			acc.getPasswordPolicy(), dispatcher.getDominiContrasenyes());
    		Long l = null;
    		
    		if (politica != null && politica.getDuradaMaxima() != null && politica.getTipus().equals("M"))
    		    l = politica.getDuradaMaxima();
    		else if (politica != null && politica.getTempsRenovacio() != null && politica.getTipus().equals("A"))
    			l = politica.getTempsRenovacio();

           	acs.updateAccountPasswordDate(acc, l);

           	ServiceLocator.instance().getLogonService().propagatePassword(system, account, value);
    		
        }
        return "OK";
    }

}

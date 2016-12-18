package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.LinkedList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.AttributeVisibilityEnum;
import com.soffid.iam.api.VaultFolder;

import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountAccessLevelEnum;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.AutoritzacioRol;
import es.caib.seycon.ng.comu.DadaUsuari;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.PoliticaContrasenya;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.TipusDada;
import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.AccountService;
import es.caib.seycon.ng.servei.DadesAddicionalsService;
import es.caib.seycon.ng.servei.DominiUsuariService;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.SecretStoreService;
import es.caib.seycon.ng.utils.Security;
import es.caib.seycon.ng.comu.Password;

public class ChangeSecretServlet extends HttpServlet {

	private SessioService ss;
	private UsuariService usuariService;
	private AccountService accountService;

	public ChangeSecretServlet ()
	{
		ss = ServerServiceLocator.instance().getSessioService();
		usuariService = ServerServiceLocator.instance().getUsuariService();
		accountService = ServiceLocator.instance().getAccountService();
				
	}
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetSecretsServlet"); //$NON-NLS-1$

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {

        String ssoAttribute = req.getParameter("sso"); //$NON-NLS-1$
        String user = req.getParameter("user"); //$NON-NLS-1$
        String key = req.getParameter("key"); //$NON-NLS-1$
        String secret = req.getParameter("secret"); //$NON-NLS-1$
        String description = req.getParameter("description"); //$NON-NLS-1$
        String account = req.getParameter("account"); //$NON-NLS-1$
        String system = req.getParameter("system"); //$NON-NLS-1$
        String value = req.getParameter("value"); //$NON-NLS-1$
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                        "UTF-8")); //$NON-NLS-1$
        try {
            Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Sessio sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getClau().equals(key) ) {
                    writer.write(doChangeSecret(usuari, secret, account, system, ssoAttribute, description, value));
                    writer.close();
                    return;
                }
            }
            writer.write(Messages.getString("ChangeSecretServlet.0")); //$NON-NLS-1$
            log.warn("Invalid key {} for user {}", key, user); //$NON-NLS-1$
        } catch (Exception e) {
            log.warn("Error getting keys", e); //$NON-NLS-1$
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        writer.close();

    }

    /**
	 * @param usuari.getCodi()
	 * @param secret
	 * @param account
	 * @param system
     * @param value 
     * @param value2 
	 * @return
     * @throws InternalErrorException 
     * @throws RemoteException 
     * @throws AccountAlreadyExistsException 
     * @throws UnsupportedEncodingException 
	 */
	private String doChangeSecret (Usuari usuari, String secret, String account,
					String system, String ssoAttribute, String description, String value) throws InternalErrorException, RemoteException, AccountAlreadyExistsException, UnsupportedEncodingException
	{

		Security.nestedLogin(usuari.getCodi(), Security.getAuthorizations().toArray(new String [0]));
		try
		{
	        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
	        if (secret != null)
	        	sss.putSecret(usuari, secret, new Password(value));
	        else if (account == null || account.trim().length() == 0)
	        {
	    		if (canCreateAccount (usuari, system))
	    		{
	    			Account acc = createAccount (system, usuari, description);
	    			return "OK|"+acc.getName();
	           	}
	    		else
	    		{
	    			return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	    		}
	        }
	        else if (system != null && account != null && system.length() > 0 && account.length() > 0)
	        {
	        	Account acc = ServiceLocator.instance().getAccountService().findAccount(account, system);
	        	
	        	if (acc == null)
	        	{
	    			return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	        	}
	        	
	        	if (acc instanceof es.caib.seycon.ng.comu.UserAccount)
	        	{
	        		if (! ((es.caib.seycon.ng.comu.UserAccount) acc).getUser().equals(usuari.getCodi()))
	        			return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	        	}
	        	else
	        	{
	        		boolean found = false;
	        		for (Account acc2: ServiceLocator.instance().getAccountService().getUserGrantedAccounts(usuari, AccountAccessLevelEnum.ACCESS_MANAGER))
	        		{
	        			if (acc2.getId().equals(acc.getId()))
	        			{
	        				found = true;
	        				break;
	        			}
	        		}
	        		if (! found)
	        			return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	        	}
	           	
	           	AccountService acs = ServiceLocator.instance().getAccountService();
	
	           	if (ssoAttribute == null || ssoAttribute.length() == 0)
	           	{
	               	sss.setPassword(acc.getId(), new Password(value));
	
	               	DominiUsuariService dominiService = ServiceLocator.instance().getDominiUsuariService();
		           	DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
		           	Dispatcher dispatcher = dispatcherService.findDispatcherByCodi(system);
		        	PoliticaContrasenya politica = dominiService.findPoliticaByTipusAndDominiContrasenyas(
		        			acc.getPasswordPolicy(), dispatcher.getDominiContrasenyes());
		    		Long l = null;
		    		
		    		if (politica != null && politica.getDuradaMaxima() != null && politica.getTipus().equals("M")) //$NON-NLS-1$
		    		    l = politica.getDuradaMaxima();
		    		else if (politica != null && politica.getTempsRenovacio() != null && politica.getTipus().equals("A")) //$NON-NLS-1$
		    			l = politica.getTempsRenovacio();
		
		           	acs.updateAccountPasswordDate(acc, l);
		
		           	ServiceLocator.instance().getLogonService().propagatePassword(account, system, value);
	           	} else {
	           		String actualAttribute = "SSO:"+ssoAttribute;
	           		for ( DadaUsuari du: accountService.getAccountAttributes(acc))
	           		{
	           			if (du.getCodiDada().equals (actualAttribute) && du.getId() != null)
	           			{
	       					du.setValorDada(value);
	       					accountService.updateAccountAttribute(du);
	       					return "OK";
	           			}
	           		}
	           		// Attribute not found
	           		DadesAddicionalsService metadataService = ServiceLocator.instance().getDadesAddicionalsService();
	           		TipusDada md = metadataService.findSystemDataType(system, actualAttribute);
	           		if (md == null)
	           		{
	           			md = new TipusDada();
	           			md.setAdminVisibility(AttributeVisibilityEnum.EDITABLE);
	           			md.setUserVisibility(AttributeVisibilityEnum.EDITABLE);
	           			md.setOperatorVisibility(AttributeVisibilityEnum.EDITABLE);
	           			md.setCodi(actualAttribute);
	           			if (ssoAttribute.equals("Server") || ssoAttribute.equals("URL"))
	           			{
	               			md.setLabel(ssoAttribute);
	               			md.setType(TypeEnumeration.STRING_TYPE);
	           			}
	           			else
	           			{
	           				md.setLabel("Form data");
	               			md.setType(TypeEnumeration.SSO_FORM_TYPE);
	           			}
	           			md.setSize(1024);
	           			md.setOrdre(0L);
	           			md.setSystemName(system);
	           			md.setRequired(false);
	           			md = metadataService.create(md);
	           		}
	           		DadaUsuari du = new DadaUsuari();
	           		du.setAccountName(account);
	           		du.setSystemName(system);
	           		du.setCodiDada(md.getCodi());
					du.setValorDada(value);
	           		acs.createAccountAttribute(du);
	           	}
	    		
	        } else {
				return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	        }
	        return "OK"; //$NON-NLS-1$
		} finally {
			Security.nestedLogoff();
		}
    }

	/**
	 * 
	 * @param system
	 * @return
	 * @throws InternalErrorException 
	 */
	private long findLastAccount (String system) throws InternalErrorException
	{
		long bits = 0;
		long top = 0;
		long attempt = 1;
		/**
		 * Find radix the first account with number = 2 ^ radix
		 */
		do
		{
			Account acc = accountService.findAccount(""+attempt, system);
			if (acc == null) break;
			top = attempt;
			attempt = attempt + attempt;
			bits ++ ;
		} while (true);
		/**
		 * Now look for the other bits
		 * top exists
		 * attempt does not exist
		 */
		long step = top;
		while (bits > 1)
		{
			step = step / 2;
			attempt = top + step;
			Account acc = accountService.findAccount(""+attempt, system);
			if (acc != null) top = attempt;
			bits --;
		}
		return top;
	}
	
	
	
	private Account createAccount(String system, Usuari owner, String description) throws InternalErrorException, AccountAlreadyExistsException {
		long i = findLastAccount (system) + 1;
		
		
		
		Account acc = new Account();
		acc.setName(""+i);
		acc.setDescription(description);
		acc.setDispatcher(system);
		acc.setOwnerUsers(new LinkedList<Usuari>());
		acc.getOwnerUsers().add(owner);
		String ssoPolicy = System.getProperty("AutoSSOPolicy"); //$NON-NLS-1$
		if (ssoPolicy == null)
			throw new InternalErrorException (Messages.getString("ChangeSecretServlet.22")); //$NON-NLS-1$
		acc.setType(AccountType.SHARED);
		acc.setPasswordPolicy(ssoPolicy);
		// Search for personal folder
		VaultFolder vf = ServiceLocator.instance().getVaultService().getPersonalFolder();
			
		if (vf != null)
		{
			acc.setVaultFolder(vf.getName());
			acc.setVaultFolderId(vf.getId());
		}
			
		return ServiceLocator.instance().getAccountService().createAccount(acc);
	}

	private boolean canCreateAccount(Usuari usuari, 
			String system) throws InternalErrorException {
		String authSystem = System.getProperty("AutoSSOSystem"); //$NON-NLS-1$
		if (authSystem != null && authSystem.equals(system))
		{
			Collection<AutoritzacioRol> auts = ServiceLocator
					.instance()
					.getAutoritzacioService()
					.getUserAuthorization("sso:manageAccounts", usuari.getCodi());
			return ! auts.isEmpty();
		}
		else 
		{
			if (authSystem == null)
			{
				log.info("Missing configuration property AutoSSOSystem. Please,  configure to enable ESSO clients", null, null);
			}
			return false;
		}
	}

}

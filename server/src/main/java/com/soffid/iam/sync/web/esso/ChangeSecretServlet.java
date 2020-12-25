package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.AttributeVisibilityEnum;
import com.soffid.iam.api.AuthorizationRole;
import com.soffid.iam.api.DataType;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.System;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.UserData;
import com.soffid.iam.api.VaultFolder;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.comu.AccountAccessLevelEnum;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
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
    Logger log = Log.getLogger("GetSecretsServlet"); //$NON-NLS-1$
    static Map<String,String> defaultDispatcher = new HashMap<String, String>();
    
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
        
        User usuari;
		try {
			usuari = usuariService.findUserByUserName(user);
			if (usuari == null)
				throw new UnknownUserException(user);
			String userAccount = user;
			String dispatcher = defaultDispatcher.get(Security.getCurrentTenantName());
			if (dispatcher == null)
			{
				dispatcher = ServiceLocator.instance().getDispatcherService().findSoffidDispatcher().getName();
				defaultDispatcher.put(Security.getCurrentTenantName(), dispatcher);
			}
			for ( UserAccount ua: accountSvc.findUsersAccounts(usuari.getUserName(), dispatcher))
			{
				userAccount = ua.getName();
			}
	        Security.nestedLogin(userAccount, new String[] {
	        		Security.AUTO_USER_QUERY+Security.AUTO_ALL,
	        		Security.AUTO_ACCOUNT_QUERY+Security.AUTO_ALL,
	        		Security.AUTO_ACCOUNT_UPDATE+Security.AUTO_ALL,
	        		Security.AUTO_ACCOUNT_CREATE+Security.AUTO_ALL
	        });
	        try {

	            for (Session sessio : ss.getActiveSessions(usuari.getId())) {
	                if (sessio.getKey().equals(key) ) {
	                    writer.write(doChangeSecret(usuari, userAccount, secret, account, system, ssoAttribute, description, value));
	                    writer.close();
	                    return;
	                }
	            }
	            writer.write(Messages.getString("ChangeSecretServlet.0")); //$NON-NLS-1$
	            log.warn("Invalid key {} for user {}", key, user); //$NON-NLS-1$
	        } finally {
	        	Security.nestedLogoff();
	        }
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
     * @param value2 
	 * @return
     * @throws InternalErrorException 
     * @throws RemoteException 
     * @throws AccountAlreadyExistsException 
     * @throws UnsupportedEncodingException 
	 */
	private String doChangeSecret (User usuari, String userAccount, String secret, String account,
					String system, String ssoAttribute, String description, String value) throws InternalErrorException, RemoteException,
					AccountAlreadyExistsException, UnsupportedEncodingException
	{

		Security.nestedLogin(userAccount, Security.getAuthorizations().toArray(new String [0]));
		try
		{
	        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
	        if (secret != null)
	        {
				log.info("Storing secret {} for user {}", secret, usuari.getUserName());
	        	sss.putSecret(usuari, secret, new Password(value));
	        }
	        else if (account == null || account.trim().length() == 0)
	        {
	        	log.info("Creating account for {}", usuari.getUserName(), null);
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
	        	
	        	if (acc instanceof UserAccount)
	        	{
	        		if (! ((UserAccount) acc).getUser().equals(usuari.getUserName()))
	        			return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	        	}
	        	else
	        	{
	        		boolean found = false;
	        		for ( String user: ServiceLocator.instance().getAccountService().getAccountUsers(acc, AccountAccessLevelEnum.ACCESS_USER))
	        		{
	        			if (user.equals(usuari.getUserName()))
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
					log.info("Setting password for {} at {}", account, system);
	               	sss.setPassword(acc.getId(), new Password(value));
	
	               	UserDomainService dominiService = ServiceLocator.instance().getUserDomainService();
		           	DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
		           	System dispatcher = dispatcherService.findDispatcherByName(system);
		        	PasswordPolicy politica = dominiService.findPolicyByTypeAndPasswordDomain(
		        			acc.getPasswordPolicy(), dispatcher.getPasswordsDomain());
		    		Long l = null;
		    		
		    		if (politica != null && politica.getMaximumPeriod() != null && politica.getType().equals("M")) //$NON-NLS-1$
		    		    l = politica.getMaximumPeriod();
		    		else if (politica != null && politica.getRenewalTime() != null && politica.getType().equals("A")) //$NON-NLS-1$
		    			l = politica.getRenewalTime();
		           	acs.updateAccountPasswordDate(acc, l);
		           	ServiceLocator.instance().getLogonService().propagatePassword(account, system, value);
	           	} else {
	           		if ( value.length() < 1024)
	           		{
	           			
	           			if (ssoAttribute.equals("Server") ) 
	           				acc.setServerName(value);
	           			else if (ssoAttribute.equals("URL"))
	           				acc.setLoginUrl(value);
	           			else {
	           				String actualAttribute = "SSO:"+ssoAttribute;
	           				acc.getAttributes().put(actualAttribute, value);
	           				// Attribute not found
	           				AdditionalDataService metadataService = ServiceLocator.instance().getAdditionalDataService();
	           				DataType md = metadataService.findSystemDataType(system, actualAttribute);
	           				if (md == null)
	           				{
	           					md = new DataType();
	           					md.setAdminVisibility(AttributeVisibilityEnum.EDITABLE);
	           					md.setUserVisibility(AttributeVisibilityEnum.EDITABLE);
	           					md.setOperatorVisibility(AttributeVisibilityEnum.EDITABLE);
	           					md.setCode(actualAttribute);
	           					md.setValidationExpression("false");
	           					if (ssoAttribute.equals("Server"))
	           					{
	           						md.setLabel("Server");
	           						md.setType(TypeEnumeration.STRING_TYPE);
	           					}
	           					else
	           					{
	           						md.setLabel("Form data");
	           						md.setType(TypeEnumeration.SSO_FORM_TYPE);
	           					}
	           					md.setSize(1024);
	           					md.setOrder(0L);
	           					md.setSystemName(system);
	           					md.setRequired(false);
	           					md = metadataService.create(md);
	           				}
	           			}
		           		acs.updateAccount2(acc);
	           		}
	           	}
	    		
	        } else {
				return Messages.getString("ChangeSecretServlet.NotAuth"); //$NON-NLS-1$
	        }
		} finally {
			Security.nestedLogoff();
		}
        return "OK"; //$NON-NLS-1$
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
			Account acc = accountSvc.findAccount(""+attempt, system);
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
			Account acc = accountSvc.findAccount(""+attempt, system);
			if (acc != null) top = attempt;
			bits --;
		}
		return top;
	}
	
	
	
	private Account createAccount(String system, User owner, String description) throws InternalErrorException, AccountAlreadyExistsException {
		long i = findLastAccount (system) + 1;
		
		
		
		Account acc = new Account();
		acc.setName(""+i);
		acc.setDescription(description);
		acc.setSystem(system);
		acc.setOwnerUsers(new LinkedList<String>());
		acc.getOwnerUsers().add(owner.getUserName());
		String ssoPolicy = ConfigurationCache.getProperty("AutoSSOPolicy"); //$NON-NLS-1$
		if (ssoPolicy == null)
			throw new InternalErrorException (Messages.getString("ChangeSecretServlet.22")); //$NON-NLS-1$
		acc.setType(AccountType.IGNORED);
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

	private boolean canCreateAccount(User usuari, 
			String system) throws InternalErrorException {
		String authSystem = ConfigurationCache.getProperty("AutoSSOSystem"); //$NON-NLS-1$
		if (authSystem != null && authSystem.equals(system))
		{
			System soffid = ServiceLocator.instance().getDispatcherService().findSoffidDispatcher();
			for (UserAccount account: accountSvc.findUsersAccounts(usuari.getUserName(), soffid.getName()))
			{
				Collection<AuthorizationRole> auts = ServiceLocator
						.instance()
						.getAuthorizationService()
						.getUserAuthorization("sso:manageAccounts", account.getName());
				if (! auts.isEmpty())
					return true;
			}
			return false;
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

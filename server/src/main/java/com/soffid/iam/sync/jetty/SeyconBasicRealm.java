package com.soffid.iam.sync.jetty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.Collection;

import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.security.Password;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.AuthorizationRole;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.config.Config;
import com.soffid.iam.service.AuthorizationService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.service.SecretConfigurationService;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.utils.Security;

import javax.security.auth.Subject;
import javax.servlet.ServletRequest;


public class SeyconBasicRealm implements LoginService {
    Config config = null;
    Logger log = Log.getLogger("SeyconBasicRealm");
    ServerService server;
    private SecretConfigurationService sc;
    private LogonService logonService;
    private AuthorizationService authService;
	private IdentityService identityService;

    public SeyconBasicRealm() throws FileNotFoundException, IOException {
        config = Config.getConfig();
        sc = ServerServiceLocator.instance().getSecretConfigurationService(); 
        logonService = ServerServiceLocator.instance().getLogonService();
        server = ServerServiceLocator.instance().getServerService();
        authService  = ServerServiceLocator.instance().getAuthorizationService(); 
    }

    public UserIdentity login(String username, Object credentials,
            ServletRequest request) {
        try {
            if (username.startsWith("-seu-")) 
            {
                String token = URLDecoder.decode((String) credentials, "UTF-8");
                if (sc.validateAuthToken(token)) {
                	UserPrincipal p = new UserPrincipal(username.substring(5), new Password((String) credentials));
                	Subject s = new Subject();
                	p.configureSubject(s);
                	s.setReadOnly();
                	return new DefaultUserIdentity(s, p, new String[] {"SEU_CONSOLE"});
                }
            }
        } catch (Throwable e) {
            log.info("Login failed for user {}", username, null);
            log.warn("Exception:", e);
            return null;
        }
        return null;
    }

    public String getName() {
        return ("SEYCON BASIC REALM");
    }

	public boolean validate(UserIdentity user) {
		return true;
	}

	public IdentityService getIdentityService() {
		return identityService;
	}

	public void setIdentityService(IdentityService service) {
		this.identityService = service;
	}

	public void logout(UserIdentity user) {
	}


}

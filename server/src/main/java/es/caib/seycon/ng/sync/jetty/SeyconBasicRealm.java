package es.caib.seycon.ng.sync.jetty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;

import org.mortbay.jetty.Request;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.AutoritzacioRol;
import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.servei.AutoritzacioService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.LogonService;
import es.caib.seycon.ng.sync.servei.SecretConfigurationService;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ng.utils.Security;

public class SeyconBasicRealm implements UserRealm {
    Config config = null;
    Logger log = Log.getLogger("SeyconBasicRealm");
    ServerService server;
    private SecretConfigurationService sc;
    private LogonService logonService;
    private AutoritzacioService authService;

    public SeyconBasicRealm() throws FileNotFoundException, IOException {
        config = Config.getConfig();
        sc = ServerServiceLocator.instance().getSecretConfigurationService(); 
        logonService = ServerServiceLocator.instance().getLogonService();
        server = ServerServiceLocator.instance().getServerService();
        authService  = ServerServiceLocator.instance().getAutoritzacioService(); 
    }

    public Principal authenticate(String username, Object credentials,
            Request request) {
        if (config.isDebug())
            return new SimplePrincipal(username);
        try {
            if (username.equals ("-seu-")) {
                String token = (String) credentials;
                if (sc.validateAuthToken(token))
                    return new TokenPrincipal();
            }
            String password = (String) credentials;
            if (logonService.validatePassword(username, null, password) != PasswordValidation.PASSWORD_GOOD) {
                return null;
            }
        } catch (Throwable e) {
            log.info("Login failed for user {}", username, null);
            log.warn("Exception:", e);
            return null;
        }
        return new SimplePrincipal(username);
    }

    public void disassociate(Principal user) {
    }

    public String getName() {
        return ("SEYCON BASIC REALM");
    }

    public Principal getPrincipal(String username) {
        return new SimplePrincipal(username);
    }

    public boolean isUserInRole(Principal user, String role) {
        if (config.isDebug())
            return true;
        boolean authorized = false;
        try {
            if (user instanceof TokenPrincipal) {
                return true;
            } else {

                Config config = Config.getConfig();
                Collection<RolGrant> roles;
                
                Collection<AutoritzacioRol> auth = authService.getUserAuthorization(Security.AUTO_MONITOR_AGENT_RESTART, user.getName());
                if (auth != null && auth.size() > 0)
                    authorized = true;
                
                auth = authService.getUserAuthorization(Security.AUTO_AUTHORIZATION_ALL, user.getName());
                if (auth != null && auth.size() > 0)
                    authorized = true;
            }
        } catch (Throwable e) {
            authorized = false;
        }
        return authorized;
    }

    public void logout(Principal user) {
    }

    public Principal popRole(Principal user) {
        return user;
    }

    public Principal pushRole(Principal user, String role) {
        return user;
    }

    public boolean reauthenticate(Principal user) {
        return true;
    }

}

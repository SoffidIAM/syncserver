package com.soffid.iam.sync.jetty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Principal;
import java.util.Vector;

import org.bouncycastle.asn1.x509.X509Name;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.URLManager;

public class SeyconUserRealm implements UserRealm {
    Config config = null;
    Logger log = Log.getLogger("SeyconUserRealm");
    
    
    public SeyconUserRealm () throws FileNotFoundException, IOException {
        config = Config.getConfig();
    }
    
    public Principal authenticate(String username, Object credentials,
            Request request) {
        return getPrincipal (username);
    }

    public void disassociate(Principal user) {
    }

    public String getName() {
        return ("SEYCON REALM");
    }

    public Principal getPrincipal(String username) {
        X509Name name = new X509Name (username);
        Vector v = name.getValues(X509Name.CN);
        return new SimplePrincipal (v.get(0).toString());
    }

    public boolean isUserInRole(Principal user, String role) {
        if ("agent".equals(role))
            return true;
        if ("server".equals(role))
        {
            try {
                String serverList [] = config.getServerList().split("[, ]+");
                for (int i = 0; i < serverList.length; i++)
             
                {
                    URLManager m = new URLManager (serverList[i]);
                    if (m.getServerURL().getHost().equals(user.getName()))
                        return true;
                }
            } catch (Exception e) {
                log.warn("Error checking for permissions", e);
            }
            return false;
        }
        
        return false;
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

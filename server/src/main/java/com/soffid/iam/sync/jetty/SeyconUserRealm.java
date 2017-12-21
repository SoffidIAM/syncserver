package com.soffid.iam.sync.jetty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Vector;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.X509Name;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.URLManager;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.utils.Security;

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
        try {
			X500Name name = new X500Name (username);
			String domain = null;
			String n = null;
			for ( RDN rdn: name.getRDNs())
			{
				if (rdn.getFirst() != null &&
						rdn.getFirst().getType().equals( RFC4519Style.o))
					domain = rdn.getFirst().getValue().toString();
				if (rdn.getFirst() != null &&
						rdn.getFirst().getType().equals( RFC4519Style.cn))
					n = rdn.getFirst().getValue().toString();
			}
			String userName = URLEncoder.encode(n, "UTF-8");
			if (domain != null)
				userName = URLEncoder.encode(domain, "UTF-8") + "\\" + userName;
			return new SimplePrincipal (userName);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
    }

    public boolean isUserInRole(Principal user, String role) {
        if ("agent".equals(role))
            return true;
        if ("server".equals(role))
        {
        	String name = user.getName();
			if (!name.startsWith( Security.getMasterTenantName() + "\\"))
				return false;
        	int p = name.indexOf('\\');
       		name = name.substring(p+1);
            try {
                String serverList [] = config.getServerList().split("[, ]+");
                for (int i = 0; i < serverList.length; i++)
             
                {
                    URLManager m = new URLManager (serverList[i]);
                    if (m.getServerURL().getHost().equals(name))
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

package com.soffid.iam.sync.jetty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.Principal;

import javax.security.auth.Subject;
import javax.servlet.ServletRequest;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.server.UserIdentity;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.URLManager;

import es.caib.seycon.ng.utils.Security;

public class SeyconUserRealm implements LoginService {
    Config config = null;
    Logger log = Log.getLogger("SeyconUserRealm");
	private IdentityService identityService;
    
    
    public SeyconUserRealm () throws FileNotFoundException, IOException {
        config = Config.getConfig();
    }
    
    public String getName() {
        return ("SEYCON REALM");
    }

    public UserPrincipal getPrincipal(String username) {
        try {
			X500Name name = new X500Name (username);
			String domain = null;
			String n = null;
			for ( RDN rdn: name.getRDNs())
			{
				if (rdn.getFirst() != null &&
						rdn.getFirst().getType().equals( RFC4519Style.ou))
					domain = rdn.getFirst().getValue().toString();
				if (rdn.getFirst() != null &&
						rdn.getFirst().getType().equals( RFC4519Style.cn))
					n = rdn.getFirst().getValue().toString();
			}
			String userName = URLEncoder.encode(n, "UTF-8");
			if (domain != null)
				userName = URLEncoder.encode(domain, "UTF-8") + "\\" + userName;
			else
				userName = "master\\" + userName;
			return new UserPrincipal(userName, null);
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


	public UserIdentity login(String username, Object credentials, ServletRequest request) {
		UserPrincipal principal = getPrincipal(username);
		String roles[];
		if (isUserInRole(principal, "server"))
			roles = new String[] {"agent", "server"};
		else
			roles = new String[] {"agent"};
        
		Subject subject = new Subject();
        principal.configureSubject(subject);
        subject.setReadOnly();

        return new DefaultUserIdentity(null, principal, roles);
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

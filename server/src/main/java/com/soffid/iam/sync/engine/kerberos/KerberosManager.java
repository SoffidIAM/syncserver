package com.soffid.iam.sync.engine.kerberos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Random;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Password;
import com.soffid.iam.api.System;
import com.soffid.iam.config.Config;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.intf.KerberosAgent;
import com.soffid.iam.sync.intf.KerberosPrincipalInfo;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.exception.InternalErrorException;

public class KerberosManager {
    private static final long MAX_CACHE_TIME = 1000 * 60 * 60 * 24;
    static HashMap<String, KerberosCache> cache = new HashMap<String, KerberosCache>();
    Logger log = Log.getLogger("KerberosManager");
    private Properties properties;
    private TaskGenerator taskGenerator;

    public KerberosManager() throws FileNotFoundException, IOException {
        taskGenerator = ServerServiceLocator.instance().getTaskGenerator();
        properties = new Properties();
        File propertiesFile = getPropertiesFile();
        if (propertiesFile.canRead())
            properties.load(new FileInputStream(propertiesFile));
    }
    

    private Collection<DispatcherHandler> getDispatcherHandlerForRealm (String domain) throws InternalErrorException 
    {
//    	log.info("Getting systems for realm {}", domain, null);
        Collection<DispatcherHandler> result = new LinkedList<DispatcherHandler>();
        Collection<DispatcherHandler> dispatchers = taskGenerator.getDispatchers();
        for (Iterator<DispatcherHandler> it = dispatchers.iterator(); it.hasNext();) {
            DispatcherHandler handler = it.next();
            if (handler != null) {
                KerberosAgent krb = handler.getKerberosAgent();
                try {
                	if (krb != null)
                	{
	                	String actualDomain = krb.getRealmName();
	                    if (domain.equals(actualDomain)) {
	                        result.add(handler);
	                    }
                	}
                } catch (InternalErrorException e) {
                    log.warn("Error getting kerberos name in "+handler.getSystem().getName(), e);
                }
            }
        }
		return result;
    }


    public Collection<System> getSystemsForRealm (String domain) throws InternalErrorException 
    {
        Collection<System> result = new LinkedList<System>();
    	for (DispatcherHandler handler: getDispatcherHandlerForRealm(domain))
    	{
    		result.add(handler.getSystem());
    	}
        return result;
    }

    public System getSystemForRealm (String domain) throws InternalErrorException 
    {
        System result = null;
    	for (System d: getSystemsForRealm(domain))
    	{
    		if (result == null ||
    				(d.isReadOnly() ? 1 : 0) < (result.isReadOnly() ? 1 : 0) ||
    				(d.isAuthoritative() ? 1 : 0) > (result.isAuthoritative() ? 1 : 0) ||
    				d.getId().compareTo(result.getId()) < 0)
    			result = d;
    	}
        return result;
    }

    private Collection<KerberosAgent> getKerberosAgent(String domain) throws InternalErrorException {
        Collection<KerberosAgent> result = new LinkedList<KerberosAgent>();
    	for (DispatcherHandler handler: getDispatcherHandlerForRealm(domain))
    		result.add(handler.getKerberosAgent());
		return result;
    }

    private File getConfigFile() throws FileNotFoundException, IOException {
        File home = Config.getConfig().getHomeDir();
        return new File(home, "conf/krb5.conf");
    }

    private File getPropertiesFile() throws FileNotFoundException, IOException {
        File home = Config.getConfig().getHomeDir();
        return new File(home, "conf/krb5.properties");
    }

    private void generateKrbConfig() throws IOException, InternalErrorException {
        File config = getConfigFile();

        java.lang.System.setProperty("java.security.krb5.conf", config.getAbsolutePath());
        java.lang.System.setProperty("sun.security.krb5.debug", "true");
        java.lang.System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        StringBuffer realms = new StringBuffer();
        boolean reconfig = false;
        String defaultRealm = null;

        Collection<DispatcherHandler> dispatchers = taskGenerator.getDispatchers();
        for (Iterator<DispatcherHandler> it = dispatchers.iterator(); it.hasNext();) {
            DispatcherHandler handler = it.next();
            if (handler != null) {
                KerberosAgent krb = handler.getKerberosAgent();
                if (krb != null) {
                    try {
                        String realm = krb.getRealmName();
                    	if (defaultRealm == null)
                    		defaultRealm = realm;
                        String servers[] = krb.getRealmServers();
                        StringBuffer b = new StringBuffer();
                        realms.append(realm);
                        realms.append(" = {\n");
                        for (int j = 0; j < servers.length; j++) {
                            realms.append("  kdc = ");
                            realms.append(servers[j]);
                            realms.append("\n");
                            if (j > 0)
                                b.append(",");
                            b.append(servers[j]);

                        }
                        realms.append("}\n\n");
                        // Si hi ha hagut canvis, s'ha de reconfigurar
                        if (!b.toString().equals(properties.get(realm))) {
                            reconfig = true;
                            properties.put(realm, b.toString());
                        }
                    } catch (Exception e) {
                        log.warn("Error generating kerberos configuration", e);
                    }
                }
            }
        }
        
        if (reconfig) {
            PrintWriter writer = new PrintWriter(config);
            writer.println("[libdefaults]");
            writer.println("kdc_timeout=3000");
            writer.println("max_retries=2");
            writer.println("default_tkt_enctypes=aes-128-cts aes-128-cts-hmac-sha1-96 rc4-hmac");
            writer.println("default_tgs_enctypes=aes-128-cts aes-128-cts-hmac-sha1-96 rc4-hmac");
            writer.println("permitted_enctypes=aes-128-cts aes-128-cts-hmac-sha1-96 rc4-hmac des3-cbc-sha1 des-cbc-md5 des-cbc-crc");
            writer.println("default_realm="+defaultRealm);
            writer.println();
            writer.println("[realms]");
            writer.println(realms.toString());
            writer.close();
            storeProperties();
        }
    }

    private void storeProperties() throws IOException, FileNotFoundException {
        properties.store(new FileOutputStream(getPropertiesFile()), "Autogenerated by seycon");
    }

    public Subject getServerSubject(String domain) throws LoginException, FileNotFoundException,
            InternalErrorException, IOException {
        KerberosCache kc = getKerberosCache(domain);
        if (kc != null)
            return kc.subject;
        else
            return null;
    }

    private KerberosCache getKerberosCache(String domain) throws LoginException,
            FileNotFoundException, InternalErrorException, IOException {
        // Primer intent de la cache
        KerberosCache kc = cache.get(domain);
        if (kc == null) {
            try {
                generateKrbConfig();
            } catch (IOException e) {
                log.warn("Unable to configure kerberos realm " + domain, e);
            }
        }
        if (kc == null || kc.lastSet.getTime() - java.lang.System.currentTimeMillis() > MAX_CACHE_TIME) {
            if (kc == null)
                kc = new KerberosCache();
            kerberosLogin(domain, kc);
            cache.put(domain, kc);
        }
        return kc;
    }

    public String getServerPrincipal(String domain) throws LoginException, FileNotFoundException,
            InternalErrorException, IOException {
        KerberosCache kc = getKerberosCache(domain);
        if (kc != null)
            return kc.user;
        else
            return null;
    }

    private void kerberosLogin(String domain, KerberosCache kc) throws InternalErrorException,
            LoginException, FileNotFoundException, IOException {
        Collection<KerberosAgent> krbList = getKerberosAgent(domain);
        String user = null;
        String hostName = null;
        String principal = null;
        Password password = null;
        String keytabFile = null;
        LoginContext lc = null;

        ChainConfiguration.addConfiguration(domain, new KerberosLoginConfiguration(domain));
        // First try using cache
        try {
            user = properties.getProperty(domain + ".user");
            principal = properties.getProperty(domain + ".principal");
            String passwordString = properties.getProperty(domain + ".password");
            if (user != null && passwordString != null) {
                password = Password.decode(passwordString);
                lc = new LoginContext(domain, new KerberosCallbackHandler(user, password));
                log.info("Trying login for {} at {} using cache ", user, domain);
                lc.login();
                log.info("SUCCESSFULL Login", null, null);
            }
        } catch (LoginException e) {
            log.warn("Error in kerberos authentication from cache", e);
        }

        if (lc == null || lc.getSubject() == null) {
            if (krbList == null || krbList.isEmpty())
                throw new InternalErrorException("Unknown kerberos realm " + domain);
            else {
            	boolean done = false;
                for (KerberosAgent krb: krbList)
                {
	                try {
	                    hostName = Config.getConfig().getHostName();
	                    KerberosPrincipalInfo p = krb.createServerPrincipal(hostName);
	                    if (p != null)
	                    {
		                    keytabFile = Config.getConfig().getHomeDir() + "/conf/" + domain + ".ktab";
		                    if (p.getKeytab() != null && p.getKeytab().length > 0) {
		                        File ktabFile = new File(keytabFile);
		                        FileOutputStream out = new FileOutputStream(ktabFile);
		                        out.write(p.getKeytab());
		                        out.close();
		                        principal = p.getPrincipalName();
		                        user = p.getPrincipalName();
		                    } else {
		                        principal = p.getPrincipalName();
		                        user = p.getUserName() + "@" + domain;
		                        new File(keytabFile).delete();
		                    }
		                    password = p.getPassword();
		                    lc = new LoginContext(domain, new KerberosCallbackHandler(user, password));
		                    lc.login();
		                    done = true;
		                    break;
	                    }
	                } catch (Exception e) {
	                    log.warn("Unable to create kerberos principal for domain " + domain, e);
	                }
                }
                if (! done)
                    throw new InternalErrorException(
                            "Unable to create kerberos principal for domain " + domain);
            }
        }

        kc.subject = lc.getSubject();
        kc.lastSet = new Date();
        kc.principal = principal;
        kc.user = user;
        properties.setProperty(domain + ".principal", principal);
        properties.setProperty(domain + ".user", user);
        properties.setProperty(domain + ".password", password.toString());
        storeProperties();

    }
}

class KerberosCache {
    Subject subject;
    Date lastSet;
    String user;
    String principal;
}

class KerberosLoginConfiguration extends Configuration {
	String domain;
    public KerberosLoginConfiguration(String domain) {
    	this.domain = domain;
	}

	public AppConfigurationEntry[] getAppConfigurationEntry(String codi) 
	{
		if (! codi.equals (domain))
			return null;
		
        AppConfigurationEntry app[] = new AppConfigurationEntry[1];
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("storeKey", "true");
        File ktabFile;
        try {
            ktabFile = new File(Config.getConfig().getHomeDir() + "/conf/" + codi + ".ktab");
            if (false && ktabFile.canRead()) {
                params.put("useKeyTab", "true");
                params.put("keyTab", ktabFile.getAbsolutePath());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        params.put("debug", "true");
        app[0] = new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, params);
        return app;
    }

    public void refresh() {
    }

}

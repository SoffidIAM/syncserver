package com.soffid.iam.sync.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.eclipse.jetty.security.UserPrincipal;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.context.exe.ContextInstance;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.AuthorizationRole;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.ServerRegistrationToken;
import com.soffid.iam.api.System;
import com.soffid.iam.api.Tenant;
import com.soffid.iam.api.TenantCriteria;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.config.Config;
import com.soffid.iam.service.TenantService;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.service.CertificateEnrollServiceBase;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.InvalidPasswordException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class CertificateEnrollServiceImpl extends CertificateEnrollServiceBase {
    static JbpmConfiguration config = null;
    static Map<Long,Object> preapprovedRequests = new HashMap<Long,Object>();
    private CertificateServer certificateServer;

    private static JbpmConfiguration getConfig () {
        if (config == null) {
            config = JbpmConfiguration.getInstance("com/soffid/iam/sync/jbpm/jbpm.cfg.xml");
        }
        return config;
    }
    @Override
    protected long handleCreateRequest(String tenant, String user, String password, String domain,
            String hostName, PublicKey key) throws Exception {
    	Security.nestedLogin(tenant, user, Security.ALL_PERMISSIONS);
    	try
    	{
    		
    		String port = "760";
    		String subject = hostName;
    		int i = hostName.indexOf(":");
    		if (i >= 0)
    		{
    			port = hostName.substring(i+1);
    			subject = hostName.substring(0, i);
    		}
	        PasswordValidation vs;
	        vs = validatePassword(user, password, domain);
	        if (vs == PasswordValidation.PASSWORD_GOOD) {
	        	if (isAllowedAutoRegister (user, domain))
	        	{
	        		return autoCreateRequest (tenant, subject, key);
	        	}
	        	else
	        	{
	        		JbpmConfiguration config = getConfig();
	        		JbpmContext ctx = config.createJbpmContext();
	        		ProcessDefinition def;
        			def = ctx.getGraphSession()
        					.findLatestProcessDefinition("Soffid agent enrollment");
        			if (def == null) {
		        		Security.nestedLogin(Security.getMasterTenantName(), user, Security.ALL_PERMISSIONS);
		        		try {
		        			def = ctx.getGraphSession()
		        					.findLatestProcessDefinition("Soffid agent enrollment");
		        		} finally {
		        			Security.nestedLogoff();
		        		}
        			}
	        		ProcessInstance pi = new ProcessInstance(def);
	        		ContextInstance ctxInstance = pi.getContextInstance();
	        		try {
	        			User userData = getServerService().getUserInfo(user, domain);
	        			ctxInstance.setVariable("tenant", tenant);
	        			ctxInstance.setVariable("user", userData.getUserName());
	        			ctxInstance.setVariable("name", userData.getFirstName());
	        			ctxInstance.setVariable("surname", userData.getLastName());
	        		} catch (UnknownUserException e) {
	        			ctxInstance.setVariable("tenant", tenant);
	        			ctxInstance.setVariable("user", user);
	        			ctxInstance.setVariable("name", user);
	        			ctxInstance.setVariable("surname", "");
	        		}
	        		ctxInstance.setVariable("hostname", subject);
	        		ctxInstance.setVariable("publicKey", key);
	        		Invoker invoker = Invoker.getInvoker();
	        		ctxInstance.setVariable("remoteAddress", invoker.getAddr().getHostAddress());
	        		ctx.save(pi);
	        		pi.signal();
	        		ctx.save(pi);
	        		ctx.close();
	        		return pi.getId();
	        	}
	        } else {
	            throw new InvalidPasswordException();
	        }
    	} finally {
    		Security.nestedLogoff();
    	}
    }
    
    private Long autoCreateRequest(String tenant, String hostName, Object key) {
    	Long l = new Random().nextLong();
    	preapprovedRequests.put(l, key);
    	return l;
	}
    
	private boolean isAllowedAutoRegister(String user, String domain) throws InternalErrorException {
		if (user.isEmpty())
			return true;
    	boolean allow = "direct".equals( ConfigurationCache.getProperty("soffid.server.register") );
    	if (!allow)
    		return false;
    	System soffidDispatcher = ServiceLocator.instance().getDispatcherService().findSoffidDispatcher();
    	Account account = ServiceLocator.instance().getAccountService().findAccount(user, soffidDispatcher.getName());
    	if (account == null)
    		return false;
    	if (account instanceof UserAccount)
    	{
    		String userName = ((UserAccount) account).getUser();
    		Collection<AuthorizationRole> auths = ServiceLocator.instance().getAuthorizationService().getUserAuthorization(Security.AUTO_AUTHORIZATION_ALL, user);
    		if (!auths.isEmpty())
    			return true;
    		auths = ServiceLocator.instance().getAuthorizationService().getUserAuthorization(Security.AUTO_SERVER_MANAGE_SERVER, user);
    		if (!auths.isEmpty())
    			return true;
    	}
    	return false;
	}
    
	private PasswordValidation validatePassword(String user, String password, String domain)
            throws FileNotFoundException, IOException, RemoteException, InternalErrorException {
        PasswordValidation vs;
        Config seyconConfig = Config.getConfig();
        LogonService logonService = getLogonService();
        if (user.isEmpty()) {
        	ServerRegistrationToken t = getDispatcherService().consumeRegistrationToken(password);
        	return PasswordValidation.PASSWORD_GOOD;
        }
        else if (user.equals (seyconConfig.getDbUser()) && password.equals(seyconConfig.getPassword().getPassword()) && domain == null) {
            vs = PasswordValidation.PASSWORD_GOOD;
        } else {
            vs = logonService.validatePassword(user, domain, password);
        }
        return vs;
    }

    @Override
    protected X509Certificate handleGetCertificate(String tenant, String user, String password, String domain,
            String hostName, Long request) throws Exception {
    	return handleGetCertificate(tenant, user, password, domain, hostName, request, false);
    }
    
    @Override
    protected X509Certificate handleGetCertificate(String tenant, String user, String password, String domain,
            String hostName, Long request, boolean remote) throws Exception {

    	Security.nestedLogin(tenant, user, Security.ALL_PERMISSIONS);
    	try 
    	{
    		String port = "760";
    		String subject = hostName;
    		int i = hostName.indexOf(":");
    		if (i >= 0)
    		{
    			port = hostName.substring(i+1);
    			subject = hostName.substring(0, i);
    		}

	        PasswordValidation vs = validatePassword(user, password, domain);
	        if (vs == PasswordValidation.PASSWORD_GOOD) {
	        	PublicKey pk;
	        	X509Certificate cert;
	        	if (isAllowedAutoRegister(user, domain) && preapprovedRequests.containsKey(request))
	        	{
	        		Object o = preapprovedRequests.get(request);
	        		if (o instanceof X509Certificate)
	        			cert = (X509Certificate) o;
	        		else {
		        		pk = (PublicKey) o; 
		        		if (certificateServer == null)
		        			certificateServer = new CertificateServer();
		        		cert = certificateServer.createCertificate(tenant, subject, pk);
	        		}
	        		preapprovedRequests.remove(request);
	        	}
	        	else
	        	{
	        		cert = generateCertificateFromProcess(request, tenant, user, domain, subject);
	        	}
	        	Server server = null;
	        	for (Server s: getDispatcherService().findAllServers())
	        	{
	        		if (s.getName().equals(subject))
	        		{
	        			getDispatcherService().delete(server);
	        		}
	        	}
	        	if (server == null)
	        	{
	        		server = registerNewServer(tenant, remote, subject, port);
	        	}
	        	getDispatcherService().addCertificate(server, cert);
	        	return cert;
	        } else {
	            throw new InvalidPasswordException();
	        }
    	} finally {
    		Security.nestedLogoff();
    	}
    }
	public X509Certificate generateCertificateFromProcess(Long request, String tenant, String user, String domain,
			String subject) throws InternalErrorException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied,
			KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException,
			CertificateEncodingException, InvalidKeyException, NoSuchProviderException, SignatureException,
			UnrecoverableKeyException {
		PublicKey pk;
		X509Certificate cert;
		JbpmConfiguration config = getConfig();
		JbpmContext ctx = config.createJbpmContext();
		ProcessInstance pi = ctx.getProcessInstance(request.longValue());
		if (pi == null) 
			throw new InternalErrorException("Wrong request ID");
		ContextInstance ctxInstance = pi.getContextInstance();
		User userData;
		try {
			userData = getServerService().getUserInfo(user, domain);
		} catch (UnknownUserException e) {
			userData = new User();
			userData.setUserName(user);
		}
		Invoker invoker = Invoker.getInvoker();
		
		if (! ctxInstance.getVariable("user").equals(userData.getUserName()))
			throw new InternalErrorException (String.format("Certificate must be retrieved by %s",ctxInstance.getVariable("user")));
		if (! ctxInstance.getVariable("hostname").equals(subject))
			throw new InternalErrorException (String.format("Certificate belongs to host %s",ctxInstance.getVariable("hostname")));
		if (! ctxInstance.getVariable("remoteAddress").equals(invoker.getAddr().getHostAddress()))
			throw new InternalErrorException (String.format("Certificate not accesible from %s",invoker.getAddr().getHostAddress()));
		if (pi.getEnd() != null)
			throw new InternalErrorException("This certificate has already been issued");
		String aproved = (String) ctxInstance.getVariable ("approve");
		if (aproved == null)
			throw new CertificateEnrollWaitingForAproval();
		if (! aproved.equals("yes")) {
			pi.signal();
			ctx.save(pi);
			ctx.close();
			throw new CertificateEnrollDenied();
		}
		cert = (X509Certificate) ctxInstance.getVariable("certificate");
		if (cert == null) {
			pk = (PublicKey) ctxInstance.getVariable("publicKey");
			if (certificateServer == null)
				certificateServer = new CertificateServer();
			cert = certificateServer.createCertificate(tenant, subject, pk);
		}
		pi.signal();
		ctx.save(pi);
		ctx.close();
		return cert;
	}
	public Server registerNewServer(String tenant, boolean remote, String subject, String port)
			throws InternalErrorException, MalformedURLException {
		Server server;
		server = new Server();
		server.setName(subject);
		server.setBackupDatabase(null);
		if (remote)
		{
			server.setType(ServerType.REMOTESERVER);
			String gateway = getDispatcherService().createRemoteServer(subject, tenant);
			for ( Server gatewayServer: getDispatcherService().findAllServers())
			{
				if ( gatewayServer.getUrl() != null && gateway.endsWith(gatewayServer.getName()))
				{
					port = Integer.toString( new URL(gatewayServer.getUrl()).getPort() );
				}
			}
			server.setUrl("https://"+gateway+":"+port+"/");
		}
		else
		{
			server.setType(ServerType.PROXYSERVER);
			server.setUrl("https://"+subject+":"+port+"/");
		}
		server.setUseMasterDatabase(Boolean.FALSE);
		server = getDispatcherService().create(server);
		TenantService tenantSvc = ServiceLocator.instance().getTenantService();
		TenantCriteria criteria = new TenantCriteria();
		criteria.setName(tenant);
		for (Tenant t: tenantSvc.find(criteria ))
		{
			if (t.getName().equals(tenant))
			{
				tenantSvc.addTenantServer(t, server.getName());
			}
		}
		return server;
	}

    @Override
    protected String handleGetServerList() throws Exception {
        return getServerService().getConfig("seycon.server.list");
    }

    @Override
    protected int handleGetServerPort() throws Exception {
        String s = getServerService().getConfig("seycon.https.port");
        return Integer.parseInt(s);
    }
    @Override
    protected X509Certificate handleGetRootCertificate() throws Exception {
        if (certificateServer == null)
            certificateServer = new CertificateServer();
        return certificateServer.getRoot();
    }
	@Override
	protected long handleCreateRequest(String tenant, String user, String password, String domain, String hostName,
			X509Certificate cert) throws Exception {
    	Security.nestedLogin(tenant, user, Security.ALL_PERMISSIONS);
    	try
    	{
			String port = "760";
			String subject = hostName;
			int i = hostName.indexOf(":");
			if (i >= 0)
			{
				port = hostName.substring(i+1);
				subject = hostName.substring(0, i);
			}
	        PasswordValidation vs;
	        vs = validatePassword(user, password, domain);
	        if (vs == PasswordValidation.PASSWORD_GOOD) {
	        	checkRightTenant(cert, tenant);
	        	if (isAllowedAutoRegister (user, domain))
	        	{
	        		return autoCreateRequest (tenant, subject, cert);
	        	}
	        	else
	        	{
	        		JbpmConfiguration config = getConfig();
	        		JbpmContext ctx = config.createJbpmContext();
	        		ProcessDefinition def;
	    			def = ctx.getGraphSession()
	    					.findLatestProcessDefinition("Soffid agent enrollment");
	    			if (def == null) {
		        		Security.nestedLogin(Security.getMasterTenantName(), user, Security.ALL_PERMISSIONS);
		        		try {
		        			def = ctx.getGraphSession()
		        					.findLatestProcessDefinition("Soffid agent enrollment");
		        		} finally {
		        			Security.nestedLogoff();
		        		}
	    			}
	        		ProcessInstance pi = new ProcessInstance(def);
	        		ContextInstance ctxInstance = pi.getContextInstance();
	        		try {
	        			User userData = getServerService().getUserInfo(user, domain);
	        			ctxInstance.setVariable("tenant", tenant);
	        			ctxInstance.setVariable("user", userData.getUserName());
	        			ctxInstance.setVariable("name", userData.getFirstName());
	        			ctxInstance.setVariable("surname", userData.getLastName());
	        		} catch (UnknownUserException e) {
	        			ctxInstance.setVariable("tenant", tenant);
	        			ctxInstance.setVariable("user", user);
	        			ctxInstance.setVariable("name", user);
	        			ctxInstance.setVariable("surname", "");
	        		}
	        		ctxInstance.setVariable("hostname", subject);
	        		ctxInstance.setVariable("certificate", cert);
	        		Invoker invoker = Invoker.getInvoker();
	        		ctxInstance.setVariable("remoteAddress", invoker.getAddr().getHostAddress());
	        		ctx.save(pi);
	        		pi.signal();
	        		ctx.save(pi);
	        		ctx.close();
	        		return pi.getId();
	        	}
	        } else {
	            throw new InvalidPasswordException();
	        }
		} finally {
			Security.nestedLogoff();
		}
	}

	private void checkRightTenant(X509Certificate cert, String tenant) {
		X500Name name = new X500Name (cert.getSubjectX500Principal().getName());
		String domain = null;
		for ( RDN rdn: name.getRDNs())
		{
			if (rdn.getFirst() != null &&
					rdn.getFirst().getType().equals( RFC4519Style.ou))
				domain = rdn.getFirst().getValue().toString();
		}
		if ( tenant.equals(Security.getMasterTenantName()) && domain == null || 
				tenant.equals(domain))
			return; // OK
		throw new SecurityException("Certificate OU must be "+tenant+" but it is ["+domain+"]");
	}
	
	@Override
	public List<X509Certificate> handleGetCertificates() throws InternalErrorException, InternalErrorException {
		return getDispatcherService().findValidCertificates();
	}
    
}

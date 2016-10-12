package com.soffid.iam.sync.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.context.exe.ContextInstance;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;

import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.User;
import com.soffid.iam.config.Config;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.service.CertificateEnrollServiceBase;
import com.soffid.iam.sync.service.LogonService;
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
    	Security.nestedLogin(tenant, user, new String [] {
    			Security.AUTO_AUTHORIZATION_ALL
    	});
    	try
    	{
	        PasswordValidation vs;
	        vs = validatePassword(user, password, domain);
	        if (vs == PasswordValidation.PASSWORD_GOOD) {
	            JbpmConfiguration config = getConfig();
	            JbpmContext ctx = config.createJbpmContext();
	            ProcessDefinition def;
	            Security.nestedLogin(Security.getMasterTenantName(), user, Security.ALL_PERMISSIONS);
	            try {
	            	def = ctx.getGraphSession()
	            			.findLatestProcessDefinition("Soffid agent enrollment");
	            } finally {
	            	Security.nestedLogoff();
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
	            ctxInstance.setVariable("hostname", hostName);
	            ctxInstance.setVariable("publicKey", key);
	            Invoker invoker = Invoker.getInvoker();
	            ctxInstance.setVariable("remoteAddress", invoker.getAddr().getHostAddress());
	            ctx.save(pi);
	            pi.signal();
	            ctx.save(pi);
	            ctx.close();
	            return pi.getId();
	        } else {
	            throw new InvalidPasswordException();
	        }
    	} finally {
    		Security.nestedLogoff();
    	}
    }
    private PasswordValidation validatePassword(String user, String password, String domain)
            throws FileNotFoundException, IOException, RemoteException, InternalErrorException {
        PasswordValidation vs;
        Config seyconConfig = Config.getConfig();
        LogonService logonService = getLogonService();
        if (user.equals (seyconConfig.getDbUser()) && password.equals(seyconConfig.getPassword().getPassword()) && domain == null) {
            vs = PasswordValidation.PASSWORD_GOOD;
        } else {
            vs = logonService.validatePassword(user, domain, password);
        }
        return vs;
    }

    @Override
    protected X509Certificate handleGetCertificate(String tenant, String user, String password, String domain,
            String hostName, Long request) throws Exception {
    	Security.nestedLogin(tenant, user, new String [] {
    			Security.AUTO_AUTHORIZATION_ALL
    	});
    	try 
    	{
	        LogonService logonService = getLogonService();
	        PasswordValidation vs = validatePassword(user, password, domain);
	        if (vs == PasswordValidation.PASSWORD_GOOD) {
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
	            if (! ctxInstance.getVariable("hostname").equals(hostName))
	                throw new InternalErrorException (String.format("Certificate belongs to host %s",ctxInstance.getVariable("hostname")));
	            if (! ctxInstance.getVariable("remoteAddress").equals(invoker.getAddr().getHostAddress()))
	                throw new InternalErrorException (String.format("Certificate not accesible from %s",ctxInstance.getVariable(invoker.getAddr().getHostAddress())));
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
	            } else {
	                PublicKey pk = (PublicKey) ctxInstance.getVariable("publicKey");
	                if (certificateServer == null)
	                    certificateServer = new CertificateServer();
	                X509Certificate cert = certificateServer.createCertificate(tenant, hostName, pk);
	                pi.signal();
	                ctx.save(pi);
	                ctx.close();
	                boolean found = false;
	                for (Server server: getDispatcherService().findAllServers())
	                {
	                	if (server.getName().equals(hostName))
	                	{
	                        found = true;
	                	}
	                }
	                if (! found)
	                {
	                    Server server = new Server();
	                    server.setName(hostName);
	                    server.setBackupDatabase(null);
	                    server.setType(ServerType.PROXYSERVER);
	                    server.setUrl("https://"+hostName+":"+Config.getConfig().getPort()+"/");
	                    server.setUseMasterDatabase(Boolean.FALSE);
	                    getDispatcherService().create(server);
	                }
	                return cert;
	            }
	        } else {
	            throw new InvalidPasswordException();
	        }
    	} finally {
    		Security.nestedLogoff();
    	}
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

    
}

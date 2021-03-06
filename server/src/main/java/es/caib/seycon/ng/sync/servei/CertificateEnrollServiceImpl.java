package es.caib.seycon.ng.sync.servei;

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

import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.comu.Server;
import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.InvalidPasswordException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.engine.cert.CertificateServer;
import es.caib.seycon.ng.sync.jetty.Invoker;

public class CertificateEnrollServiceImpl extends CertificateEnrollServiceBase {
    static JbpmConfiguration config = null;
    private CertificateServer certificateServer;

    private static JbpmConfiguration getConfig () {
        if (config == null) {
            config = JbpmConfiguration.getInstance("es/caib/seycon/ng/sync/jbpm/jbpm.cfg.xml");
        }
        return config;
    }
    @Override
    protected long handleCreateRequest(String user, String password, String domain,
            String hostName, PublicKey key) throws Exception {
        PasswordValidation vs;
        vs = validatePassword(user, password, domain);
        if (vs == PasswordValidation.PASSWORD_GOOD) {
            JbpmConfiguration config = getConfig();
            JbpmContext ctx = config.createJbpmContext();
            ProcessDefinition def = ctx.getGraphSession()
				.findLatestProcessDefinition("Soffid agent enrollment");
            if (def == null)
            	def = ctx.getGraphSession()
            		.findLatestProcessDefinition("Conexió agent SEYCON");
            ProcessInstance pi = new ProcessInstance(def);
            ContextInstance ctxInstance = pi.getContextInstance();
            try {
                Usuari userData = getServerService().getUserInfo(user, domain);
                ctxInstance.setVariable("user", userData.getCodi());
                ctxInstance.setVariable("name", userData.getNom());
                ctxInstance.setVariable("surname", userData.getPrimerLlinatge()+" "+userData.getSegonLlinatge());
            } catch (UnknownUserException e) {
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
    protected X509Certificate handleGetCertificate(String user, String password, String domain,
            String hostName, Long request) throws Exception {
        LogonService logonService = getLogonService();
        PasswordValidation vs = validatePassword(user, password, domain);
        if (vs == PasswordValidation.PASSWORD_GOOD) {
            JbpmConfiguration config = getConfig();
            JbpmContext ctx = config.createJbpmContext();
            ProcessInstance pi = ctx.getProcessInstance(request.longValue());
            if (pi == null) 
                throw new InternalErrorException("Wrong request ID");
            ContextInstance ctxInstance = pi.getContextInstance();
            Usuari userData;
            try {
                 userData = getServerService().getUserInfo(user, domain);
            } catch (UnknownUserException e) {
                userData = new Usuari();
                userData.setCodi(user);
            }
            Invoker invoker = Invoker.getInvoker();

            if (! ctxInstance.getVariable("user").equals(userData.getCodi()))
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
                X509Certificate cert = certificateServer.createCertificate(hostName, pk);
                pi.signal();
                ctx.save(pi);
                ctx.close();
                boolean found = false;
                for (Server server: getDispatcherService().findAllServers())
                {
                	if (server.getNom().equals(hostName))
                	{
                        found = true;
                	}
                }
                if (! found)
                {
                    Server server = new Server();
                    server.setNom(hostName);
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

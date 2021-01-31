package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.LogFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Challenge;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.System;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.challenge.ChallengeStore;
import com.soffid.iam.sync.engine.kerberos.KerberosManager;
import com.soffid.iam.sync.intf.SecretStoreAgent;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.LogonDeniedException;
import es.caib.seycon.util.Base64;

public class KerberosLoginServlet extends HttpServlet {
    private LogonService logonService;
	private SecretStoreService secretStoreService;

    public KerberosLoginServlet() {
        logonService = ServerServiceLocator.instance().getLogonService();
        secretStoreService = ServerServiceLocator.instance().getSecretStoreService();
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    org.apache.commons.logging.Log log = LogFactory.getLog(getClass());

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        String action = req.getParameter("action");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        try {
            if ("start".equals(action))
                writer.write(doStartAction(req, resp));
            else if ("continue".equals(action))
                writer.write(doContinueAction(req, resp));
            else if ("pbticket".equals(action))
                writer.write(doPBAction(req, resp));
            else if ("getSecrets".equals(action))
                writer.write(doSecretsAction(req, resp));
            else if ("createSession".equals(action))
                writer.write(doCreateSessionAction(req, resp));
            else
                throw new Exception("Invalid action " + action);
        } catch (Exception e) {
            log.warn("Error performing kerberos login", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        }
        writer.close();
    }

    private String doCreateSessionAction(HttpServletRequest req, HttpServletResponse resp)
            throws InternalErrorException {
        Challenge challenge = getChallenge(req);

        try {
            String value = req.getParameter("cardValue");
            String port = req.getParameter("port");
            challenge.setCloseOldSessions("true".equals(req.getParameter("force")));
            challenge.setSilent("true".equals(req.getParameter("silent")));
            challenge.setValue(value);
            challenge.setCentinelPort(Integer.decode(port));
            Session result = logonService.responseChallenge(challenge);

            return "OK|" + challenge.getChallengeId() + "|" + Long.toString(result.getId()) + "|";
        } catch (Exception e) {
            log.warn("Error creating session", e);
            return e.getClass().getName() + "|" + e.getMessage() + "\n";
        }
    }

    private String doPBAction(HttpServletRequest req, HttpServletResponse resp)
            throws InternalErrorException {
        String challengeId = req.getParameter("challengeId");

        final Challenge challenge = challengeStore.getChallenge(challengeId);
        if (challenge == null)
            return "ERROR|Ticket unknown " + challengeId;
        else {
            String ticket = null;
            TaskGenerator tg = ServerServiceLocator.instance().getTaskGenerator();
            ServerService ss = ServerServiceLocator.instance().getServerService();
            for (DispatcherHandler d : tg.getDispatchers()) {
                if (d != null) {
                    try {
                        Object agent = d.getRemoteAgent();
                        if (agent != null && agent instanceof SecretStoreAgent) {
                            SecretStoreAgent generator = (SecretStoreAgent) agent;
                            ticket = generator.generateUserTicket(challenge.getUserKey());
                        }
                    } catch (Exception e) {
                        log.warn("Error getting secrets for " + d.getSystem().getName(), e);
                    }
                }
            }
            challengeStore.removeChallenge(challenge);
            return "OK|" + ticket;
        }
    }

    private String doSecretsAction(HttpServletRequest req, HttpServletResponse resp)
            throws InternalErrorException, UnsupportedEncodingException {
    	boolean encode = "true".equals( req.getParameter("encode") );
        final Challenge challenge = getChallenge(req);
        if (challenge == null)
            return "ERROR|Unknown ticket";
        else {
            StringBuffer result = new StringBuffer("OK");
            
            for (Secret secret: secretStoreService.getAllSecrets(challenge.getUser())) {
            	if (secret.getName() != null && secret.getName().length() > 0 &&
            			secret.getValue() != null &&
            			secret.getValue().getPassword() != null &&
            			secret.getValue().getPassword().length() > 0 )
            	{
	                result.append('|');
	                if (encode)
	                	result.append( encodeSecret(secret.getName()));
	                else
	                	result.append(secret.getName());
	                result.append('|');
	                if (encode)
		                result.append( encodeSecret(secret.getValue().getPassword()));
	                else
	                	result.append(secret.getValue().getPassword());
            	}
            }
            result.append ("|sessionKey|").append(challenge.getChallengeId());
            if (encode)
            	result.append ("|fullName|").append(encodeSecret(challenge.getUser().getFullName()));
            else
            	result.append ("|fullName|").append(challenge.getUser().getFullName());
            challengeStore.removeChallenge(challenge);
            return result.toString();
        }
    }

	private String encodeSecret(String secret)
			throws UnsupportedEncodingException {
		return URLEncoder.encode(secret,"UTF-8").replaceAll("\\|", "%7c"); 
	}

    private static ChallengeStore challengeStore = ChallengeStore.getInstance();

    private String doStartAction(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String principal = req.getParameter("principal");
        String clientIP = req.getParameter("clientIP");
        String cardSupport = req.getParameter("cardSupport");
        String token = req.getParameter("krbToken");
        String hostIP = Security.getClientIp();
        String hostSerial=req.getParameter("serial");
        final KerberosManager km = new KerberosManager();

        int split = principal.indexOf('@');
        if (split < 0)
            throw new LogonDeniedException("Bad principal name " + principal);
        String user = principal.substring(0, split);
        String domain = principal.substring(split + 1).toUpperCase();

        LogonService logonService = ServerServiceLocator.instance().getLogonService();

        final System dispatcher = km.getSystemForRealm(domain); 
        if (dispatcher == null)
        {
        	log.warn("Cannot guess agent for principal "+principal+" (domain "+domain+")");
        }
        
        final Challenge challenge = 
        		logonService.requestChallenge(Challenge.TYPE_KERBEROS, 
        				dispatcher == null ? principal: user,
        				dispatcher==null ? null: dispatcher.getName(), 
        				hostSerial == null ? hostIP: hostSerial, clientIP,
        				Integer.decode(cardSupport));

        challenge.setKerberosDomain(domain);

        // Check some credentials are stored
        if ( secretStoreService.getAllSecrets(challenge.getUser()).isEmpty()) {
        	throw new LogonDeniedException("No secrets available for "+user+" yet");
        }


        Subject serverSubject = km.getServerSubject(dispatcher);

        // Crear el context de servidor
        Object result = Subject.doAs(serverSubject, new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    GSSManager manager = GSSManager.getInstance();
                    GSSName serverName = manager.createName(
                            km.getServerPrincipal(dispatcher), null);
                    Oid desiredMechs = new Oid("1.2.840.113554.1.2.2"); // Kerberos
                                                                        // V5
                    // Oid desiredMechs = new Oid("1.3.6.1.5.5.2"); // SPNEGO
                    GSSCredential serverCreds = manager.createCredential(serverName,
                            GSSCredential.INDEFINITE_LIFETIME, desiredMechs,
                            GSSCredential.ACCEPT_ONLY);
                    GSSContext secContext = manager.createContext(serverCreds);
                    secContext.requestMutualAuth(true);
                    return secContext;
                } catch (Exception e) {
                    return e;
                }
            }
        });

        if (result instanceof Exception)
            throw (Exception) result;
        else
            challenge.setKerberosContext((GSSContext) result);

        return tryLogin(challenge, token);

    }

    private String doContinueAction(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        final Challenge challenge = getChallenge(req);
        String token = req.getParameter("krbToken");

        return tryLogin(challenge, token);

    }

    private Challenge getChallenge(HttpServletRequest req) throws InternalErrorException {
        String challengeId = req.getParameter("challengeId");
        final Challenge challenge = challengeStore.getChallenge(challengeId);

        if (challenge == null)
            throw new InternalErrorException("Invalid token " + challengeId);
        if ( !challenge.getHost().getIp().equals(Security.getClientIp())) 
        {
            log.warn("Ticket spoofing detected from "+Security.getClientIp()+". Expected "+challenge.getHost().getIp());
            throw new InternalErrorException("Invalid token " + challengeId);
        }
        return challenge;
    }

    private String tryLogin(final Challenge challenge, final String token) throws Exception {
        // Ahora intentar hacer login kerberos
        final KerberosManager km = new KerberosManager();
        System system = km.getSystemForRealm(challenge.getKerberosDomain());
        Subject serverSubject = km.getServerSubject(system);
        Object result = Subject.doAs(serverSubject, new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    byte inToken[] = Base64.decode(token);
                    byte outToken[] = challenge.getKerberosContext().acceptSecContext(inToken, 0,
                            inToken.length);
                    GSSContext ctx = challenge.getKerberosContext();

                    String resultToken = "";
                    if (outToken != null) {
                        resultToken = Base64.encodeBytes(outToken, Base64.DONT_BREAK_LINES);
                    }
                    if (ctx.isEstablished()) {
                        log.info("Login from "+ctx.getSrcName()+" to "+ 
                        		ctx.getTargName().toString()+" ip "+challenge.getHost().getIp());
                        return "OK|" + challenge.getChallengeId() + "|" + resultToken + "|"
                                + challenge.getCardNumber() + "|" + challenge.getCell()+ "|" + challenge.getUser().getUserName();
                    } else {
                        return "MoreDataReq|" + challenge.getChallengeId() + "|" + resultToken;
                    }
                } catch (Exception e) {
                    return e;
                }
            }
        });

        if (result instanceof Exception)
        	
            throw (Exception) result;
        else {
            return (String) result;
        }
    }
}

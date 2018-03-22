package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ietf.jgss.GSSContext;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Challenge;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.System;
import com.soffid.iam.api.User;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.challenge.ChallengeStore;
import com.soffid.iam.sync.engine.kerberos.KerberosManager;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.InvalidPasswordException;
import es.caib.seycon.util.Base64;

public class PasswordLoginServlet extends HttpServlet {

    private ServerService serverService;
    private LogonService logonService;
    private SessionService sessioService;
    private SecretStoreService secretStoreService;

    public PasswordLoginServlet() {
        serverService = ServerServiceLocator.instance().getServerService();
        logonService = ServerServiceLocator.instance().getLogonService();
        sessioService = ServerServiceLocator.instance().getSessionService();
        secretStoreService = ServerServiceLocator.instance().getSecretStoreService();
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("PasswordLoginServlet");

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        String action = req.getParameter("action");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        try {
            if ("prestart".equals(action))
                writer.write(doPreStartAction(req, resp));
            else if ("start".equals(action))
                writer.write(doStartAction(req, resp));
            else if ("changePass".equals(action))
                writer.write(doChangePassAction(req, resp));
            else if ("getSecrets".equals(action))
                writer.write(doSecretsAction(req, resp));
            else if ("createSession".equals(action))
                writer.write(doCreateSessionAction(req, resp));
            else
                throw new Exception("Invalid action " + action);
        } catch (Exception e) {
            log.warn("Error performing password login", e);
            StringBuffer b = new StringBuffer().append (e.getClass().getName()).
            				append ("|").
            				append (e.getMessage()).
            				append ("\n");
            writer.write(b.toString());
        }
        writer.close();

    }

    private String doChangePassAction(HttpServletRequest req, HttpServletResponse resp) throws InternalErrorException {
        String user = req.getParameter("user");
        String domain = req.getParameter("domain");
        String pass1 = req.getParameter("password1");
        String pass2 = req.getParameter("password2");

    	if (domain.isEmpty() )
    		domain = null;
    	else if (domain != null)
        {
        	System dispatcher = serverService.getDispatcherInfo(domain);
        	if (!serverService.getDefaultDispatcher().equals (domain) &&
        		! dispatcher.getTrusted().booleanValue() )
        	{
        		return "ERROR|" + String.format("'%s' is not a trusted Soffid Agent", domain);
        	}
        }

        try {
            logonService.changePassword(user, domain, pass1, pass2);
        } catch (RemoteException e) {
            return "ERROR|" + e.toString();
        } catch (InternalErrorException e) {
            return "ERROR|" + e.toString();
        } catch (BadPasswordException e) {
            return "ERROR2|" + e.getMessage();
        } catch (InvalidPasswordException e) {
            return "ERROR1|" + e.toString();
        }
        return "OK";

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

            Session s = logonService.responseChallenge(challenge);

            boolean canAdmin;

            Host maquinaAcces = serverService.getHostInfoByIP(req.getRemoteAddr());
            canAdmin = serverService.hasSupportAccessHost(maquinaAcces.getId(), challenge.getUser().getId());

            return "OK|" + challenge.getChallengeId() + "|" + Long.toString(s.getId())
                    + "|" + canAdmin;
        } catch (Exception e) {
            return e.getClass().getName() + "|" + e.getMessage() + "\n";
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
        String s = doPreStartAction(req, resp);
    	if (s.equals("OK")) {
            String clientIP = req.getParameter("clientIP");
            String domain = req.getParameter("domain");
            if (domain.isEmpty())
            	domain = null;
            String user = req.getParameter("user");

            String cardSupport = req.getParameter("cardSupport");
            String hostIP = req.getRemoteAddr();
            int iCardSupport = Challenge.CARD_IFNEEDED;
            try {
                iCardSupport = Integer.decode(cardSupport);
            } catch (Exception e) {
            }

            final Challenge challenge = logonService.requestChallenge(Challenge.TYPE_PASSWORD, user, domain, hostIP, clientIP,
                    iCardSupport);

            return "OK|" + challenge.getChallengeId() + "|" + challenge.getCardNumber() + "|"
                    + challenge.getCell()+"|"+challenge.getUser().getUserName();

        } else
            return s;
    }

    private String doPreStartAction(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        String user = req.getParameter("user");
        String domain = req.getParameter("domain");
        String pass = req.getParameter("password");
    	if (domain.isEmpty() )
    		domain = null;
    	else if (domain != null)
        {
        	System dispatcher = serverService.getDispatcherInfo(domain);
        	if (!serverService.getDefaultDispatcher().equals (domain) &&
        		! dispatcher.getTrusted().booleanValue() )
        	{
        		return "ERROR|" + String.format("'%s' is not a trusted Soffid Agent", domain);
        	}
        }
        User usuari = serverService.getUserInfo(user, domain);

        PasswordValidation result = logonService.validatePassword(user, domain, pass);
        if (result == PasswordValidation.PASSWORD_GOOD) {
        	log.info("Prestart action GOOD {} {}", user, domain);
            if (! usuari.getActive().booleanValue()) {
                log.info("login {} is disabled: not authorized", user, null);
                return "ERROR";
            }
        } else if (result == PasswordValidation.PASSWORD_GOOD_EXPIRED) {
            log.debug("login {}: password expired", user, null);
            return "EXPIRED";
        } else {
            log.debug("login {}: not valid", user, null);
            return "ERROR";
        }

        return "OK";

    }

    private Challenge getChallenge(HttpServletRequest req) throws InternalErrorException {
        String challengeId = req.getParameter("challengeId");
        final Challenge challenge = challengeStore.getChallenge(challengeId);

        if (challenge == null)
            throw new InternalErrorException("Invalid token " + challengeId);
        if (!challenge.getHost().getIp().equals(req.getRemoteHost())) {
            log.warn("Ticket spoofing detected from {}", req.getRemoteHost(), null);
            throw new InternalErrorException("Invalid token " + challengeId);
        }
        return challenge;
    }

    private String tryLogin(final Challenge challenge, final String token) throws Exception {
        // Ahora intentar hacer login kerberos
        final KerberosManager km = new KerberosManager();
        log.info("Kerberos accept challenge {}\n{}", challenge.getChallengeId(), token);
        Subject serverSubject = km.getServerSubject(challenge.getKerberosDomain());
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
                        log.info("Login desde {} hacia {}", ctx.getSrcName().toString(), ctx
                                .getTargName().toString());
                        return "OK|" + challenge.getChallengeId() + "|" + resultToken + "|"
                                + challenge.getCardNumber() + "|" + challenge.getCell();
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
            log.info("Result is {}", result, null);
            return (String) result;
        }
    }
}

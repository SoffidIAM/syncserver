package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.List;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.Challenge;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.sso.Secret;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.LogonDeniedException;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.challenge.ChallengeStore;
import es.caib.seycon.ng.sync.engine.kerberos.KerberosManager;
import es.caib.seycon.ng.sync.intf.SecretStoreAgent;
import es.caib.seycon.ng.sync.jetty.Invoker;
import es.caib.seycon.ng.sync.servei.LogonService;
import es.caib.seycon.ng.sync.servei.SecretStoreService;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.util.Base64;

public class KerberosLoginServlet extends HttpServlet {
    private LogonService logonService;

    public KerberosLoginServlet() {
        logonService = ServerServiceLocator.instance().getLogonService();
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("KerberosLoginServlet");

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
            Sessio result = logonService.responseChallenge(challenge);

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
//                            String key = ss.getUserKey(challenge.getUser().getId(), d
//                                    .getDispatcher().getDominiUsuaris());
//                            key.c
                            ticket = generator.generateUserTicket(challenge.getUserKey());
                        }
                    } catch (Exception e) {
                        log.warn("Error getting secrets for " + d.getDispatcher().getCodi(), e);
                    }
                }
            }
            challengeStore.removeChallenge(challenge);
            return "OK|" + ticket;
        }
    }

    private String doSecretsAction(HttpServletRequest req, HttpServletResponse resp)
            throws InternalErrorException {
        final Challenge challenge = getChallenge(req);
        if (challenge == null)
            return "ERROR|Unknown ticket";
        else {
            SecretStoreService ss = ServerServiceLocator.instance().getSecretStoreService();
            StringBuffer result = new StringBuffer("OK");
            List<Secret> secrets = ss.getAllSecrets(challenge.getUser());
            for (Iterator<Secret> it = secrets.iterator(); it.hasNext();) {
                Secret s = it.next();
                result.append('|');
                result.append(s.getName());
                result.append('|');
                result.append(s.getValue().getPassword());
            }
            result.append ("|sessionKey|").append(challenge.getChallengeId());
            result.append ("|fullName|").append(challenge.getUser().getFullName());
            challengeStore.removeChallenge(challenge);
            return result.toString();
        }
    }

    private static ChallengeStore challengeStore = ChallengeStore.getInstance();

    private String doStartAction(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String principal = req.getParameter("principal");
        String clientIP = req.getParameter("clientIP");
        String cardSupport = req.getParameter("cardSupport");
        String token = req.getParameter("krbToken");
        String hostIP = req.getRemoteAddr();
        final KerberosManager km = new KerberosManager();

        int split = principal.indexOf('@');
        if (split < 0)
            throw new LogonDeniedException("Bad principal name " + principal);
        String user = principal.substring(0, split);
        String domain = principal.substring(split + 1).toUpperCase();

        Dispatcher dispatcher = km.getDispatcherForRealm(domain); 
        LogonService logonService = ServerServiceLocator.instance().getLogonService();
        final Challenge challenge = logonService.requestChallenge(Challenge.TYPE_KERBEROS, 
        				user,
        				dispatcher==null ? null: dispatcher.getCodi(), 
        				hostIP, clientIP,
        				Integer.decode(cardSupport));

        challenge.setKerberosDomain(domain);

        Subject serverSubject = km.getServerSubject(challenge.getKerberosDomain());

        // Crear el context de servidor
        Object result = Subject.doAs(serverSubject, new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    GSSManager manager = GSSManager.getInstance();
                    GSSName serverName = manager.createName(
                            km.getServerPrincipal(challenge.getKerberosDomain()), null);
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
        if (!challenge.getHost().getNom().equals(req.getRemoteHost()) &&
                !challenge.getHost().getAdreca().equals(req.getRemoteAddr())) {
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
                                + challenge.getCardNumber() + "|" + challenge.getCell()+ "|" + challenge.getUser().getCodi();
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

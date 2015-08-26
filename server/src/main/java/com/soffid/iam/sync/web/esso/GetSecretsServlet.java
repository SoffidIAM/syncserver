package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class GetSecretsServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetSecretsServlet");

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {

        String user = req.getParameter("user");
        String key = req.getParameter("key");
        String key2 = req.getParameter("key2");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        SessionService ss = ServerServiceLocator.instance().getSessionService();
        UserService usuariService = ServerServiceLocator.instance().getUserService();
        try {
            User usuari = usuariService.findUserByUserName(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Session sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getKey().equals(key) && sessio.getTemporaryKey().equals(key2)) {
                    writer.write(doSecretsAction(usuari, key));
                    writer.close();
                    return;
                }
            }
            writer.write("ERROR|Invalid key");
            log.warn("Invalid key {} for user {}", key, user);
        } catch (Exception e) {
            log.warn("Error getting keys", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        }
        writer.close();

    }

    private String doSecretsAction(User user, String sessionKey) throws InternalErrorException {
        StringBuffer result = new StringBuffer("OK");
        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
        for (Secret secret : sss.getAllSecrets(user)) {
            result.append('|');
            result.append(secret.getName());
            result.append('|');
            result.append(secret.getValue().getPassword());

        }
        result.append ("|sessionKey|").append(sessionKey);
        result.append ("|fullName|").append(user.getFullName());
        
        return result.toString();
    }

}

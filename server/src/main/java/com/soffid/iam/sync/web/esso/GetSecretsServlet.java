package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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
    	boolean encode = "true".equals( req.getParameter("encode") );
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
                    writer.write(doSecretsAction(usuari, key, encode));
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

    private String doSecretsAction(User user, String sessionKey, boolean encode) throws InternalErrorException, UnsupportedEncodingException {
        StringBuffer result = new StringBuffer("OK");
        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
        for (Secret secret : sss.getAllSecrets(user)) {
        	if (secret.getName() != null && secret.getName().length() > 0 &&
        			secret.getValue() != null &&
        			secret.getValue().getPassword() != null &&
        			secret.getValue().getPassword().length() > 0 )
        	{
                result.append('|');
                if (encode)
                	result.append( URLEncoder.encode(secret.getName(),"UTF-8"));
                else
                	result.append(secret.getName());
                result.append('|');
                if (encode)
	                result.append( URLEncoder.encode(secret.getValue().getPassword(),"UTF-8"));
                else
                	result.append(secret.getValue().getPassword());
        	}
        }
        result.append ("|sessionKey|").append(sessionKey);
        if (encode)
        	result.append ("|fullName|").append(URLEncoder.encode(user.getFullName(),"UTF-8"));
        else
        	result.append ("|fullName|").append(user.getFullName());
        return result.toString();
    }

}

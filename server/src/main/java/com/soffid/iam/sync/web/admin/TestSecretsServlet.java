package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.User;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class TestSecretsServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetSecretsServlet");
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        String user = req.getParameter("user");
        
        SecretStoreService ss = ServerServiceLocator.instance().getSecretStoreService();
        ServerService server = ServerServiceLocator.instance().getServerService();
        List<Secret> secrets;
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        try {
            User usuari = server.getUserInfo(user, null);
            secrets = ss.getSecrets(usuari);
            resp.setContentType("text/plain");
            
            for (Iterator<Secret> it = secrets.iterator(); it.hasNext(); )
            {
                Secret s = it.next();
                writer.write(s.getName());
                writer.write("=[");
                writer.write(s.getValue().getPassword());
                writer.write("]\n");
            }
            writer.close ();
        } catch (InternalErrorException e) {
            log.warn("Error", e);
            writer.write("Error intern:");
            writer.write(e.toString());
        } catch (UnknownUserException e) {
            log.warn("Error", e);
            writer.write("Error intern:");
            writer.write(e.toString());
        }
        
    }

}

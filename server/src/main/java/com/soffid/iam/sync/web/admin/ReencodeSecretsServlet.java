package com.soffid.iam.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;

import es.caib.seycon.ng.exception.InternalErrorException;

public class ReencodeSecretsServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetSecretsServlet");
    static Thread thread = null;
    static int processed = 0;
    static int max = 0;

    public ReencodeSecretsServlet() {
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {

        String action = req.getParameter("action");
        resp.setContentType("text/html; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        try {
            if (thread != null) {
                if (thread.isAlive()) {
                    writer.write("<meta http-equiv='refresh' content='2'/>");
                    resp.addHeader("refresh", "2");
                    writer.write("Procés de replicació en curs.<br>");
                    writer.write("Processat: " + processed + " de " + max + "<br>");
                } else {
                    writer.write("Procés de replicació finalitzat.<br><br>");
                    writer.write("<a href='/main'>Tornar al menú</a>");
                    thread = null;
                }
            } else if (action != null && action.equals("YES")) {
                resp.addHeader("refresh", "2");
                replicate();
                writer.write("Iniciant procés de replicació...<br>");
            } else {
                writer.write("Ha de realitzar aquest procés quan s'instal·lin nous servidors a la xarxa<br>");
                writer.write("El procés pot durar bastant de temps<br><br>");
                writer.write("Vol realitzar la replicació? <br><br>");
                writer.write("<a href='/reencodesecrets?action=YES'>Sí</A> /");
                writer.write("<a href='/main'>No</A><br>");
            }
            writer.close();
        } catch (Exception e) {
            log.warn("Error processing servlet", e);
        }

    }

    private void replicate() throws InternalErrorException {
        final SecretStoreService secretStoreService = ServerServiceLocator.instance()
                .getSecretStoreService();
        final Collection<User> usuaris = secretStoreService.getUsersWithSecrets();
        final Collection<Account> accounts = secretStoreService.getAccountsWithPassword();
        max = usuaris.size() + accounts.size();

        processed = 0;

        Runnable r = new Runnable () {

            public void run() {
                for (User usuari : usuaris) {
                	log.info("Reencoding secrets for user {}", usuari.getUserName(), null);
                    try {
                        secretStoreService.reencode(usuari);
                    } catch (InternalErrorException e) {
                        log.warn("Error reencoding secrets", e);
                    }
                    processed++;
                }

                for (Account acc : accounts) {
                	log.info("Reencoding secrets for account {} @ {}", acc.getName(), acc.getSystem());
                    try {
                    	Password p = secretStoreService.getPassword(acc.getId());
                    	if (p != null)
                    		secretStoreService.setPassword(acc.getId(), p);
                    } catch (InternalErrorException e) {
                        log.warn("Error reencoding secrets", e);
                    }
                    processed++;
                }
}
            
        };
        thread = new  Thread(r);
        thread.start();
    }

}

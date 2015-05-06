package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Calendar;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Auditoria;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;

public class AuditPasswordQueryServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Logger log = Log.getLogger("AuditPasswordQueryServlet");
	
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String user = req.getParameter("user");
        String key = req.getParameter("key");
        String account = req.getParameter("account");
        String system = req.getParameter("system");
        String url = req.getParameter ("url");
        String app = req.getParameter ("application");

        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        SessioService ss = ServerServiceLocator.instance().getSessioService();
        UsuariService usuariService = ServerServiceLocator.instance().getUsuariService();
        try {
            Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
            if (usuari == null)
                throw new UnknownUserException(user);

            for (Sessio sessio : ss.getActiveSessions(usuari.getId())) {
                if (sessio.getClau().equals(key) ) {
                	Auditoria audit = new Auditoria ();
               		audit.setAccio( url == null ? "E" : "W");
               		audit.setAccount(account);
               		audit.setAplicacio(app == null ? url: app);
               		audit.setAutor(sessio.getCodiUsuari());
               		audit.setBbdd(system);
               		audit.setCalendar(Calendar.getInstance());
               		audit.setData("-");
               		audit.setObjecte("SSO");
               		ServiceLocator.instance().getAuditoriaService().create(audit);
                    writer.write("OK");
                    return;
                }
            }
            writer.write("ERROR|Invalid key");
            log.warn("Invalid key {} for user {}", key, user);
        } catch (Exception e) {
            log.warn("Error getting keys", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        } finally {
        	writer.close ();
        }
    }

}

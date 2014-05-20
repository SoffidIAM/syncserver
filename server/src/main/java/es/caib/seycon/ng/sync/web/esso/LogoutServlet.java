package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.sync.ServerServiceLocator;

public class LogoutServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("PasswordLoginServlet");
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        String session = req.getParameter("sessionId");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        try {
            SessioService sessioService = ServerServiceLocator.instance().getSessioService();
            Sessio sessio = sessioService.getSessioByHost(Long.decode(session), req.getRemoteAddr());
            if (sessio != null)
            	sessioService.destroySessio(sessio);
        } catch (Exception e) {
            log.warn("Error performing logout", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage()+"\n");
        }
        writer.close ();
        
    }

}

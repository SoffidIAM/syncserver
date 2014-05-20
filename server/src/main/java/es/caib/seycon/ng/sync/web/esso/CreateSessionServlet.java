package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.net.SeyconServiceLocator;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.sync.SeyconApplication;
import es.caib.seycon.ng.sync.engine.session.SessionManager;

public class CreateSessionServlet extends HttpServlet {

    Logger log = Log.getLogger("CreateSessionServlet");
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String user = req.getParameter("user");
        String clientIP = req.getParameter("clientIP");
        String port = req.getParameter("port");
        resp.setContentType("text/plain; charset=UTF-8");
        log.info("CreateSession: user="+user+" host="+req.getRemoteAddr()+" client="+clientIP+" port="+port,
                null, null);
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        try {
        	XarxaService xs = ServiceLocator.instance().getXarxaService();
        	Maquina maq = xs.findMaquinaByIp(req.getRemoteAddr());
        	if (maq == null)
        	{
        		throw new InternalErrorException("Unknown host "+req.getRemoteAddr());
        	}
        	Maquina client = null;
        	if (clientIP != null)
        		
        	{
        		client = xs.findMaquinaByIp(clientIP);
            	if (client == null)
            	{
            		client = new Maquina();
            		client.setNom(clientIP);
            		client.setAdreca(clientIP);
            	}
        	}
            Sessio session = SessionManager.getSessionManager().addSession(
                    maq,
                    Integer.parseInt(port),
                    user,
                    null, // Password,
                    client,
                    null,  // Sense clau
                    false, false); // Sense tancar altres sessions
            writer.write("OK|");
            writer.write(Long.toString(session.getId()));
            writer.write("\n");
        } catch (Exception e) {
            writer.write(e.getClass().getName() + "|" + e.getMessage()+"\n");
        }
        writer.close ();
    }

}

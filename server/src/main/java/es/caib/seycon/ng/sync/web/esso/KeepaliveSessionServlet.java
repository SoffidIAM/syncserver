package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

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
import es.caib.seycon.ng.comu.PuntEntrada;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.AutoritzacioService;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.SeyconApplication;
import es.caib.seycon.ng.sync.engine.challenge.ChallengeStore;
import es.caib.seycon.ng.sync.engine.session.SessionManager;
import es.caib.seycon.ng.utils.Security;

public class KeepaliveSessionServlet extends HttpServlet {

    Logger log = Log.getLogger("KeepaliveSessionServlet");
    
    private SessioService sessioService;
    private XarxaService xarxaService;
    private AutoritzacioService autoritzacioService;
    private UsuariService usuariService;
    
    static public Map<String,String> newSessionKeys = new Hashtable<String, String>();

    public KeepaliveSessionServlet() {
        sessioService = ServerServiceLocator.instance().getSessioService();
        xarxaService = ServerServiceLocator.instance().getXarxaService();
        autoritzacioService = ServerServiceLocator.instance().getAutoritzacioService();
        usuariService = ServerServiceLocator.instance().getUsuariService();
    }
    
    private String computeDiferences(String key, String newKey) {
    	if (newKey == null)
    		return null;
        StringBuffer b = new StringBuffer();
        ChallengeStore s = ChallengeStore.getInstance();
        for (int i=0; i <key.length();i++)
        {
            char ch = key.charAt(i);
            char ch2 = newKey.charAt(i);
            int dif = s.charToInt(ch2) - s.charToInt(ch);
            b.append (s.intToChar(dif));
        }
        return b.toString();
    }

    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        String user = req.getParameter("user");
        String key = req.getParameter("key");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        String[] auths = null;
        try {
            Security.nestedLogin(user, new String [] { 
                Security.AUTO_HOST_ALL_QUERY+Security.AUTO_ALL
            });

            try {
                Sessio sessio = null;
                String newSessionKey = null;
                Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
                for (Sessio s: sessioService.getActiveSessions(usuari.getId())) {
                    if (key.equals (s.getClau()))
                    {
                        sessio = s;
                        newSessionKey = computeDiferences(sessio.getClau(), sessio.getClauTemporal());
                        break;
                    }
                }
                if (sessio == null) {
                	writer.write("EXPIRED|Invalid session");
                }
                else
                {
                    Maquina maq = xarxaService.findMaquinaByNom(sessio.getNomMaquinaServidora());
                    if (maq == null || maq.getAdreca() == null || 
                    		!maq.getAdreca().equals(req.getRemoteAddr())) {
                        writer.write("EXPIRED|Invalid host");
                    } else {
                		writer.write("OK|");
                    	if (newSessionKey != null)
                    	{
                    		writer.write(newSessionKey+"|");
                    	}
                    }
                }
            } catch (Exception e) {
                log("Error keeping alive session", e);
                writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
            } finally {
                Security.nestedLogoff();
            }
        } catch (Exception e1) {
            throw new ServletException(e1);
        }
        writer.close();
    }

}

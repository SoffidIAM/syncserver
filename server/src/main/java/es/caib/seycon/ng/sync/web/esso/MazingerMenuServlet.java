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

import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.PuntEntrada;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.AutoritzacioService;
import es.caib.seycon.ng.servei.PuntEntradaService;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.session.SessionManager;
import es.caib.seycon.ng.utils.Security;

public class MazingerMenuServlet extends HttpServlet {
    Logger log = Log.getLogger("MazingerMenuServlet");
    private PuntEntradaService puntEntradaService;
    private SessioService sessioService;
    private XarxaService xarxaService;
    private AutoritzacioService autoritzacioService;
    private UsuariService usuariService;

    public MazingerMenuServlet() {
        puntEntradaService = ServerServiceLocator.instance().getPuntEntradaService();
        sessioService = ServerServiceLocator.instance().getSessioService();
        xarxaService = ServerServiceLocator.instance().getXarxaService();
        autoritzacioService = ServerServiceLocator.instance().getAutoritzacioService();
        usuariService = ServerServiceLocator.instance().getUsuariService();
    }

    ConnectionPool pool = ConnectionPool.getPool();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        String user = req.getParameter("user");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        SessionManager mgr = SessionManager.getSessionManager();
        String key = req.getParameter("key");
        String[] auths = null;
        try {
            auths = autoritzacioService.getUserAuthorizationsString(user);
            Security.nestedLogin(user, auths);

            try {
                Maquina maq = xarxaService.findMaquinaByIp(req.getRemoteAddr());
                if (maq == null) {
                    throw new UnknownHostException(req.getRemoteAddr());
                }
                Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
                if (usuari == null) {
                    throw new UnknownUserException(user);
                }
                Sessio foundSessio = null;
                for (Sessio s: sessioService.getActiveSessions(usuari.getId())) {
                    if (key.equals (s.getClau()))
                    {
                        foundSessio = s;
                        break;
                    }
                }
                if (foundSessio == null) {
                    throw new InternalErrorException("Invalid session key");
                }
                log.info("Generating menus for {}", user, null);
                StringBuffer buffer = new StringBuffer();
                PuntEntrada root = puntEntradaService.findRoot();
                generatePuntEntrada(root, buffer);
                writer.write("OK|");
                writer.write(buffer.toString());
            } catch (Exception e) {
                log("Error getting menu", e);
                writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
            } finally {
                Security.nestedLogoff();
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
        writer.close();
    }

    public void generatePuntEntrada(PuntEntrada punt, StringBuffer buffer) throws InternalErrorException {

        if ("S".equals(punt.getMenu())) {
            buffer.append("MENU|");
            buffer.append(punt.getNom());
            buffer.append("|");
            for (PuntEntrada child : puntEntradaService.findChildren(punt)) {
                generatePuntEntrada(child, buffer);
            }
            buffer.append("ENDMENU|");
        } else {
            buffer.append(punt.getId());
            buffer.append("|");
            buffer.append(punt.getNom());
            buffer.append("|");
            buffer.append(punt.getId() == null ? "-1" : punt.getId().toString());
            buffer.append("|");
        }

    }

}

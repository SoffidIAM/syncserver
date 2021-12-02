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

import com.soffid.iam.api.AccessTree;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.service.AuthorizationService;
import com.soffid.iam.service.EntryPointService;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.session.SessionManager;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class MazingerMenuServlet extends HttpServlet {
    Logger log = Log.getLogger("MazingerMenuServlet");
	private EntryPointService puntEntradaService;
	private SessionService sessioService;
	private NetworkService xarxaService;
	private AuthorizationService autoritzacioService;
	private UserService usuariService;

    public MazingerMenuServlet() {
        puntEntradaService = ServerServiceLocator.instance().getEntryPointService();
        sessioService = ServerServiceLocator.instance().getSessionService();
        xarxaService = ServerServiceLocator.instance().getNetworkService();
        autoritzacioService = ServerServiceLocator.instance().getAuthorizationService();
        usuariService = ServerServiceLocator.instance().getUserService();
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
                boolean trackIp = "true".equals( ConfigurationCache.getProperty("SSOTrackHostAddress"));
                Host maq = xarxaService.findHostByIp(com.soffid.iam.utils.Security.getClientIp());
                if (maq == null && trackIp) {
                    throw new UnknownHostException(com.soffid.iam.utils.Security.getClientIp());
                }
                User usuari = usuariService.findUserByUserName(user);
                if (usuari == null) {
                    throw new UnknownUserException(user);
                }
                Session foundSessio = null;
                for (Session s: sessioService.getActiveSessions(usuari.getId())) {
                    if (key.equals (s.getKey()))
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
                AccessTree root = puntEntradaService.findRoot();
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
    
    private String removeSlash(String s)
    {
    	return s.replace('/', ' ').replace('\\',' ').replace('|',' ');
    }

    public void generatePuntEntrada(AccessTree punt, StringBuffer buffer) throws InternalErrorException {

        if (punt.isMenu()) {
            buffer.append("MENU|");
            buffer.append(removeSlash(punt.getName()));
            buffer.append("|");
            for (AccessTree child : puntEntradaService.findChildren(punt)) {
                generatePuntEntrada(child, buffer);
            }
            buffer.append("ENDMENU|");
        } else if (!punt.getExecutions().isEmpty()) {
            buffer.append(punt.getId());
            buffer.append("|");
            buffer.append(removeSlash(punt.getName()));
            buffer.append("|");
            buffer.append(punt.getId() == null ? "-1" : punt.getId().toString());
            buffer.append("|");
        }

    }

}

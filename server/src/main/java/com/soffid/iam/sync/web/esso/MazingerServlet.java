package com.soffid.iam.sync.web.esso;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.User;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class MazingerServlet extends HttpServlet {

    private ServerService serverService;

    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("Mazinger Servlet");

    public MazingerServlet() {
        serverService = ServerServiceLocator.instance().getServerService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String user = request.getParameter("user");
        String modoXml = request.getParameter("xml"); // para modo debug (ver
                                                      // salida xml)
        String version = request.getParameter("version");
        if (user != null && !"".equals(user.trim())) {
            try {
                User usuari = serverService.getUserInfo(user, null);
                if (modoXml == null) {
                    byte data[] = serverService.getUserMazingerRules(usuari.getId(), version == null ? "2": version);

                    try {
                        response.setContentType("application/octet-stream");
                        response.getOutputStream().write(data);
                    } catch (Throwable th) {
                        log.warn("Error de compilación XML ", th);
                        throw new ServletException(th.getMessage());
                    }
                } else {
                    byte data[] = serverService.getUserMazingerRules(usuari.getId(), "xml");
                    response.setContentType("text/xml");
                    response.getOutputStream().write(data);
                }
            } catch (InternalErrorException e) {
                throw new ServletException(e);
            } catch (UnknownUserException e) {
                throw new ServletException(e);
            }
        }

    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }

}

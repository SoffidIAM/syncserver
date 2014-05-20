package es.caib.seycon.ng.sync.web.wsso;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.sso.Secret;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.SecretStoreService;

public class WebSessionServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		SessioService sessioService = ServerServiceLocator.instance()
				.getSessioService();
		try {
			String id = req.getParameter("sessionId");
			String key = req.getParameter("key");
			Sessio sessio = sessioService.getSession(Long.decode(id).longValue(), key);

			String action = req.getParameter("action");
			byte b[] = null;
			if ("register".equals(action)  || "createSession".equals(action)) {
				
				if (sessio != null && sessio.getUrl() != null)
				{
					Sessio newSession =
						sessioService.registraSessioWeb(sessio.getCodiUsuari(), 
							req.getParameter("host"), 
							req.getParameter("clientHost"), 
							req.getParameter("url"));
					String sessionId = "OK|"+Long.toString(newSession.getId())+"|"+newSession.getClau();
					b = sessionId.getBytes("UTF-8");
				} else {
					b = "ERROR|Invalid session key".getBytes("UTF-8");
				}
			} else if ("keepAlive".equals(action)) {
				if (sessio != null && sessio.getUrl() != null)
					sessioService.sessioKeepAlive(sessio);

				b = "OK".getBytes("UTF-8");
			} else if ("getSecrets".equals(action)) {
		        StringBuffer result = new StringBuffer("OK");
		        
		        Usuari usuari = ServerServiceLocator.instance().getUsuariService().findUsuariByCodiUsuari(sessio.getCodiUsuari());
		        SecretStoreService sss = ServerServiceLocator.instance().getSecretStoreService();
		        for (Secret secret : sss.getAllSecrets(usuari)) {
		            result.append('|');
		            result.append(secret.getName());
		            result.append('|');
		            result.append(secret.getValue().getPassword());

		        }
		        b = result.toString().getBytes("UTF-8");
			}
			resp.setContentType("text/plain");
			resp.setCharacterEncoding("UTF-8");
			resp.setContentLength(b.length);
			resp.getOutputStream().write(b);
		} catch (Exception e) {
			resp.getOutputStream().print("ERROR|");
			resp.getOutputStream().print(e.toString());
			log("Error on web session servlet", e);
		}
	}

}

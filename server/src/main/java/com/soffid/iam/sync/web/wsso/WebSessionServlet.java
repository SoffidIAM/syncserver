package com.soffid.iam.sync.web.wsso;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class WebSessionServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		SessionService sessioService = ServerServiceLocator.instance()
				.getSessionService();
		try {
			String id = req.getParameter("sessionId");
			String key = req.getParameter("key");
			Session sessio = sessioService.getSession(Long.decode(id).longValue(), key);

			String action = req.getParameter("action");
			byte b[] = null;
			if ("register".equals(action)  || "createSession".equals(action)) {
				
				if (sessio != null && sessio.getUrl() != null)
				{
					Session newSession =
						sessioService.registerWebSession(sessio.getUserName(), 
							req.getParameter("host"), 
							req.getParameter("clientHost"), 
							req.getParameter("url"));
					String sessionId = "OK|"+Long.toString(newSession.getId())+"|"+newSession.getKey();
					b = sessionId.getBytes("UTF-8");
				} else {
					b = "ERROR|Invalid session key".getBytes("UTF-8");
				}
			} else if ("keepAlive".equals(action)) {
				if (sessio != null && sessio.getUrl() != null)
					sessioService.sessionKeepAlive(sessio);

				b = "OK".getBytes("UTF-8");
			} else if ("getSecrets".equals(action)) {
		        StringBuffer result = new StringBuffer("OK");
		        
		        User usuari = ServerServiceLocator.instance().getUserService().findUserByUserName(sessio.getUserName());
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

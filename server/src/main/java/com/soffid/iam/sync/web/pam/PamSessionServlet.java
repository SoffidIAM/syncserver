package com.soffid.iam.sync.web.pam;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.SecretStoreService;

public class PamSessionServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		SessionService sessioService = ServerServiceLocator.instance()
				.getSessionService();
		try {
			String sessionKey = req.getParameter("sessionKey");
			String policy = req.getParameter("policy");
			String rule = req.getParameter("rule");
			ServiceLocator.instance().getPamPolicyService().applyRule(sessionKey, policy, rule);
			resp.getOutputStream().print("OK");
		} catch (Exception e) {
			resp.getOutputStream().print("ERROR|");
			resp.getOutputStream().print(e.toString());
			log("Error on web session servlet", e);
		}
	}

}

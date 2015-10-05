package com.soffid.iam.sync.web.internal;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import com.soffid.iam.utils.Security;

public class ServerInvokerServlet extends InvokerServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected void finishRequest ()
	{
		Security.nestedLogoff();
	}

	protected void prepareRequest (HttpServletRequest request)
	{
		Security.nestedLogin(request.getRemoteUser(), new String [] {Security.AUTO_AUTHORIZATION_ALL});
	}

}

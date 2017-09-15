package com.soffid.iam.sync.web.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;

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
		String userName = request.getRemoteUser();
		String tenant;
		try {
			if (userName == null)
			{
				tenant = Security.getMasterTenantName();
				userName = "anonymous";
			}
			else if (userName.startsWith("-seu-"))
			{
				tenant = URLDecoder.decode(userName.substring(5), "UTF-8");
				userName = "Console";
			}
			else
			{
				userName = URLDecoder.decode(userName, "UTF-8");
				int i = userName.indexOf('\\');
				if ( i < 0 )
				{
					tenant = Security.getMasterTenantName();
					userName = URLDecoder.decode(userName,"UTF-8");
				}
				else
				{
					tenant = URLDecoder.decode(userName.substring(0, i), "UTF-8");
					userName = URLDecoder.decode(userName.substring(i+1), "UTF-8");
				}
				if (tenant.equals("master"))
				{
					String targetTenant = request.getHeader("Soffid-Tenant");
					if (targetTenant != null)
						tenant = targetTenant;
				}
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (InternalErrorException e) {
			throw new RuntimeException(e);
		}
		Security.nestedLogin(tenant, userName, Security.ALL_PERMISSIONS);
	}

}

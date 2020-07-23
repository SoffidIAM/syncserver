package com.soffid.iam.sync.jetty;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.soffid.iam.config.Config;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;


public class InvokerFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Invoker oldInvoker = Invoker.getInvoker();
        try {
            Invoker.setInvoker(new Invoker((HttpServletRequest)request));
            String user = Invoker.getInvoker().getUser();
            if (user == null)
            {
            	String host = Config.getConfig().getHostName();
            	String tenant = Security.getMasterTenantName();
        		if (request.getServerName() != null &&
        				request.getServerName().endsWith("."+host))
        		{
        			String server = request.getServerName();
        			tenant = server.substring(0, server.length() - host.length() - 1);
        		}
        		Security.nestedLogin(tenant, "anonymous", Security.ALL_PERMISSIONS);
        		try {
        			try {
        				Security.setClientRequest((HttpServletRequest) request);
        			} catch (Exception e) {}
        			chain.doFilter(request, response);
        		} finally {
        			Security.nestedLogoff();
        		}
            	
            }
            else
            {
	            user = URLDecoder.decode(user, "UTF-8");
	            int i = user.indexOf('\\');
	            if (i > 0)
	            {
	            	Security.nestedLogin(user.substring(0, i), user.substring(i+1), Security.ALL_PERMISSIONS);
	            	try
	            	{
	        			try {
	        				Security.setClientRequest((HttpServletRequest) request);
	        			} catch (Throwable e) {}
	                	chain.doFilter(request, response);
	            	}
	            	finally
	            	{
	            		Security.nestedLogoff();
	            	}
	            }
	            else
	            	chain.doFilter(request, response);
            }
        } finally {
            Invoker.setInvoker(oldInvoker);
        }
    }

    public void destroy() {
    }

}

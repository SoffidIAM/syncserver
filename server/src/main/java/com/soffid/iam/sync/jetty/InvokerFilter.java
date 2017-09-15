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

import com.soffid.iam.utils.Security;


public class InvokerFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Invoker oldInvoker = Invoker.getInvoker();
        try {
            Invoker.setInvoker(new Invoker((HttpServletRequest)request));
            String user = URLDecoder.decode(Invoker.getInvoker().getUser(), "UTF-8");
            int i = user.indexOf('\\');
            if (i > 0)
            {
            	Security.nestedLogin(user.substring(0, i), user.substring(i+1), Security.ALL_PERMISSIONS);
            	try
            	{
                	chain.doFilter(request, response);
            	}
            	finally
            	{
            		Security.nestedLogoff();
            	}
            }
            else
            	chain.doFilter(request, response);
        } finally {
            Invoker.setInvoker(oldInvoker);
        }
    }

    public void destroy() {
    }

}

package es.caib.seycon.ng.sync.jetty;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;


public class InvokerFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Invoker oldInvoker = Invoker.getInvoker();
        try {
            Invoker.setInvoker(new Invoker((HttpServletRequest)request));
            chain.doFilter(request, response);
        } finally {
            Invoker.setInvoker(oldInvoker);
        }
    }

    public void destroy() {
    }

}

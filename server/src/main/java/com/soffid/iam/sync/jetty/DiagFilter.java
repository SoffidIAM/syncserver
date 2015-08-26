package com.soffid.iam.sync.jetty;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class DiagFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String name = Thread.currentThread().getName();
            int colon = name.indexOf(":");
            if (colon >= 0) {
                name = name.substring(0,colon);
            }
            Thread.currentThread().setName(name+": "+req.getRequestURI());
            try {
                chain.doFilter(request, response);
            } finally {
                Thread.currentThread().setName(name+": "+req.getRequestURI()+" (DONE)");
            }
        }

    }

    public void destroy() {
    }

}

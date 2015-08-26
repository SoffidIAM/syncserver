package com.soffid.iam.sync.jetty;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

public class Invoker {
    static ThreadLocal<Invoker> requests = new ThreadLocal<Invoker>();
    
    /* (non-Javadoc)
     * @see es.caib.seycon.jetty.InvokableInterface#setRequest(javax.servlet.http.HttpServletRequest)
     */
    public static void setInvoker (Invoker request)
    {
        if (request == null)
            requests.remove();
        else
            requests.set(request);
    }

    /* (non-Javadoc)
     * @see es.caib.seycon.jetty.InvokableInterface#getRequest()
     */
    public static Invoker getInvoker ()
    {
        return requests.get();
    }

    String user;
    InetAddress addr;
    HttpServletRequest request;
    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }
    public InetAddress getAddr() {
        return addr;
    }
    public void setAddr(InetAddress addr) {
        this.addr = addr;
    }
    public HttpServletRequest getRequest() {
        return request;
    }
    public void setRequest(HttpServletRequest request) {
        this.request = request;
        try {
            addr = InetAddress.getByName(request.getRemoteAddr());
        } catch (UnknownHostException e) {
        }
        user = request.getUserPrincipal().getName();
        
    }
    public Invoker(HttpServletRequest request) {
        super();
        this.request = request;
        this.user = request.getRemoteUser();
        try {
            this.addr = InetAddress.getByName(request.getRemoteHost());
        } catch (UnknownHostException e) {
            try {
                this.addr = InetAddress.getByName(request.getRemoteAddr());
            } catch (UnknownHostException e1) {
                this.addr = null;
            }
        }
    }
    
    public Invoker(String user, InetAddress addr) {
        super();
        this.user = user;
        this.addr = addr;
    }
    public Invoker(InetAddress addr) {
        super();
        this.addr = addr;
    }
    
}

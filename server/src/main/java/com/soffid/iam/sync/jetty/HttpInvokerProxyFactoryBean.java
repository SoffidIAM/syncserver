package com.soffid.iam.sync.jetty;

import java.io.IOException;

import org.springframework.beans.factory.FactoryBean;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteInvokerFactory;
import com.soffid.iam.remote.URLManager;

public class HttpInvokerProxyFactoryBean implements FactoryBean{
    private String serviceName;
    private String serviceInterface;
    static int roundRobin = 0;
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public Object getObject() throws Exception {
        String[] servers =  Config.getConfig().getServerList().split("[, ]+");
        RemoteInvokerFactory factory = new RemoteInvokerFactory();
        int next = roundRobin;
        Exception lastException = null;;
        for (int i = 0; i < servers.length; i++) {
            if (next >= servers.length)
                next = 0;
            try {
                URLManager um = new URLManager(servers[next]);
                Object handler = factory.getInvoker(um.getHttpURL("/seycon/"+serviceName));
                if (handler != null)
                    return handler;
            } catch (IOException e) {
                lastException = e;
                next ++;
            }
        }
        if (lastException == null) 
            throw new IOException("No servers configured");
        else
            throw lastException;
            
    }

    public Class<?> getObjectType() {
        return null;
    }
    

    public boolean isSingleton() {
        return false;
    }

}

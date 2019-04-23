package com.soffid.iam.sync.agent;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.Enumeration;
import java.util.LinkedList;

public class AgentClassLoader extends URLClassLoader {

    public AgentClassLoader(URL[] urls) {
        super(urls);
    }

    public AgentClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public AgentClassLoader(URL[] urls, ClassLoader parent,
            URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
	        // First, check if the class has already been loaded
	        Class c = findLoadedClass(name);
	        if (c == null) {
	            if (getParent() != null && (name.startsWith("java.")  || name.startsWith("javax.")) &&
	            		! name.startsWith("javax.ws.")) {
	                try {
	                    c = getParent().loadClass(name);
	                } catch (ClassNotFoundException e) {
	                    c = findClass(name);
	                }
	            } else {
	                try {
	                    c = findClass(name);
	                } catch (ClassNotFoundException e) {
	                    // If still not found, then invoke findClass in order
	                    // to find the class.
	                    if (getParent() != null) {
	                        c = getParent().loadClass(name);
	                    } else {
	                        throw new ClassNotFoundException(name);
	                    }
	                }
	            }
	        }
	        return c;
        }
    }

	@Override
	public URL getResource(String name) {
        URL url;
        url = findResource(name);
        if (url == null)
        {
        	url = super.getResource(name);
        }
        return url;
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration[] tmp = new Enumeration[2];
        tmp[0] = findResources(name);
        if (getParent() != null) {
            tmp[1] = getParent().getResources(name);
        } else {
            tmp[1] = ClassLoader.getSystemClassLoader().getResources(name);
        }

        return new SimpleEnumeration<URL>(tmp);
	}

}

package com.soffid.iam.sync.agent;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

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
        // First, check if the class has already been loaded
        Class c = findLoadedClass(name);
        if (c == null) {
            if (getParent() != null && (name.startsWith("java.")  || name.startsWith("javax."))) {
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

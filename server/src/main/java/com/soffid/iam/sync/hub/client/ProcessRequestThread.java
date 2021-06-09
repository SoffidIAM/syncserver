package com.soffid.iam.sync.hub.client;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.lang.MessageFactory;
import com.soffid.iam.ssl.ConnectionFactory;
import com.soffid.iam.sync.hub.server.Request;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.jetty.JettyServer;

import es.caib.seycon.ng.exception.InternalErrorException;

public class ProcessRequestThread extends Thread{
	Log log = LogFactory.getLog(getClass());
	
	private Request request;
	private JettyServer server;

	private String targetUrl;

	public ProcessRequestThread(JettyServer server, Request request, String targetUrl) {
		super ("Request #"+request.getId());
		setDaemon(true);
		this.request = request;
		this.server = server;
		this.targetUrl = targetUrl;
	}
	
	@Override
	public void run() {
		try {
			if ( ! request.getUrl().startsWith("/seycon"))
			{
				log.warn("Unexpected request "+request.getUrl()+" does not start with /seycon");
			}
			Object handler = server.getServiceHandler(request.getUrl());
			if (handler == null)
			{
				sendResult (false, new IOException("Service not found"));
			}
			else
			{
				if (request.getMethod().equals(""))
					processGet (handler);
				else
					processPost(handler);
			}
		} catch (Exception e) {
			log.warn("Error processing request ", e);
		}
	}

	private void processPost(Object handler) {
        // Leer parámetros
        ClassLoader classLoader = getClass().getClassLoader();
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
        	Thread.currentThread().setContextClassLoader(classLoader);

            try {
                String methodName = request.getMethod();
                int len = request.getClasses().length;
                Class[] classes = new Class[len];
                for (int i = 0; i < len; i++) {
                    String clazz = request.getClasses()[i];
                    if ("boolean".equals(clazz))
                        classes[i] = Boolean.TYPE;
                    else if ("int".equals(clazz))
                        classes[i] = Integer.TYPE;
                    else if ("long".equals(clazz))
                        classes[i] = Long.TYPE;
                    else if ("double".equals(clazz))
                        classes[i] = Double.TYPE;
                    else if ("float".equals(clazz))
                        classes[i] = Float.TYPE;
                    else
                        classes[i] = Class.forName(clazz, false, classLoader);
                }
    
                Method method = handler.getClass().getMethod(methodName, classes);
    
                Object result = null;
                
                Locale previousLocale = MessageFactory.getThreadLocale();
                
                try {
                	Invoker invoker = new com.soffid.iam.sync.jetty.Invoker( request.getSource(), null);
                	Invoker.setInvoker(invoker);
                    result = method.invoke(handler, request.getArgs());
                    sendResult(true, result);
                } catch (InvocationTargetException e) {
                	sendResult(false, new InternalErrorException(e.getMessage(), e.getCause()));
                } finally {
                	MessageFactory.setThreadLocale(previousLocale);
                	Invoker.setInvoker(null);
                }
            } catch (Exception e) {
                log.warn("Error invoking " + request.getUrl(), e);
            }
        } finally {
        	Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
	}

	private void processGet(Object handler) throws IOException {
        Vector v = new Vector(3);
        try
		{
		    populateInterfaces (handler.getClass(), v);
			StringBuffer s = new StringBuffer(50) ;
			for (int i = 0; i < v.size(); i++)
			{
			     if (i > 0)
			            s.append(',');
			    s.append(v.get(i).toString());
			}
			sendResult(true, s.toString());
		}
		catch (Exception e)
		{
			log.warn("Unexpected error", e);
			sendResult(false, e);
		}
	}

	private void sendResult(boolean success, Object result) throws IOException {
		HttpURLConnection conn = ConnectionFactory.getConnection(new URL(targetUrl));
		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.connect();
		OutputStream out = conn.getOutputStream();
		ObjectOutputStream oout = new ObjectOutputStream(out);
		oout.writeLong(request.getId());
		oout.writeBoolean(success);
		oout.writeObject(result);
		oout.close();
		conn.getInputStream().close();
	}


    private void populateInterfaces(Class clazz, Vector v) {
        if (clazz == null)
            return;

        populateInterfaces(clazz.getSuperclass(), v);

        Class clazzes [] = clazz.getInterfaces();
        if (clazz != null )
        {
            for (int i = 0; i < clazzes.length; i++)
            {
                addInterface(v, clazzes[ i ]);
            }
        }
    }

    private void addInterface(Vector v, Class clazz) {
    	String className = clazz.getName();
        if ( ! className.startsWith("java.lang") &&
        	 ! className.startsWith("org.springframework") &&
        	 ! className.startsWith("org.hibernate") &&
             ! v.contains(clazz.getName()))
        {
            v.add(clazz.getName());
        }
    }
}

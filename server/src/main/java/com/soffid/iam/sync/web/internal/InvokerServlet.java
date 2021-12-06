package com.soffid.iam.sync.web.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.lang.MessageFactory;

import es.caib.seycon.ng.exception.InternalErrorException;

public class InvokerServlet extends HttpServlet {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static Class<?> advisedClass = null;
	static Class<?> nestedRuntimeExceptionClass = null;
	
	
	{
		try {
			advisedClass = Class.forName("org.springframework.aop.framework.Advised");
		} catch (ClassNotFoundException e) {
			advisedClass = null;
		}
		try {
			nestedRuntimeExceptionClass = Class.forName("org.springframework.core.NestedRuntimeException");
		} catch (ClassNotFoundException e) {
			nestedRuntimeExceptionClass = null;
		}
	}
	
	private Object target;
    Logger log = Log.getLogger("Invoker");
    private String targetName;

    public Object getTarget() {
        targetName = getServletConfig().getInitParameter("target");
        return getServletConfig().getServletContext().getAttribute(targetName);
    }

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        // Obtener el target
       
        target = getTarget ();
        
        // Leer parámetros
        ClassLoader classLoader = getClass().getClassLoader();
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
        	Thread.currentThread().setContextClassLoader(classLoader);

            ObjectInputStream oin = new ObjectInputStream(request.getInputStream());
            prepareRequest(request);
            try {
                String methodName = oin.readUTF();
                int len = oin.readInt();
                Class[] classes = new Class[len];
                for (int i = 0; i < len; i++) {
                    String clazz = oin.readUTF();
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
    
                Method method = target.getClass().getMethod(methodName, classes);
    
                Object data[] = new Object[len];
                for (int i = 0; i < len; i++) {
                    data[i] = oin.readObject();
                }
                oin.close();
    
                Object result = null;
                
                Locale previousLocale = MessageFactory.getThreadLocale();
    
                response.setContentType("x-application/java-rmi");
                try {
                    MessageFactory.setThreadLocale( request.getLocale() );
                    result = method.invoke(target, data);
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setHeader("Success", "true");
//                    ((Request) request).setHandled(true);
                } catch (InvocationTargetException e) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setHeader("Success", "false");
                    result = e.getCause();
                    if (nestedRuntimeExceptionClass != null &&
                    	nestedRuntimeExceptionClass.isAssignableFrom(result.getClass()))
                    {
                        log.warn("Unexpected error", e);
                        result = new InternalErrorException("Unexpected error", e);
                    }
                    else if (result != null)
                    {
                    	boolean validException = false;
                    	for ( Class<?> exceptionClass: method.getExceptionTypes())
                    		if ( exceptionClass.isAssignableFrom(result.getClass()) )
                    			validException = true;
                    	if ( !validException)
                    		result = new InternalErrorException(e.getCause().getMessage(), e.getCause());
                    }
                } finally {
                	MessageFactory.setThreadLocale(previousLocale);
                }
                ByteArrayOutputStream baout = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(baout);
                oout.writeObject(result);
                oout.close();
                baout.close();
                response.setContentLength(baout.size());
                
                OutputStream out = response.getOutputStream();
                out.write(baout.toByteArray());
                out.close();
//                ((Request) request).setHandled(true);
            } catch (Exception e) {
                log.warn("Error invoking " + request.getRequestURI(), e);
            } finally {
                finishRequest();
            }
        } finally {
        	Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

	protected void finishRequest ()
	{
	}

	protected void prepareRequest (HttpServletRequest request)
	{
	}

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        Object obj = getTarget();
        Vector v = new Vector(3);
        
        try
		{
			if (advisedClass != null && advisedClass.isAssignableFrom(obj.getClass())) {
				Class[] interfaces = (Class[]) advisedClass.getMethod("getProxiedInterfaces").invoke(obj);
			    for ( Class clazz: interfaces) {
			        addInterface(v, clazz);
			    }
			} else {
			    populateInterfaces (obj.getClass(), v);
			}
			StringBuffer s = new StringBuffer(50) ;
			for (int i = 0; i < v.size(); i++)
			{
			     if (i > 0)
			            s.append(',');
			    s.append(v.get(i).toString());
			}
			response.setHeader("Classes", s.toString());
			response.setStatus(HttpServletResponse.SC_OK);
			ServletOutputStream out = response.getOutputStream();
			out.println("OK");
			out.close();
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Unexpected error", e);
			throw new ServletException(e);
		}
		catch (SecurityException e)
		{
			log.warn("Unexpected error", e);
			throw new ServletException(e);
		}
		catch (IllegalAccessException e)
		{
			log.warn("Unexpected error", e);
			throw new ServletException(e);
		}
		catch (InvocationTargetException e)
		{
			log.warn("Unexpected error", e);
			throw new ServletException(e);
		}
		catch (NoSuchMethodException e)
		{
			log.warn("Unexpected error", e);
			throw new ServletException(e);
		}
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

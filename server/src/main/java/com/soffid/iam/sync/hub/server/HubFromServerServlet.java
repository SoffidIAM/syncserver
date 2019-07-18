package com.soffid.iam.sync.hub.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.Hashtable;
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Request;

import com.soffid.iam.config.Config;
import com.soffid.iam.lang.MessageFactory;

import es.caib.seycon.ng.exception.InternalErrorException;

public class HubFromServerServlet extends HttpServlet {
	private static Hashtable<String,String> urlToServerName = new Hashtable<String,String>();
	private Config config;
	private HubQueue hubQueue;
	static Log log = LogFactory.getLog(HubFromServerServlet.class);
	
	@Override
	public void init(ServletConfig filterConfig) throws ServletException {
		try {
			config = Config.getConfig();
			hubQueue = HubQueue.instance();
		} catch (IOException e) {
			throw new ServletException ("Error initializing HubFilter",e);
		}
	}

	
	@Override
	public void service(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		String hostName = request.getHeader("Host");
		String url;
		try {
			url = "https://"+hostName+"/";
			String server = getServerForUrl(url);
			if (server == null)
			{
				log.warn("Received request "+ request.getRequestURI() + "for unknown host "+hostName);
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
			else 
			{
				try {
					forwardToRemote(server, (HttpServletRequest) request, (HttpServletResponse) response, hostName);
				} catch (Throwable e) {
					throw new ServletException ( e );
				}
			}
		} catch (InternalErrorException e) {
			throw new ServletException(e);
		}
	}

	private void forwardToRemote(String server, HttpServletRequest request, HttpServletResponse response, String hostName)
			throws RemoteException, IOException, ServletException, Throwable {
		try {
			HttpServletRequest req = (HttpServletRequest) request; 
			
		    ClassLoader classLoader = getClass().getClassLoader();

		    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		    try {
		    	Thread.currentThread().setContextClassLoader(classLoader);
		    	if ( req.getMethod().equals("GET"))
		    	{
		            Locale previousLocale = MessageFactory.getThreadLocale();
					try {
		                MessageFactory.setThreadLocale( request.getLocale() );
		                Object result = hubQueue.process(req.getRemoteUser(), server, req.getRequestURI(), "", null, null);
		                response.setStatus(HttpServletResponse.SC_OK);
		    			response.setHeader("Classes", result.toString());
		    			response.setStatus(HttpServletResponse.SC_OK);
		                response.setHeader("Success", "true");
		                ((Request) request).setHandled(true);
		            } catch (InvocationTargetException e) {
		            	throw new ServletException(e);
		            } finally {
		            	MessageFactory.setThreadLocale(previousLocale);
		            }
		    	}
		    	else
		    	{
			    	ObjectInputStream oin = new ObjectInputStream(request.getInputStream());
			        try {
			            String methodName = oin.readUTF();
			            int len = oin.readInt();
			            String[] classes = new String[len];
			            for (int i = 0; i < len; i++) {
			                classes[i] = oin.readUTF();
			            }
			
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
			                result = hubQueue.process(req.getRemoteUser(), server, req.getRequestURI(), methodName, classes, data);
			                response.setStatus(HttpServletResponse.SC_OK);
			                response.setHeader("Success", "true");
			                ((Request) request).setHandled(true);
			            } catch (InvocationTargetException e) {
			                response.setStatus(HttpServletResponse.SC_OK);
			                response.setHeader("Success", "false");
			                result = e.getCause();
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
			            ((Request) request).setHandled(true);
			        } catch (Exception e) {
			            log.warn("Error invoking " + request.getRequestURI(), e);
			        }
		    	}
		    } finally {
		    	Thread.currentThread().setContextClassLoader(oldClassLoader);
		    }
		} catch (InternalErrorException e) {
			throw new ServletException(e);
		}
	}

	private String getServerForUrl(String url) throws InternalErrorException, IOException, ServletException {
		String server = urlToServerName.get(url);
		if (server == null)
		{
			server = hubQueue.getServerForPath(url);
			if (server == null)
				throw new ServletException("Unknown server "+url);
			urlToServerName.put(url, server);
		}
		return server;
	}

	public void destroy() {
	}

}

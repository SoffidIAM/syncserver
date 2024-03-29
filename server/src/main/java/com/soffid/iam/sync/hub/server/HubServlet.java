package com.soffid.iam.sync.hub.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * 
 * This servlet is used by remote sync servers to fetch remote invocation requests and
 * to pot remote invocation results
 * 
 *
 */
public class HubServlet extends HttpServlet {
	Log log = LogFactory.getLog(getClass());
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = null;
		try {
			user = req.getRemoteUser();
			
			if (user.contains("\\"))
				user = user.substring(user.indexOf("\\")+1);
			
			user = new URLDecoder().decode(user, "UTF-8");
			
			String active = req.getParameter("active");
			if (active != null) {
				HubQueue.instance().setActive (user, active);
			}
			Request r = HubQueue.instance().get(user);
			if ( r == null)
			{
				resp.setStatus( HttpServletResponse.SC_NO_CONTENT);
			}
			else
			{
				ByteArrayOutputStream o = new ByteArrayOutputStream ();
				new ObjectOutputStream(o).writeObject(r);
				resp.setStatus(HttpServletResponse.SC_OK);
				resp.setContentLength(o.size());
				ServletOutputStream o2 = resp.getOutputStream();
				o2.write(o.toByteArray());
				o2.close();
			}
		} catch (Exception e) {
			log.warn("Error providing request to "+user, e);
			if (e instanceof IOException)
				throw e;
			else
				throw new IOException(e);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = req.getRemoteUser();

		try {
			if (user.contains("\\"))
				user = user.substring(user.indexOf("\\")+1);
			
			user = new URLDecoder().decode(user, "UTF-8");
	
			ObjectInputStream oin = new ObjectInputStream(req.getInputStream());
	        
	        long requestId = oin.readLong();
	        boolean success = oin.readBoolean();
	        Object result;
			try {
				result = oin.readObject();
				try {
					HubQueue.instance().post(user, requestId, result, success);
				} catch (InternalErrorException e) {
					log.warn("Error registering response", e);
					throw new IOException(e);
				}
			} catch (Exception e) {
				try {
					HubQueue.instance().post(user, requestId, e, false);
				} catch (InternalErrorException e2) {
					throw new IOException(e2);
				}
			}
	        oin.close();
			resp.setStatus(HttpServletResponse.SC_OK);
		} catch (Exception e) {
			log.warn("Error processing response from "+user, e);
			if (e instanceof IOException)
				throw e;
			else
				throw new IOException(e);
		}
	}

}

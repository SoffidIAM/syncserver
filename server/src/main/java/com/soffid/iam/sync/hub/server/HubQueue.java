package com.soffid.iam.sync.hub.server;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.api.Server;
import com.soffid.iam.remote.RemoteServiceLocator;

import es.caib.seycon.ng.exception.InternalErrorException;

public class HubQueue {
	public final static int TIMEOUT_TO_ACCEPT = 10;
	public final static int ACCEPT_DELAY = TIMEOUT_TO_ACCEPT / 2;
	public final static int SECONDS_TO_WAIT = 3;
	public final static int LONG_ACTION_KEEPALIVE = 20;
	public final static int LONG_ACTION_TIMEOUT = LONG_ACTION_KEEPALIVE * 2;
	boolean debug = false;
	
	final static private HubQueue instance = new HubQueue();
	Log log = LogFactory.getLog(getClass());
	
	long requestId = System.currentTimeMillis();
	
	Map<String, LinkedList<Request>> pendingRequests = new Hashtable<String, LinkedList<Request>>();  
	Map<Long, Request> activeRequests = new Hashtable<Long, Request>();
	
	/**
	 * Gets a pending request to process
	 * 
	 * @param server
	 * @return
	 */
	public Request get ( String server) {
		if (debug) {
			log.info("Fetching requests for "+server);
		}
		LinkedList<Request> list = pendingRequests.get(server);
		if (list == null)
		{
			if (debug) 
				log.info("NO requests for "+server);
			return null;
		}
		
		synchronized (list)
		{
			if (list.isEmpty())
			{
				try {
					list.wait(3000);
				} catch (InterruptedException e) {
				}
			}
			if (list.isEmpty()) {
				if (debug) 
					log.info("NO requests for "+server);
				return null;
			}
			Request request = list.pollFirst();
			request.setId ( new Long (requestId++) );
			request.setAccepted(true);
			activeRequests.put(request.getId(), request);
			log.info("Sent request for "+server);
			return request;
		}
	}
	
	public void post ( Long requestId, Object result, boolean success) throws InternalErrorException
	{
		Request r = activeRequests.get(requestId);
		if (r == null || r.isDone())
		{
			throw new InternalErrorException("Request already processed");
		}
		synchronized (r) {
			r.setSuccess(success);
			r.setResponse(result);
			r.setDone(true);
			r.notifyAll();
			activeRequests.remove(requestId);
		}
	}
	
	public Object process (String user, String host, String url, String method, String classes[], Object params[] ) 
			throws Throwable
	{
		Request req = new Request();
//		log.info("Invoking "+url);
		req.setSource(user);
		req.setTarget(host);
		req.setUrl(url);
		req.setStart(System.currentTimeMillis());
		req.setMethod(method);
		req.setClasses(classes);
		req.setArgs(params);
		req.setAccepted(false);
		req.setDone(false);
		LinkedList<Request> list = pendingRequests.get(host);
		if (list == null)
		{
			list = new LinkedList<Request>();
			pendingRequests.put(host, list);
		}
		synchronized (list)
		{
			list.addLast(req);
			list.notifyAll();
		}
		synchronized (req)
		{
			if (!req.isAccepted())
				req.wait(10000);
			if ( !req.isAccepted())
				throw new IOException ("Connection timeout");
			if (!req.isDone())
				req.wait();
			if (req.isDone())
			{
				if ( req.isSuccess())
					return req.getResponse();
				else
				{
//					log.info ("Received error "+req.getResponse().toString());
					throw  new InvocationTargetException( (Throwable) req.getResponse() );
				}
			}
			else
				throw new IOException ("Connection reset");
		}
		
	}

	/**
	 * Method to notify that a server has restarted.
	 * @param host
	 */
	public void reset (String host)
	{
		synchronized (activeRequests) {
			for (Iterator<Entry<Long, Request>> iterator = activeRequests.entrySet().iterator(); iterator.hasNext();)
			{
				Entry<Long, Request> entry = iterator.next();
				Request request = entry.getValue();
				if (request.getTarget().equals(host))
				{
					synchronized (request) {
						request.setSuccess(false);
						request.setResponse(new IOException("Connection reset"));
						request.setDone(true);
						request.notifyAll();
					}
				}
			}
		}
		
	}
	
	public static HubQueue instance() {
		return instance;
	}
	
	public String getServerForPath (String url) throws InternalErrorException, IOException {
		Server s = new RemoteServiceLocator().getServerService().findRemoteServerByUrl(url);
		if (s == null)
			return null;
		else
			return s.getName();
	}

	public void dump() {
		debug = true;
		log.info("------------------------------------");
		synchronized (pendingRequests) {
			log.info("Pending requests");
			for ( String server: pendingRequests.keySet()) {
				log.info(">> "+server);
				LinkedList<Request> l = pendingRequests.get(server);
				for (Request r: l) {
					log.info(">>   "+r.getSource()+" -> "+r.getTarget()+" : "+r.getUrl());
				}
			}
		}
		synchronized (activeRequests) {
			log.info("Active requests");
			for ( Request r: activeRequests.values()) {
				log.info(">>   "+r.getSource()+" -> "+r.getTarget()+" : "+r.getUrl());
			}
		}
		log.info("------------------------------------");
	}
}

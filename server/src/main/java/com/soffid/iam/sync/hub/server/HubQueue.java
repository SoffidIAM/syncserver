package com.soffid.iam.sync.hub.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	final static private HubQueue instance = new HubQueue();
	Log log = LogFactory.getLog(getClass());
	
	long requestId = System.currentTimeMillis();
	
	Map<String, Long> lastSeen = new Hashtable<>();
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
		lastSeen.put(server, System.currentTimeMillis());
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
			request.setAccept(System.currentTimeMillis());
			request.setAccepted(true);
			activeRequests.put(request.getId(), request);
			if (debug)
				log.info("Sent request "+request.getId()+" to "+server);
			return request;
		}
	}
	
	public void post ( String host, Long requestId, Object result, boolean success) throws InternalErrorException
	{
		if (debug) {
			log.info("Fetching response "+requestId);
		}
		Request r = activeRequests.get(requestId);
		if (r == null || r.isDone())
		{
			log.info("Request already "+requestId+" from "+host+" processed twice");
		}
		else {
			synchronized (r) {
				r.setSuccess(success);
				r.setResponse(result);
				r.setDone(true);
				r.notifyAll();
				activeRequests.remove(requestId);
			}
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
		boolean remove = false;
		try {
			synchronized (req)
			{
				if (!req.isAccepted())
					req.wait(10000);
				if ( !req.isAccepted()) {
					remove = true;
					throw new IOException ("Connection timeout");
				}
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
		} finally {
			if (remove) {
				synchronized (list) {
					list.remove(req);
				}
			}
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

	public void dump(BufferedWriter writer) throws IOException {
		if (writer != null)
			writer.write("-------------------------------------\n");
		synchronized (pendingRequests) {
			log.info("Pending requests");
			if (writer != null)
				writer.write("Pending requests\n");
			for ( String server: pendingRequests.keySet()) {
				log.info(">> "+server);
				if (writer != null)
					writer.write(">> "+server+"\n");
				LinkedList<Request> l = pendingRequests.get(server);
				for (Request r: l) {
					log.info(">>   "+r.getSource()+" -> "+r.getTarget()+" : "+r.getUrl());
					if (writer != null)
						writer.write(">>   ["+dateFormat.format(new Date(r.getStart()))+"] "+r.getSource()+" -> "+r.getTarget()+" : "+r.getUrl()+"\n");
				}
			}
		}
		synchronized (activeRequests) {
			log.info("Active requests");
			if (writer != null)
				writer.write("Active requests\n");
			for ( Request r: activeRequests.values()) {
				log.info(">>   "+r.getSource()+" -> "+r.getTarget()+" : "+r.getUrl());
				if (writer != null)
					writer.write(">>   ["+dateFormat.format(new Date(r.getStart()))+"] ["+dateFormat.format(new Date(r.getAccept()))+"] "+r.getSource()+" -> "+r.getTarget()+" : "+r.getUrl()+"\n");
			}
		}
		synchronized (lastSeen) {
			log.info("Last Seen");
			if (writer != null)
				writer.write("Last Seen\n");
			for ( String s: lastSeen.keySet()) {
				Object message = ">>   "+s+": "+dateFormat.format(new Date(lastSeen.get(s)));
				log.info(message);
				if (writer != null)
					writer.write(message+"\n");
			}
		}
		log.info("------------------------------------");
		if (writer != null)
			writer.write("------------------------------------\n");
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void setActive(String server, String active) {
		try {
			Set<Long> a = new HashSet<>(); 
			for (String s: active.split(",")) {
				if (s.length() > 0) {
					try {
						a.add( Long.parseLong(s) );
					} catch (Exception e) {
						
					}
				}
			}
	
			for (Request r: activeRequests.values()) {
				if (r.getTarget().equals(server) &&
						r.start < System.currentTimeMillis() - 2000) {
					log.info("Expiring request "+r.getId());
					post (server, r.getId(), new RemoteException("Connection reset"), false);
					return;
				}
			}
		} catch (Exception e) {
			// Map modified during iteration
		}
	}
}

package com.soffid.iam.sync.engine.pool;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import com.soffid.iam.config.Config;

public abstract class AbstractPool<S> implements Runnable {
	static LinkedList<WeakReference<AbstractPool<?>>> currentPools = new LinkedList<WeakReference<AbstractPool<?>>>();

	org.apache.commons.logging.Log log = LogFactory.getLog(getClass());
	
	private Thread theThread;
	private ThreadLocal<PoolElement<S>> current = new ThreadLocal<PoolElement<S>>();
	
	private boolean stop = false;
	int minSize = 0;
	int maxIdle = 99999;
	int minIdle = 0;
	int maxSize = 10;
	int maxUnusedTime = 120; // 2 minutes
	int maxUsedTime = 9200; // 2 hours
	int currentSize = 0;

	public void debug(String msg) { 
	}

	public int getMinSize() {
		return minSize;
	}


	public void setMinSize(int minSize) {
		this.minSize = minSize;
	}


	public int getMaxSize() {
		return maxSize;
	}


	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
	}


	public int getMaxUnusedTime() {
		return maxUnusedTime;
	}


	public void setMaxUnusedTime(int maxUnusedTime) {
		this.maxUnusedTime = maxUnusedTime;
	}


	public AbstractPool ()
	{
		theThread = new Thread(this);
		theThread.setName(getClass()+" Pool Manager Thread");
		theThread.start();
		currentPools.add(new WeakReference<AbstractPool<?>>(this));
	}
	
	protected void finalize () {
		for (Iterator<WeakReference<AbstractPool<?>>> it = currentPools.iterator(); it.hasNext();)
		{
			WeakReference<AbstractPool<?>> wr = it.next();
			if (wr.get() == this)
				it.remove();
		}
	}
	
	LinkedList<PoolElement<S>> freeList = new LinkedList<PoolElement<S>>();
	HashSet<PoolElement<S>> inUse = new HashSet<PoolElement<S>>();

	public void run() {
		try {
			// Wait for one second
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
		} 
		while ( ! stop )
		{
			try {
				evictUnusedElements();
				recoverLostElements ();
				adjustIdle();
				Thread.sleep(maxUnusedTime * 500);
			} catch (Throwable e) {
			}
		}
	}

	private void recoverLostElements ()
	{
		synchronized (inUse) {
			for (PoolElement<S> e: new LinkedList<PoolElement<S>>(inUse))
			{
				if (e.getBoundThread() != null && !e.getBoundThread().isAlive())
				{
					log.warn("Returning "+e.toString()+" to object pool. Creation stack trace:");
					for ( StackTraceElement s: e.getStackTrace())
					{
						String ln = (s.getLineNumber() < 0 ? "Unknown" : Integer.toString(s.getLineNumber()));
						log.warn("   at "+s.getClassName()+"("+ ln + ")");
					}
					returnToPool (e);
				}
			}
		}
	}
	
	private synchronized void returnToPool(PoolElement<S> e) {
		if (inUse.remove(e))
		{
			if (e.isExpired()) {
				debug("Removing expired connection "+e.getObject().toString());
				performCloseConnection(e);
			}
			else
				freeList.push(e);
		}
	}


	private synchronized void evictUnusedElements() {
		long now = System.currentTimeMillis();
		long lastAcceptable = now - maxUnusedTime * 1000;
		for (Iterator<PoolElement<S>> it = freeList.iterator();
				it.hasNext();)
		{
			PoolElement<S> element = it.next();
			if (element.getLastUse() < lastAcceptable && currentSize > minSize)
			{
				debug("Removing connection "+element.getObject().toString()+". Pool size("+currentSize+") > Minimumu size ("+minSize+")");
				it.remove();
				performCloseConnection(element);
			}
		}
	}
	
	private synchronized void adjustIdle() {
		while (freeList.size() < minIdle && freeList.size() + inUse.size() < maxSize || 
				freeList.size() + inUse.size() < minSize) {
			PoolElement<S> empty;
			try {
				empty = allocateConnection();
				debug("Created idle connection "+empty.getObject().toString()+". Free size("+freeList.size()+") < Minimum idle ("+minIdle+") or Minimum size ("+minSize+")");
				freeList.add(empty );
			} catch (Exception e) {
			}
		}
		while (freeList.size() > maxIdle && freeList.size() + inUse.size() > minSize) {
			PoolElement<S> element = freeList.pollLast();
			debug("Closed idle connection "+element.getObject().toString()+". Free size("+freeList.size()+") > Maximum idle ("+maxIdle+")");
			performCloseConnection(element);
		}
	}

	public void stop ()
	{
		stop  = true;
	}

	public S getConnection () throws Exception
	{
		PoolElement<S> c = current.get();
		if (c != null && c.getBoundThread() == Thread.currentThread() && c.getLocks() > 0)
		{
			c.setLocks(c.getLocks()+1);
			return c.getObject();
		}
			
		PoolElement<S> element = null;
		
		while (true) {
			PoolElement<S> e = null;
			synchronized (this) {
				if (!freeList.isEmpty())
					e = freeList.pop();
			}
			if (e == null) break;
			try {
				if ( isConnectionValid(e.getObject()))
				{
					element = e ;
					break;
				}
				else {
					debug("Closing invalid connection "+e.getObject().toString());
					performCloseConnection(e);
				}
			} catch (Exception ex) {
				debug("Closing invalid connection "+e.getObject().toString());
				performCloseConnection(e);
			}
		}
		if (element == null)
		{
			element = allocateConnection();
		}

		element.setBoundThread(Thread.currentThread());
		element.setLastUse(System.currentTimeMillis());
		element.setStackTrace(Thread.currentThread().getStackTrace());
		element.setLocks(1);

		synchronized(this) {
			inUse.add(element);
			current.set(element);
		}
		return element.getObject();
	}

	protected PoolElement<S> allocateConnection() throws Exception {
		PoolElement<S> element = null;
		synchronized(this) {
			if (currentSize >= maxSize)
				throw new Exception ("Cannot allocate connection. Reached limit: "+maxSize);
			currentSize ++;
		}
		// Raise privileges to create connections during scripts evaluation
		Object e = java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<Object>() {
			public Object run() {
				try {
					PoolElement<S> element = new PoolElement<S>(createConnection());
					debug("Created new connection "+element.getObject().toString());
					return element;
				} catch (Exception e) {
					return e;
				}
			}});
		if (e != null && e instanceof Exception) {
			synchronized(this) {currentSize --;}
			throw (Exception) e;
		}
		else
			element = (PoolElement<S>) e;
		element.setCreation(System.currentTimeMillis());
		return element;
	}

	
	public synchronized void returnConnection () 
	{
		PoolElement<S> c = current.get();
		if ( c == null)
		{
			return;
		}

		if (c.getLocks() > 1)
		{
			c.setLocks(c.getLocks()-1);
			return;
		}
		else
		{
			c.setLocks(0);
			current.remove();
			returnToPool(c);
		}
	}

	public synchronized void reconfigure ()
	{
		for (PoolElement<S> element: inUse)
		{
			element.setExpired(true);
		}

		for (PoolElement<S> element: freeList)
		{
			debug("Closing connection "+element.getObject().toString()+" due to reconfiguration");
			performCloseConnection(element);
		}
		freeList.clear();
	}


	private void performCloseConnection(PoolElement<S> element) {
		try 
		{
			currentSize --;
			closeConnection(element.getObject());
		} catch (Exception e) 
		{
			LogFactory.getLog(getClass()).info("Error clossing pool element "+element.toString());
		}
	}
	
	protected abstract S createConnection () throws Exception;
	protected abstract void closeConnection(S connection) throws Exception; 
	protected boolean isConnectionValid(S connection) throws Exception
	{
		return true;
	}
	
	public String getStatus ()
	{
		StringBuffer b = new StringBuffer();
		b.append("Pool ").append (toString()).append ('\n')
				.append("Size: ")
				.append(currentSize)
				.append ('\n')
				.append("Free: ")
				.append(freeList.size())
				.append('\n');
		synchronized(inUse)
		{
			int i = 1;
			for (PoolElement<S> element: inUse)
			{
				b.append ("\n#").append(i).append(" Thread: ").append (element.getBoundThread().getName())
					.append(" generated on ") .append (new  Date(element.getCreation()))
					.append (" last access on ") .append (new  Date(element.getLastUse()))
					.append (" locks: ") .append (element.getLocks())
					.append ('\n');
				for ( StackTraceElement ste: element.getStackTrace())
				{
					b.append(" . . . ")
						.append(ste.toString())
						.append('\n');
				}
			}
		}
		return b.toString();
	}
	
	public static List<AbstractPool<?>> getPools()
	{
		LinkedList<AbstractPool<?>> ll = new LinkedList<AbstractPool<?>> ();
		for (Iterator<WeakReference<AbstractPool<?>>> it = currentPools.iterator(); it.hasNext();)
		{
			WeakReference<AbstractPool<?>> wr = it.next();
			if (wr.get() != null)
				ll.add(wr.get());
		}
		return ll;
	}
	
	public void diag (org.slf4j.Logger log)
	{
		log.info("Pool "+toString()+" usage:");
		synchronized (inUse) {
			for (PoolElement<S> e: inUse)
			{
				log.info("In use by thread "+e.getBoundThread().getName()+" "+e.getLocks()+" locks. Last use: "+new Date(e.getLastUse()));
			}
		}
		synchronized (freeList) {
			for (PoolElement<S> e: freeList)
			{
				log.info("Free. Created by thread "+e.getBoundThread().getName());
			}
		}
	}

	public synchronized void diagConnection (org.slf4j.Logger log)
	{
		PoolElement<S> c = current.get();
		if ( c == null)
		{
			log.info("No connection bound");
		}
		else
		{
			log.info("Connection bound with "+c.getLocks());
		}
	}


	public int getMaxIdle() {
		return maxIdle;
	}


	public void setMaxIdle(int maxIdle) {
		this.maxIdle = maxIdle;
	}


	public int getMinIdle() {
		return minIdle;
	}


	public void setMinIdle(int minIdle) {
		this.minIdle = minIdle;
	}


	public int getNumberOfLockedConnections() {
		return inUse.size();
	}


	public int getNumberOfConnections() {
		return inUse.size()+freeList.size();
	}
}

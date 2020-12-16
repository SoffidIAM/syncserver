package com.soffid.iam.sync.engine.pool;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.LogFactory;
import org.jfree.util.Log;

public abstract class AbstractPool<S> implements Runnable {
	static LinkedList<WeakReference<AbstractPool<?>>> currentPools = new LinkedList<WeakReference<AbstractPool<?>>>();

	org.apache.commons.logging.Log log = LogFactory.getLog(getClass());
	
	private Thread theThread;
	private ThreadLocal<PoolElement<S>> current = new ThreadLocal<PoolElement<S>>();
	
	private boolean stop = false;
	int minSize = 0;
	int maxSize = 10;
	int maxUnusedTime = 120; // 2 minutes
	int maxUsedTime = 9200; // 2 hours
	int currentSize = 0;


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
		while ( ! stop )
		{
			try {
				evictUnusedElements();
				recoverLostElements ();
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
			if (e.isExpired())
				performCloseConnection(e);
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
				it.remove();
				performCloseConnection(element);
			}
		}
	}
	
	public void stop ()
	{
		stop  = true;
	}

	public synchronized S getConnection () throws Exception
	{
		PoolElement<S> c = current.get();
		if (c != null && c.getBoundThread() == Thread.currentThread() && c.getLocks() > 0)
		{
			c.setLocks(c.getLocks()+1);
			return c.getObject();
		}
			
		PoolElement<S> element = null;
		while (!freeList.isEmpty())
		{
			PoolElement<S> e = freeList.pop();
			try {
				if ( isConnectionValid(e.getObject()))
				{
					element = e ;
					break;
				}
				else
					performCloseConnection(e);
			} catch (Exception ex) {
				performCloseConnection(e);
			}
		}
		if (element == null)
		{
			if (currentSize >= maxSize)
				throw new Exception ("Cannot allocate connection. Reached limit: "+maxSize);
			element = new PoolElement<S>(createConnection());
			element.setCreation(System.currentTimeMillis());
			currentSize ++;
		}

		element.setBoundThread(Thread.currentThread());
		element.setLastUse(System.currentTimeMillis());
		element.setStackTrace(Thread.currentThread().getStackTrace());
		element.setLocks(1);
		
		inUse.add(element);
		current.set(element);
		
		return element.getObject();
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
			Log.info("Error clossing pool element "+element.toString());
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
}

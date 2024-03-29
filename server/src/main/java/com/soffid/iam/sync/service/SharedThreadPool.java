package com.soffid.iam.sync.service;

import java.util.Collection;
import java.util.LinkedList;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

public class SharedThreadPool implements Runnable {

	boolean init = false;
	Logger log  = Log.getLogger("SharedThreadPool");
	
	LinkedList<DispatcherHandler> handlers = new LinkedList<DispatcherHandler>();

	private int delay;
	private int handlersSize = 0;
	int startedThreads = 0;
	
	public void updateThreads (final Collection<? extends DispatcherHandler> dispatchers)
	{
		synchronized (handlers)
		{
			handlers = new LinkedList<DispatcherHandler>();
			for (DispatcherHandler d: dispatchers)
			{
				if (d.getSystem().getSharedDispatcher() != null &&
						d.getSystem().getSharedDispatcher().booleanValue())
				{
					handlers.add(d);
				}
			}
			handlersSize  = handlers.size();
		}
		if (! init && handlers.size() > 0)
		{
			initialize ();
		}
	}

	private void initialize() {
		String retry = ConfigurationCache.getMasterProperty("server.dispatcher.delay");
		if (retry == null)
		    delay = 10000;
		else
		    delay = Integer.decode(retry).intValue() * 1000;
		
		String threads = ConfigurationCache.getMasterProperty("soffid.server.sharedThreads");
		int threadNumber;
		try {
			threadNumber = Integer.parseInt(threads);
		} catch (Throwable t) {
			threadNumber = (4+handlersSize) / 5;
			if (threadNumber > 32)
				threadNumber = 32;
			if (threadNumber < 1)
				threadNumber = 1;
		}
		init = true;
		for (int i = startedThreads; i < threadNumber; i++)
		{
			Thread t = new Thread (this);
			t.setName("SharedThread"+ (i+1));
			t.start();
			startedThreads ++;
		}
	}

	public void run() {
		Thread currentThread = Thread.currentThread();
		String originalName = currentThread.getName();
		int ignores = 0;
		long firstIgnore = System.currentTimeMillis() + delay;
		while (true)
		{
			DispatcherHandler h = null;
			// handlers must be saved to a local variable to avoid a dispatcher to be twice due to 
			// a reconfiguration during loop execution
			LinkedList<DispatcherHandler> currentHandlers = handlers;
			try {
				synchronized (currentHandlers)
				{
					h = currentHandlers.pollFirst();
				}
				if (h == null)
				{
					currentThread.setName (originalName+ " - Idle");
					Thread.sleep(delay);
				}
				else
				{
					currentThread.setName (originalName+ " ["+h.getSystem().getTenant()+"\\"+h.getSystem().getName()+"]");
		        	Security.nestedLogin(h.getSystem().getTenant(), Config.getConfig().getHostName(), Security.ALL_PERMISSIONS);
		        	try {
						if ( !h.runStep() )
						{
							if (ignores == 0)
								firstIgnore = System.currentTimeMillis() + delay;
							ignores ++;
							if (ignores >= handlersSize)
							{
								long now = System.currentTimeMillis();
								if (now < firstIgnore)
								{
									try {
										Thread.sleep( firstIgnore - now );
									} catch (InterruptedException e ) {}
								}
								ignores = 0;
							}
						} else
							ignores = 0;
		        	} finally {
		        		Security.nestedLogoff();
		        	}
				}
			} catch (Throwable t) {
				log.warn("Unhandled error on SharedThreadPool", t);
			} finally {
				if (h != null)
				{
					synchronized (currentHandlers)
					{
						if (h.isActive())
							currentHandlers.addLast(h);
					}
				}
			}
		}
	}
	
	public SharedThreadPool ()
	{
	}
}

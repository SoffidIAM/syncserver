package com.soffid.iam.sync.engine;

import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.lowagie.text.pdf.hyphenation.TernaryTree.Iterator;

public class Watchdog extends Thread {

	private static Watchdog instance = null;
	
	protected Watchdog() {
		setName("Watchdog");
	}

	public static Watchdog instance () {
		synchronized (Watchdog.class)
		{
			if (instance == null)
			{
				instance = new Watchdog();
				instance.start();
			}
			return instance;
		}
	}
	
	class ThreadInfo {
		Thread thread;
		int interrupts;
		long timeout;
		public long start;
	}
	LinkedList<ThreadInfo> threads = new LinkedList<Watchdog.ThreadInfo>();
	
	public void interruptMe (Long timeout)
	{
		ThreadInfo ti = new ThreadInfo();
		ti.thread = Thread.currentThread();
		ti.interrupts = 0;
		ti.start = System.currentTimeMillis();
		if (timeout == null)
			ti.timeout = 0;
		else
			ti.timeout = ti.start + timeout;
		synchronized (threads)
		{
			int index = 0;
			for (java.util.Iterator<ThreadInfo> it = threads.iterator(); it.hasNext(); index++)
			{
				ThreadInfo old = it.next();
				if (old.timeout > ti.timeout)
				{
					break;
				}
			}
			threads.add(index, ti);
		}
	}

	public void dontDisturb ()
	{
		synchronized (threads)
		{
			for (java.util.Iterator<ThreadInfo> it = threads.iterator(); it.hasNext();)
			{
				ThreadInfo old = it.next();
				if (old.thread == Thread.currentThread())
				{
					it.remove();
					break;
				}
			}
		}
	}
	
	Log log = LogFactory.getLog(getClass());
	
	@Override
	public void run() {
		while (true)
		{
			try 
			{
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				
			}
			
			synchronized (threads)
			{
				try {
					int index = 0;
					for (java.util.Iterator<ThreadInfo> it = threads.iterator(); it.hasNext(); index++)
					{
						ThreadInfo ti = it.next();
						if (ti.timeout == 0)
						{
							// Nothing to do
						}
						else if (ti.timeout < System.currentTimeMillis())
						{
							if (! ti.thread.isAlive())
							{
								it.remove();
							}
							else
							{
								long seconds = (System.currentTimeMillis() - ti.start) / 1000;
								log.warn("Thread "+ti.thread.getName()+" TIMEOUT ("+seconds+" secs). Interrupting");
								ti.thread.interrupt();
								ti.interrupts ++ ;
								if (ti.interrupts > 5 && 
										System.currentTimeMillis() > ti.timeout * 2 - ti.start)
								{
									log.warn("Unable to stop thread "+ti.thread.getName()+". Rebooting");
									for ( StackTraceElement sd: ti.thread.getStackTrace() )
									{
										log.info (" "+sd.toString()+"\n");
									}
									System.exit(-1);
								}
							}
						}
						else
							break;
					}
				} catch (Throwable t) {
					log.warn("Error detected on watchdog" ,t);
				}
			}
		}
	}
	
	
}


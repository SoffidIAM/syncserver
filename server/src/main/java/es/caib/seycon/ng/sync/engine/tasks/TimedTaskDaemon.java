package es.caib.seycon.ng.sync.engine.tasks;

import java.util.Date;

public abstract class TimedTaskDaemon {
	private Date lastRun;
	private Date lastFinished;
	
	public TimedTaskDaemon() {
		super();
	}

	public abstract boolean mustRun ();
	
	public abstract void run () throws Exception;
	
	public void doIteration () throws Exception
	{
		if (mustRun () )
		{
			Date now = new Date();
			run ();
			lastRun = now;
			lastFinished = new Date( );
		}
	}

	public boolean hasElapsedFromLastFinish (long millis)
	{
		if (lastFinished == null)
			return true;
		else
			return System.currentTimeMillis() - lastFinished.getTime() > millis;  
	}

	public boolean hasElapsedFromLastRun (long millis)
	{
		if (lastRun == null)
			return true;
		else
			return System.currentTimeMillis() - lastRun.getTime() > millis;  
	}

}

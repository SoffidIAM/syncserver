package es.caib.seycon.ng.sync.engine;


public class Watchdog  {

	private static Watchdog instance = null;
	
	private com.soffid.iam.sync.engine.Watchdog delegate = com.soffid.iam.sync.engine.Watchdog.instance();
	
	public static Watchdog instance () {
		synchronized (Watchdog.class)
		{
			if (instance == null)
			{
				instance = new Watchdog();
			}
			return instance;
		}
	}
	
	public void interruptMe (Long timeout)
	{
		delegate.interruptMe(timeout);
	}

	public void dontDisturb ()
	{
		delegate.dontDisturb();
	}
}


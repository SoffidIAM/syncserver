package es.caib.seycon.ng.sync.engine.pool;

public class PoolElement<T> {
	private long creation = 0;
	private long lastUse = 0;
	private Thread boundThread;
	private boolean expired = false;
	private int locks = 0;
	StackTraceElement[] stack;
	
	public int getLocks() {
		return locks;
	}

	public void setLocks(int locks) {
		this.locks = locks;
	}

	public boolean isExpired() {
		return expired;
	}

	public void setExpired(boolean expired) {
		this.expired = expired;
	}

	public PoolElement (T object)
	{
		theObject = object;
		creation = System.currentTimeMillis();
		lastUse = creation;
	}
	
	public Thread getBoundThread() {
		return boundThread;
	}

	public void setBoundThread(Thread boundThread) {
		this.boundThread = boundThread;
	}

	public long getCreation() {
		return creation;
	}

	public void setCreation(long creation) {
		this.creation = creation;
	}

	public long getLastUse() {
		return lastUse;
	}

	public void setLastUse(long lastUse) {
		this.lastUse = lastUse;
	}

	protected T theObject;

	T getObject() {
		return theObject;
	}

	public void setStackTrace(StackTraceElement[] stackTrace) {
		this.stack = stackTrace;
	}

	public StackTraceElement[] getStackTrace() {
		return stack;
	}
}

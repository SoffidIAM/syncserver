package com.soffid.iam.sync.engine;

public class ReconcileThread extends Thread {
	ManualReconcileEngine engine;
	
	boolean finished = false;
	Throwable exception = null;
	
	@Override
	public void run() {
		try {
			engine.reconcile();
		} catch (Throwable e) {
			exception = e;
		}
		finished = true;
	}

	public ReconcileEngine2 getEngine() {
		return engine;
	}

	public void setEngine(ManualReconcileEngine engine) {
		this.engine = engine;
	}

	public boolean isFinished() {
		return finished;
	}

	public Throwable getException() {
		return exception;
	}

	
}

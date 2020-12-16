package com.soffid.iam.sync.engine;

import com.soffid.iam.sync.agent.AgentInterface;
import com.soffid.iam.sync.intf.AgentMgr;
import com.soffid.iam.sync.intf.ReconcileMgr2;

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
		} finally {
			ReconcileMgr2 agent = engine.getAgent();
			if (agent != null && agent instanceof AgentInterface)
				((AgentInterface)agent).close();
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

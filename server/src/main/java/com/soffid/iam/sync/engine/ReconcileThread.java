package com.soffid.iam.sync.engine;

import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.ProcessInstance;

import com.soffid.iam.reconcile.service.ReconcileService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.ReconcileEngine2;

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

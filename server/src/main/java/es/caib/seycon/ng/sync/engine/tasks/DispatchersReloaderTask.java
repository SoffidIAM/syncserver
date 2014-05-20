package es.caib.seycon.ng.sync.engine.tasks;

import es.caib.seycon.ng.sync.ServerServiceLocator;

public class DispatchersReloaderTask extends TimedTaskDaemon {

	public DispatchersReloaderTask() {
		super();
	}

	public boolean mustRun() {
		return hasElapsedFromLastFinish(60000); // Cada minuto
	}

	public void run() throws Exception {
	    ServerServiceLocator.instance().getTaskGenerator().updateAgents();
	}


}

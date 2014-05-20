package es.caib.seycon.ng.sync.engine.tasks;

import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;

public class AuthTokenRenewTask extends TimedTaskDaemon {

	public AuthTokenRenewTask() {
		super();
	}

	public boolean mustRun() {
		return hasElapsedFromLastFinish(30 * 60000); // Cada 30 minutos
	}

	public void run() throws Exception {
		if (!ConnectionPool.isThreadOffline())
			ServerServiceLocator.instance().getSecretConfigurationService().changeAuthToken();
	}


}

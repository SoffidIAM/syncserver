package es.caib.seycon.ng.sync.engine.tasks;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.sync.ServerServiceLocator;

public class ExpirePasswordsTask extends TimedTaskDaemon {

    Logger log  = LoggerFactory.getLogger(getClass());
	public ExpirePasswordsTask() {
		super();
	}

	public boolean mustRun() {
		Calendar c  = Calendar.getInstance();
//		if (c.get(Calendar.HOUR_OF_DAY) > 20 ||
//			c.get(Calendar.HOUR_OF_DAY) < 6)
		return hasElapsedFromLastFinish(600000); // Cada diez minutos
//		else
//			return false;
	}

	public void run() throws Exception {
	    log.info("Disabling unused passwords");
	    InternalPasswordService ips = ServerServiceLocator.instance().getInternalPasswordService();
         
	    ips.disableUntrustedPasswords();
	    ips.disableExpiredPasswords();
	}


}

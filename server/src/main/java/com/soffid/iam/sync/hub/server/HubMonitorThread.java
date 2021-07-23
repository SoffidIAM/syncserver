package com.soffid.iam.sync.hub.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class HubMonitorThread extends Thread {
	Log log = LogFactory.getLog(getClass());
	
	@Override
	public void run() {
		while (true) {
			try {
				HubQueue.instance().dump(null);
				Thread.sleep(5000);
			} catch (Exception e) {
				log.warn("Error in hub monitor thread", e);
			}
		}
	}

}

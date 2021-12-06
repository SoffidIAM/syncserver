package com.soffid.iam.sync.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.utils.Security;

public class InstanceRegistrationThread extends Thread {
	Log log = LogFactory.getLog(getClass());
	String name;
	public InstanceRegistrationThread(String name, String url) {
		super();
		this.name = name;
		this.url = url;
	}

	String url;
	
	@Override
	public void run() {
		setName("InstanceRegistrationThread");
		while (true) {
			try {
				sleep(30_000);
			} catch (InterruptedException e) {
			}
			register();
		}
	}

	@Override
	public synchronized void start() {
		register();
		setDaemon(true);
		super.start();
	}

	private void register() {
		try {
			ServerService server;
			if ( Security.isSyncServer()) {
				server = ServiceLocator.instance().getServerService();
			} else {
				server = new RemoteServiceLocator().getServerService();
			}
			server.registerServerInstance(name, url);
		} catch (Exception e) {
			
		}
	}

}

package com.soffid.iam.sync.agent.discovery;

import java.util.LinkedList;
import java.util.List;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Network;
import com.soffid.iam.sync.agent.Agent;
import com.soffid.iam.sync.intf.discovery.DiscoveryEvent;

public class NetworkDiscoveryAgent extends Agent implements com.soffid.iam.sync.intf.NetworkDiscoveryInterface {
	DiscoveryThread thread = null;
	@Override
	public void startDiscovery(Network network, List<Account> accounts) {
		if (thread != null) {
			thread.interrupt();
		}
		thread = new DiscoveryThread();
		thread.setNetwork(network);
		thread.setAccounts(accounts);
		thread.start();
	}

	@Override
	public List<DiscoveryEvent> getDiscoveryEvents() {
		LinkedList e = null;
		List<DiscoveryEvent> events = thread.getEvents();
		synchronized(events) {
			 e = new LinkedList<>(events);
			 events.clear();
		}
		return e;
	}

	@Override
	public boolean isFinished() {
		return !thread.isAlive();
	}

}

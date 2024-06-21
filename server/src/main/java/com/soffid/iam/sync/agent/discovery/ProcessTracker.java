package com.soffid.iam.sync.agent.discovery;

import java.net.InetAddress;
import java.util.List;

import com.soffid.iam.sync.intf.discovery.HostDiscoveryEvent;

public class ProcessTracker {

	public InetAddress address;
	public Process process;
	public long start;
	boolean finished;
	HostDiscoveryEvent event;
	public Thread thread;
}

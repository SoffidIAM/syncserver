package com.soffid.iam.sync.agent.discovery;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.HostPort;
import com.soffid.iam.api.Network;
import com.soffid.iam.sync.intf.discovery.DiscoveryEvent;
import com.soffid.iam.sync.intf.discovery.HostDiscoveryEvent;
import com.soffid.iam.sync.service.ServerService;

public class DiscoveryThread extends Thread {
	Network network;
	List<Account> accounts;
	ServerService serverService;
	Throwable error = null;
	List<ProcessTracker> tracker = new LinkedList<ProcessTracker>();
	List<DiscoveryEvent> events = new LinkedList<>();
	Log log = LogFactory.getLog(getClass());
	
	@Override
	public void run() {
		try {
			setName("Network discovery thread");
			InetAddress addr = InetAddress.getByName(network.getIp());
			InetAddress mask = InetAddress.getByName(network.getMask());
			
			byte[] netBytes = addr.getAddress();
			byte[] maskBytes = mask.getAddress();
			byte[] last = lastAddress(netBytes, maskBytes);
			byte[] next = Arrays.copyOf(netBytes, netBytes.length);
			increase (next);
			
			while (true) {
				while (tracker.size() < 20 && isLess(next,last)) {
					createProcess(next);
					increase(next);;
				}
				if (tracker.isEmpty()) break;
				checkFinishedProcesses();
			}
		} catch (Throwable th) {
			error = th;
		}
	}

	private void checkFinishedProcesses() throws IOException, InterruptedException {
		boolean any = false;
		for (java.util.Iterator<ProcessTracker> it = tracker.iterator(); it.hasNext();) {
			ProcessTracker t = it.next();
			if (!t.process.isAlive())
			{
				processFinishedProcess(t);
				any = true;
				it.remove();
			}
		}
		if (!any)
			sleep(5000);
	}

	private void processFinishedProcess(ProcessTracker t) throws IOException {
		Process process = t.process;
		HostDiscoveryEvent hd = new HostDiscoveryEvent();
		hd.setIp(t.address.getHostAddress());
		hd.setName(t.address.getCanonicalHostName());
		BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
		hd.setPorts( new LinkedList<HostPort>() );
		int status = 0;
		Pattern p  = Pattern.compile("^[0-9]+/(tcp|udp)");
		Pattern p2  = Pattern.compile("OS: ([^;]*)");
		Pattern p3  = Pattern.compile("Host: ([^;]*)");
		int serviceColumn = 0;
		int versionColumn = 0;
				
		for (String line = in.readLine(); line != null; line = in.readLine()) {
			if (status == 0) {
				if (line.startsWith("PORT")) {
					serviceColumn = line.indexOf("SERVICE");
					versionColumn = line.indexOf("VERSION");
					status = 1;
				}
			}
			if (status == 1) {
				Matcher m = p.matcher(line);
				if (m.find()) {
					String port = m.group();
					String service = line.substring(serviceColumn, line.length() > versionColumn ? versionColumn: line.length()).trim();
					if (line.length() > versionColumn)
					{
						String v = line.substring(versionColumn).trim();
						if (v.length() > 0) service = v;
					}
					HostPort hp = new HostPort();
					hp.setPort(port);
					hp.setDescription(service);
					hd.getPorts().add(hp );
				}
				else if (line.startsWith("Service Info: ")) {
					m = p2.matcher(line);
					if (m.find()) {
						String os = m.group(1);
						hd.setOs( os.equalsIgnoreCase("Windows")? "NTS" :
							os.equalsIgnoreCase("Linux") ? "LIN" : "ALT");
					}
					m = p3.matcher(line);
					if (m.find()) {
						hd.setName(m.group(1));
					}
					break;
				}
			}
		}
		synchronized(events) {
			if (status == 1)
				events.add(hd);
		}
	}

	private void createProcess(byte[] next) throws IOException {
		InetAddress addr = InetAddress.getByAddress(next);
		Process process = Runtime.getRuntime().exec(new String[] {"nmap", "-Pn", "-An", addr.getHostAddress()} );
		log.info("Starting discovery for ip address "+addr.getHostAddress());
		ProcessTracker t = new ProcessTracker();
		process.getOutputStream().close();
		t.address = addr;
		t.process = process;
		t.start = System.currentTimeMillis();
		tracker.add(t);
	}

	private boolean isLess(byte[] next, byte[] last) {
		for (int i = 0; i < last.length; i++) {
			int f = next[i];
			if (f < 0) f += 256;
			int g = last[i];
			if (g < 0) g += 256;
			if ( f < g ) return true;
			if ( f > g ) return false;
		}
		return false;
	}

	private byte[] lastAddress(byte[] netBytes, byte[] maskBytes) {
		byte[] last = Arrays.copyOf(netBytes, netBytes.length);
		for (int i = 0; i < last.length; i++) {
			last[i] |= ( ~ maskBytes[i]);
		}
		return last;
	}

	private void increase(byte[] next) {
		for (int i = next.length - 1; i >=0 ; i-- ) {
			next [i] ++;
			if (next [i] != 0) {
				break;
			}
		}
	}

	public Network getNetwork() {
		return network;
	}

	public void setNetwork(Network network) {
		this.network = network;
	}

	public List<Account> getAccounts() {
		return accounts;
	}

	public void setAccounts(List<Account> accounts) {
		this.accounts = accounts;
	}

	public ServerService getServerService() {
		return serverService;
	}

	public void setServerService(ServerService serverService) {
		this.serverService = serverService;
	}

	public Throwable getError() {
		return error;
	}

	public void setError(Throwable error) {
		this.error = error;
	}

	public List<DiscoveryEvent> getEvents() {
		return events;
	}

	public void setEvents(List<DiscoveryEvent> events) {
		this.events = events;
	}
	
}

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

import org.apache.commons.beanutils.PropertyUtils;
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
	List<String> ranges = null;
	List<ProcessTracker> tracker = new LinkedList<ProcessTracker>();
	List<DiscoveryEvent> events = new LinkedList<>();
	Log log = LogFactory.getLog(getClass());
	
	@Override
	public void run() {
		try {
			setName("Network discovery thread");
			InetAddress addr = InetAddress.getByName(network.getIp());
			InetAddress mask = InetAddress.getByName(network.getMask());
			
			try {
				ranges = (List<String>) PropertyUtils.getProperty(network, "discoveryRanges");
			} catch (Exception e) {
				// Ignore. Probably older console version
			}
			
			byte[] netBytes = addr.getAddress();
			byte[] maskBytes = mask.getAddress();
			byte[] last = lastAddress(netBytes, maskBytes);
			byte[] next = Arrays.copyOf(netBytes, netBytes.length);
			increase (next);
			
			while (true) {
				while (tracker.size() < 20 && isLess(next,last)) {
					if (isInRanges (next))
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

	private boolean isInRanges(byte[] next) {
		if (ranges == null || ranges.isEmpty())
			return true;
		for (String range: ranges) {
			if (range.contains( "-" )) {
				boolean matches = compareRange(next, range);
				if (matches) return true;
			}
			else if (range.contains("/")) {
				boolean matches = compareSubNet(next, range);
				if (matches) return true;
			}
			else {
				byte[] b = parse(range, next);
				boolean matches = compareRange(next, b, b);
				if (matches) return true;
			}
		}
		return false;
	}

	static final int masks [] = {0x00, 0x80, 0xc0, 0xe0, 0xf0, 0xf8, 0xfc, 0xfe, 0xff};  

	private boolean compareSubNet(byte[] next, String range) {
		String[] split = range.split(" */ *");
		byte[] from = parse(split[0], next);
		byte[] until = new byte[from.length];
		int bits = Integer.parseInt(split[1]);
		for (int i = 0; i < from.length; i++) {
			if (bits >= 8) {
				until[i] = from[i];
				bits -= 8;
			}
			else if (bits == 0) {
				until[i] = -1;
			}
			else 
			{
				int mask = masks[bits];
				int tail = mask ^ 255;
				until[i] = (byte) (from[i] & mask | tail) ;
				bits = 0;
			}
		}
		return compareRange(next, from, until);
	}

	protected boolean compareRange(byte[] next, String range) {
		String[] split = range.split(" *- *");
		byte[]from = parse(split[0], next);
		byte[]until = parse(split[1], next);
		return compareRange(next, from, until);
	}

	protected boolean compareRange(byte[] next, byte[] from, byte[] until) {
		for (int i = 0; i < next.length; i++) {
			if (i <= from.length && unsigned(from[i]) > unsigned(next[i])) {
				return false;
			}
			if (i <= until.length && unsigned(until[i]) < unsigned(next[i])) {
				return false;
			}
		}
		return true;
	}

	private int unsigned(byte b) {
		return (((int) b) + 256) % 256;
	}

	private byte[] parse(String string, byte[] next) {
		if (string.trim().isEmpty()) 
			return next;
		try {
			return InetAddress.getByName(string).getAddress();
		} catch (UnknownHostException e) {
			return next;
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
		Process process = Runtime.getRuntime().exec(new String[] {"nmap", "-Pn", "-An", "-p", "1-1024,1500-1600,5432,3306,1433", addr.getHostAddress()} );
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

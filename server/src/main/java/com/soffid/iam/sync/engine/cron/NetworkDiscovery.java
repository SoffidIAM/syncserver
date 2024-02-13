package com.soffid.iam.sync.engine.cron;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jfree.util.Log;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.DataType;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.HostPort;
import com.soffid.iam.api.Issue;
import com.soffid.iam.api.IssueHost;
import com.soffid.iam.api.MetadataScope;
import com.soffid.iam.api.Network;
import com.soffid.iam.api.PagedResult;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordDomain;
import com.soffid.iam.api.System;
import com.soffid.iam.api.UserDomain;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.Server;
import com.soffid.iam.service.AccountService;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.NetworkDiscoveryService;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.service.TaskHandler;
import com.soffid.iam.service.UserDomainService;
import com.soffid.iam.sync.agent.AgentInterface;
import com.soffid.iam.sync.agent.discovery.NetworkDiscoveryAgent;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.DispatcherHandlerImpl;
import com.soffid.iam.sync.engine.InterfaceWrapper;
import com.soffid.iam.sync.engine.ReconcileEngine2;
import com.soffid.iam.sync.engine.kerberos.KerberosManager;
import com.soffid.iam.sync.intf.ExtensibleObjectMgr;
import com.soffid.iam.sync.intf.NetworkDiscoveryInterface;
import com.soffid.iam.sync.intf.ReconcileMgr2;
import com.soffid.iam.sync.intf.ServiceMgr;
import com.soffid.iam.sync.intf.discovery.DiscoveryEvent;
import com.soffid.iam.sync.intf.discovery.HostDiscoveryEvent;
import com.soffid.iam.sync.intf.discovery.SystemDiscoveryEvent;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;

import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.exception.AccountAlreadyExistsException;
import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import oracle.net.aso.l;

public class NetworkDiscovery implements TaskHandler
{
	ScheduledTask task;
	NetworkService networkService = ServiceLocator.instance().getNetworkService();
	NetworkDiscoveryService discoveryService = ServiceLocator.instance().getNetworkDiscoveryService();
	DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
	AccountService accountService = ServiceLocator.instance().getAccountService();
	ServerService serverService = ServiceLocator.instance().getServerService();
	TaskGenerator taskGenerator = ServiceLocator.instance().getTaskGenerator();
	UserDomainService userDomainService = ServiceLocator.instance().getUserDomainService();
	AdditionalDataService metadataService = ServiceLocator.instance().getAdditionalDataService();
	
	private PrintWriter out;
	private Network network;
	private List<Account> discoveryAccounts;
	private List<Password> passwords;
	private String systemUrl;
	
	@Override
	public void run(PrintWriter out) throws Exception {
		this.out = out;
		PagedResult<Network> networks = networkService.findNetworkByTextAndJsonQuery(null, "id eq "+task.getParams(), null, null);
		if (networks.getTotalResults().intValue() != 1)
			throw new Exception("Cannot find network with id "+task.getParams());
		network = networks.getResources().get(0);
		
		System s = createSystem(network);
		DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
		handler.setSystem(s);
		out.println("Connecting to discovery server");
		Object agent = handler.connect(true, false);
		if ( agent instanceof com.soffid.iam.sync.intf.NetworkDiscoveryInterface) {
			doDiscovery ((com.soffid.iam.sync.intf.NetworkDiscoveryInterface) agent);
		} else {
			throw new InternalErrorException("The discovery agent doesnot implpment the right interface");
		}
	}

	private void doDiscovery(NetworkDiscoveryInterface agent) throws InterruptedException, InternalErrorException {
		agent.startDiscovery(network, new LinkedList<>());
		discoveryAccounts = discoveryService.findNetworkAccount(network);
		passwords = new LinkedList<>();
		for (Iterator<Account> it = discoveryAccounts.iterator(); it.hasNext();) {
			Account acc = it.next();
			Password p = serverService.getAccountPassword(acc.getName(), acc.getSystem());
			if (p == null)
				it.remove();
			else 
				passwords.add(p);
		}
		boolean exit;
		do {
			exit = agent.isFinished();
			List<DiscoveryEvent> events = agent.getDiscoveryEvents();
			if (events.isEmpty())
			{
				Thread.sleep(10000);
			} else {
				for (DiscoveryEvent event: events) 
				{
					processEvent (event);
				}
			}
		} while (!exit);
	}

	private void processEvent(DiscoveryEvent event) {
		try {
			if (event instanceof HostDiscoveryEvent)
				processHostEvent((HostDiscoveryEvent) event);
			if (event instanceof SystemDiscoveryEvent)
				processSystemEvent((SystemDiscoveryEvent) event);
		} catch (Exception e) {
			out.println("Error processing event "+event.toString());
			e.printStackTrace(out);
		}
	}

	private void processSystemEvent(SystemDiscoveryEvent event) throws InternalErrorException, AccountAlreadyExistsException, BadPasswordException {
	}

	private boolean isInternalUser(System s, SystemDiscoveryEvent event) {
		Account account = event.getAccount();
		if (account.getSystem().equals("SSO")) {
			return false;
		} else {
			return true;
		}
	}

	private void processHostEvent(HostDiscoveryEvent event) throws InternalErrorException {
		Host host = getHost(event);

		discoveryService.registerHostPorts(host, event.getPorts());
		out.println("Registered open ports for host "+host.getName());
		if ( discoveryService.findHostSystems(host).isEmpty()) {
			if (containsPort(event.getPorts(), "389/tcp", "Active Directory")) { // Kerberos
				createSystem(host, "AD", event.getPorts());
				host.setOs("NTS");
				networkService.update(host);
			} 
			else if (containsPort(event.getPorts(), "445/tcp") || containsPort(event.getPorts(), "3389/tcp")) {
				createSystem(host, "Windows", event.getPorts());
				host.setOs("NTS");
				networkService.update(host);
			} 
			if (containsPort(event.getPorts(), "22/tcp")) {
				createSystem(host, "Linux", event.getPorts());
				host.setOs("LIN");
				networkService.update(host);
			} 
		}
	}

	private Host getHost(HostDiscoveryEvent event) throws InternalErrorException {
		String hostName = getHostName(event);
		if (hostName != null) {
			Host h = networkService.findHostByName(hostName);
			if (h == null) {
				removeIpAddressFromOtherHosts(event);
				return registerNewHost(event, hostName);
			}
			else if (h.getIp() != null && h.getIp().equals(event.getIp()) ||
				h.getHostAlias() != null && h.getHostAlias().contains(event.getIp())) // Same name and address 
			{
				return h;
			}
			else
			{
				removeIpAddressFromOtherHosts(event);
				if (h.getIp() == null)
					h.setIp(event.getIp());
				else
					h.getHostAlias().add(event.getIp());
				networkService.update(h);
				return h;
			}
		} else {
			Host h = networkService.findHostByIp(event.getIp());
			if (h == null) {
				return registerNewHost(event, event.getName());
			}
			else
			{
				return h;
			}
		}
	}

	protected Host registerNewHost(HostDiscoveryEvent event, String hostName) throws InternalErrorException {
		Host h;
		h = new Host();
		h.setName(hostName);
		h.setIp(event.getIp());
		h.setDescription("Discovered host "+event.getName());
		h.setLastSeen(Calendar.getInstance());
		h.setNetworkCode(network.getName());
		h.setOs(event.getOs() == null  ? "ALT": event.getOs());
		h = networkService.create(h);
		out.println("Registered host "+h.getName());
		Issue issue = new Issue();
		issue.setType("discovered-host");
		IssueHost ih = new IssueHost();
		ih.setHostId(h.getId());
		ih.setHostName(h.getName());
		ih.setHostIp(h.getIp());
		issue.setHosts(Arrays.asList(ih));
		ServiceLocator.instance().getIssueService().createInternalIssue(issue);
		return h;
	}

	protected void removeIpAddressFromOtherHosts(HostDiscoveryEvent event) throws InternalErrorException {
		// Remove IP address from other hosts
		for (Host oldHost2: networkService.findHostByTextAndJsonQuery(null, "hostAlias eq '"+event.getIp()+"' or "
				+ "hostIP eq '"+event.getIp()+"'", null, null).getResources()) {
			// Remove IP
			oldHost2.getHostAlias().remove(event.getIp());
			if (event.getIp().equals( oldHost2.getIp()))
				oldHost2.setIp(null);
			// Disable system if it has no other IP address
			if (oldHost2.getIp() == null && oldHost2.getHostAlias().isEmpty()) {
				for (System system: discoveryService.findHostSystems(oldHost2)) {
					system.setUrl(null);
					dispatcherService.update(system);
				}
			}
			networkService.update(oldHost2);
		}
	}

	private String getHostName(HostDiscoveryEvent event) {
		for (int i = 0; i < discoveryAccounts.size(); i++) {
			Account account = discoveryAccounts.get(i);
			Password password = passwords.get(i);
			try {
				if (containsPort(event.getPorts(), "22/tcp")) {
					String host = getLinuxHostName(event.getIp(), account.getLoginName(), password);
					if (host != null)
						return host;
				}
				if (containsPort(event.getPorts(), "445/tcp") ) {
					String host = getWindowsHostName(event.getIp(), account.getLoginName(), password);
					if (host != null)
						return host;
				} 
			} catch (Exception e) {
				
			}
		}
		return null;
	}

	private String getLinuxHostName(String ip, String loginName, Password password) throws Exception {
		System s = new System();
		s.setManualAccountCreation(true);
		s.setReadOnly(true);
		s.setDescription(ip);
		s.setName(ip);
		s.setFullReconciliation(false);
		s.setSharedDispatcher(true);
		s.setTrusted(false);
		UserDomain ud = userDomainService.findUserDomainByName("DEFAULT");
		if (ud == null)
			ud  = userDomainService.findAllUserDomain().iterator().next();
		s.setUsersDomain(ud.getName());
		PasswordDomain pd = userDomainService.findPasswordDomainByName("DEFAULT");
		if (pd == null)
			pd = userDomainService.findAllPasswordDomain().iterator().next();
		s.setPasswordsDomain(pd.getName());
		s.setClassName("com.soffid.iam.sync.agent.SimpleSSHAgent");
		s.setParam0(loginName);
		s.setParam2(password.toString());
		s.setParam3(ip);
		s.setParam4("true"); // only passwords
		s.setParam6("UTF-8");
		s.setParam7("false"); // debug
		s.setId(0L);
		DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
		s.setUrl(systemUrl);
		handler.setSystem(s);
		Object obj = handler.connect(true, false);
		ExtensibleObjectMgr eo = InterfaceWrapper.getExtensibleObjectMgr(obj);
		if (eo != null) {
			for (Map<String, Object> l: eo.invoke("invoke", "hostname", new HashMap<>())) {
				String hn = (String) l.get("result");
				if (hn != null)
					return hn.replace("\n", "").toLowerCase().trim();
			}
		}
		return null;
	}

	private String getWindowsHostName(String ip, String loginName, Password password) throws Exception {
		System s = new System();
		s.setManualAccountCreation(true);
		s.setReadOnly(true);
		s.setDescription(ip);
		s.setName(ip);
		s.setFullReconciliation(false);
		s.setSharedDispatcher(true);
		s.setTrusted(false);
		UserDomain ud = userDomainService.findUserDomainByName("DEFAULT");
		if (ud == null)
			ud  = userDomainService.findAllUserDomain().iterator().next();
		s.setUsersDomain(ud.getName());
		PasswordDomain pd = userDomainService.findPasswordDomainByName("DEFAULT");
		if (pd == null)
			pd = userDomainService.findAllPasswordDomain().iterator().next();
		s.setPasswordsDomain(pd.getName());
		s.setClassName("com.soffid.iam.sync.agent.SimpleWindowsAgent");
		s.setParam0(loginName);
		s.setParam2(password.toString());
		s.setParam3(ip);
		s.setParam4("true"); // only passwords
		s.setParam7("false"); // debug
		s.setId(0L);
		DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
		s.setUrl(systemUrl);
		handler.setSystem(s);
		Object obj = handler.connect(true, false);
		ExtensibleObjectMgr eo = InterfaceWrapper.getExtensibleObjectMgr(obj);
		if (eo != null) {
			for (Map<String, Object> l: eo.invoke("hostname", "hostname", new HashMap<>())) {
				String hn = (String) l.get("result");
				if (hn != null)
					return hn;
			}
		}
		return null;
	}

	public boolean createSystem(Host host, String type, List<HostPort> ports) {
		boolean newSystem = false;
		DispatcherHandlerImpl handler = new DispatcherHandlerImpl();
		for (int i = 0; i < discoveryAccounts.size(); i++) {
			try {
				Account account = discoveryAccounts.get(i);
				Password password = passwords.get(i);
				System system = createSystemCandidate(host, type, ports, account.getLoginName(), password);
				System old = dispatcherService.findDispatcherByName(system.getName());
				if (old != null) {
					discoveryService.registerHostSystem(host, old);
				} else {
					out.println("Probing user "+account.getLoginName());
					system.setId(new Long(0));
					system.setUrl(systemUrl);
					handler.setSystem(system);
					Object obj = handler.connect(true, false);
					newSystem = true;
					ReconcileMgr2 agent = (ReconcileMgr2) InterfaceWrapper.getReconcileMgr2(obj); 
					if (agent == null) {
						out.println("Cannot get reconcile interface for "+host.getName());
					} else {
						agent.getAccountsList();
						out.println("Succesful system connectivity. Registering system: "+type);
						system.setId(null);
						system = dispatcherService.create(system);
						createMetadata(system, type);
						discoveryService.registerHostSystem(host, system);
						taskGenerator.updateAgents();
						DispatcherHandler dh = taskGenerator.getDispatcher(system.getName());
						try {
							obj = dh.connect(false, false);
							new ReconcileEngine2 (dh.getSystem(), InterfaceWrapper.getReconcileMgr2(obj), InterfaceWrapper.getServiceMgr(obj),  out).reconcile();
							if (obj instanceof AgentInterface)
								((AgentInterface) obj).close();
							else if (obj instanceof es.caib.seycon.ng.sync.agent.AgentInterface)
								((es.caib.seycon.ng.sync.agent.AgentInterface) obj).close();
						} catch (Exception e) {
							out.println("Warning: Error reconciling system "+dh.getSystem().getName());
							e.printStackTrace(out);
						}
						registerUser(account, password, system);
					}
				}

				Issue issue = new Issue();
				issue.setType("discovered-system");
				issue.setSystem(system.getName());
				IssueHost ih = new IssueHost();
				ih.setHostId(host.getId());
				ih.setHostName(host.getName());
				ih.setHostIp(host.getIp());
				issue.setHosts(Arrays.asList(ih));
				ServiceLocator.instance().getIssueService().createInternalIssue(issue);

				return newSystem;
			} catch (Exception e) {
				out.println("Failed to probe  "+host.getName()+": "+e.toString());
			}
		}
		return newSystem;
	}

	private void registerUser(Account account, Password password, System system) throws InternalErrorException, BadPasswordException {
		Account account2 = serverService.getAccountInfo(account.getLoginName(), system.getName());
		if (account2 != null) {
			accountService.setAccountPassword(account2, password);
		} 
	}

	private boolean containsPort(List<HostPort> ports, String portNumber) {
		return containsPort(ports, portNumber, null);
	}
	
	private boolean containsPort(List<HostPort> ports, String portNumber, String description) {
		for (HostPort port: ports) {
			if (port.getPort().endsWith(portNumber) && 
					(description == null || (port.getDescription() != null && port.getDescription().contains(description))))
				return true;
		}
		return false;
	}

	private System createSystem(Network network) throws InternalErrorException {
		System s = new System();
		s.setName("discovery-"+network.getName());
		s.setClassName(NetworkDiscoveryAgent.class.getCanonicalName());
		s.setUrl("local");
		for (Server server: ServiceLocator.instance().getDispatcherService().findTenantServers()) {
			if (server.getName().equals(network.getDiscoveryServer()))
				s.setUrl(server.getUrl());
		}
		systemUrl = s.getUrl();
		s.setId(0L);
		return s;
	}

	@Override
	public void setTask(ScheduledTask task) {
		this.task = task;
	}

	@Override
	public ScheduledTask getTask() {
		return task;
	}

	protected System createSystemCandidate(Host host, String type, List<HostPort> ports, String userName, Password password)
			throws Exception {
		System s = new System();
		s.setManualAccountCreation(true);
		s.setReadOnly(false);
		s.setDescription(host.getDescription());
		s.setName(host.getName());
		s.setFullReconciliation(true);
		s.setSharedDispatcher(true);
		s.setTrusted(false);
		s.setUsage("PAM");
		UserDomain ud = userDomainService.findUserDomainByName("DEFAULT");
		if (ud == null)
			ud  = userDomainService.findAllUserDomain().iterator().next();
		s.setUsersDomain(ud.getName());
		PasswordDomain pd = userDomainService.findPasswordDomainByName("DEFAULT");
		if (pd == null)
			pd = userDomainService.findAllPasswordDomain().iterator().next();
		s.setPasswordsDomain(pd.getName());
		if (type.equalsIgnoreCase("linux")) {
			s.setClassName("com.soffid.iam.sync.agent.SimpleSSHAgent");
			s.setParam0(userName);
			s.setParam2(password.toString());
			s.setParam3(host.getIp());
			s.setParam4("true"); // only passwords
			s.setParam6("UTF-8");
			s.setParam7("false"); // debug
			return s;
		} else if (type.equalsIgnoreCase("windows")) {
			s.setClassName("com.soffid.iam.sync.agent.SimpleWindowsAgent");
			s.setParam0(userName);
			s.setParam2(password.toString());
			s.setParam3(host.getIp());
			s.setParam4("true"); // only passwords
			s.setParam7("false"); // debug
			return s;
		} else if (type.equalsIgnoreCase("AD")) {
			String domainName = null;
			String shortName = null;
			for (HostPort port: ports) {
				if (port.getPort().equals("389/tcp")) {
					int i = port.getDescription().indexOf("Domain: ");
					if (i > 0) {
						String d = port.getDescription().substring(i + 8);
						i = d.indexOf(",");
						if (i > 0)
							domainName = d.substring(0, i);
						if (domainName.endsWith("0."))
							domainName = domainName.substring(0, domainName.length()-2);
						if (domainName.endsWith("."))
							domainName = domainName.substring(0, domainName.length()-1);
					}
				}
				if (port.getPort().equals("445/tcp")) {
					int i = port.getDescription().indexOf("workgroup: ");
					if (i > 0) {
						String d = port.getDescription().substring(i + 11);
						i = d.indexOf(")");
						if (i > 0)
							shortName = d.substring(0, i);
					}
				}
			}			
			if (domainName == null)
				throw new InternalErrorException("Unknown domain for "+host.getName());
			if (shortName == null)
			{
				int i = domainName.indexOf(".");
				shortName = i > 0 ? domainName.substring(0, i): domainName;
			}
			
			String name = new KerberosManager().getDomainsToSystemMap().get(domainName);
			if (name == null)
				s.setName("AD "+domainName.toLowerCase());
			else
				s.setName(name);
			s.setClassName("com.soffid.iam.sync.agent2.CustomizableActiveDirectoryAgent");
			s.setParam0(host.getIp());
			try {
				s.setParam0(InetAddress.getByName(domainName).getHostName());
			} catch (Exception e) {
				// Ignore. Use IP address
			}
			s.setParam1(translateToLdap(domainName));
			if (userName.toLowerCase().startsWith("cn=") || userName.contains("\\") || userName.contains("@"))
				s.setParam2(userName);
			else
				s.setParam2(shortName+"\\"+userName);
			s.setParam3(password.toString());
			s.setParam4("false"); // multidomain
			s.setParam5("false"); // create OUs
			s.setParam6(""); // Exclude domains
			s.setParam7("false"); // Enable debug
			s.setParam8("true"); // Trust certs
			s.setParam9("false"); // Follow referrals
			return s;
		} else {
			throw new InternalErrorException("Unknown system type ",type);
		}
	}

	private String translateToLdap(String domainName) {
		return "dc="+domainName.replace(".", ",dc=");
	}

	protected void createMetadata(System host, String type)
			throws Exception {
		if ("Linux".equalsIgnoreCase(type)) {
			String agent = host.getName();
			DataType dt;
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("uid");
			dt.setLabel("uid");
			dt.setOrder(new Long(1));
			dt.setType(TypeEnumeration.NUMBER_TYPE);
			metadataService.create(dt);
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("gid");
			dt.setLabel("gid");
			dt.setOrder(new Long(2));
			dt.setType(TypeEnumeration.NUMBER_TYPE);
			metadataService.create(dt);
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("home");
			dt.setLabel("Home dir");
			dt.setOrder(new Long(3));
			dt.setType(TypeEnumeration.STRING_TYPE);
			metadataService.create(dt);
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("shell");
			dt.setLabel("Shell");
			dt.setOrder(new Long(4));
			dt.setType(TypeEnumeration.STRING_TYPE);
			metadataService.create(dt);
		}
		if ("Windows".equalsIgnoreCase(type)) {
			String agent = host.getName();
			DataType dt;
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("rid");
			dt.setLabel("rid");
			dt.setOrder(new Long(1));
			dt.setType(TypeEnumeration.NUMBER_TYPE);
			metadataService.create(dt);
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("gid");
			dt.setLabel("gid");
			dt.setOrder(new Long(2));
			dt.setType(TypeEnumeration.NUMBER_TYPE);
			metadataService.create(dt);
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("home");
			dt.setLabel("Home dir");
			dt.setOrder(new Long(3));
			dt.setType(TypeEnumeration.STRING_TYPE);
			metadataService.create(dt);
			dt = new DataType();
			dt.setScope(MetadataScope.ACCOUNT);
			dt.setSystemName(agent);
			dt.setName("comments");
			dt.setLabel("Comments");
			dt.setOrder(new Long(4));
			dt.setType(TypeEnumeration.STRING_TYPE);
			metadataService.create(dt);
		}
		if ("AD".equalsIgnoreCase(type)) {
			dispatcherService.setDefaultMappingsByDispatcher(host.getId());
		}
	}
}

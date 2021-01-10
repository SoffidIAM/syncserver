package com.soffid.iam.sync.tools;

import java.io.Console;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Enumeration;

import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.Tenant;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.identity.IdentityGeneratorBean;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.service.ApplicationBootService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.TenantService;
import com.soffid.iam.ssl.ConnectionFactory;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.engine.log.LogConfigurator;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.utils.Security;
import com.soffid.tools.db.persistence.XmlReader;
import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Database;
import com.soffid.tools.db.schema.Table;
import com.soffid.tools.db.updater.DBUpdater;
import com.soffid.tools.db.updater.MsSqlServerUpdater;
import com.soffid.tools.db.updater.MySqlUpdater;
import com.soffid.tools.db.updater.OracleUpdater;
import com.soffid.tools.db.updater.PostgresqlUpdater;

import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class Configure {
	static org.apache.commons.logging.Log log = LogFactory.getLog(Configure.class);

	public static void main(String args[]) throws Exception {
		LogConfigurator.configureMinimalLogging();
		Security.onSyncServer();
//        Log.setLog(new SeyconLog());

		try {
			if (args.length == 0) {
				configurationWizard();
			}
			else if ("--help".equals(args[0]) || "-?".equals(args[0])) {
				usage();
				System.exit(1);
			}
			else if ("-main".equals(args[0])) {
				parseMainParameters(args);
			} else if ("-renewCertificates".equals(args[0])) {
				parseRenewParameters(args);
			} else {
				parseSecondParameters(args);
			}
			log.info("Configuration successfully done.");
			new KubernetesConfig().save();
			System.exit(0);
		} catch (CertificateEnrollDenied e) {
			System.err.println("Your certificate request has been denied.");
		} catch (CertificateEnrollWaitingForAproval e) {
			System.err.println("Your certificate is pending for administrator aproval.");
		} catch (InternalErrorException e) {
			System.err.println("Internal error: " + e.toString());
			e.printStackTrace();
		}
		System.exit(1);

	}

	private static void configurationWizard() throws Exception {
		System.out.println("Soffid Sync server configuration wizard.");
		Console c = java.lang.System.console();
		if (c == null) {
			System.exit(0);
		}
		
		Config config = Config.getConfig();
		
		CertificateServer cs = new CertificateServer();
		if (cs.hasServerKey()) {
			System.out.println ("The system is already configured");
			System.exit(1);
		} else if (config.getRequestId() != null) {
			System.out.println("Trying to complete pending configuration request");
			secondServerWizard(c, config);

		} else {
			System.out.println("Configuring sync server.");
			String first;
			do {first = c.readLine("Is this the first sync server in the network (y/n)? ").trim().toLowerCase();} while ( !first.startsWith("y") && !first.startsWith("n") );
			
			if (first.startsWith("y")) {
				firstServerWizard (c, config);
			} else {
				secondServerWizard(c, config);

			}
			
		}
	}

	public static void firstServerWizard(Console c, Config config)
			throws Exception {
		
		String url = "";
		String dbUser = "";
		String dbPassword = ""; 
		String hostName0 = InetAddress.getLocalHost().getHostName();
		String hostName;
		String port;
		boolean repeat = false;
		do {
			repeat = false;
			do { url = c.readLine("Database URL (jdbc:....): ").trim();} while (url.isEmpty());
			do { dbUser = c.readLine("Database user: ").trim();} while (dbUser.isEmpty());
			do {dbPassword = new String (c.readPassword("Password: ")).trim(); } while (dbPassword.isEmpty());
			hostName = c.readLine("This server host name [%s]: ", hostName0).trim().toLowerCase();
			if (hostName.isEmpty()) hostName = hostName0;
			port = c.readLine("Port to listen to [1760]: ").trim(); 
			if (port.isEmpty()) port = "1760";

			try {
				System.out.println("Connecting to database "+url+" ...");
				configureFirstServer(config, false, config.getHostName(), hostName, url, dbUser, port, new Password(dbPassword));
			} catch (Exception e) {
				e.printStackTrace();
				repeat = true;
			}
		} while (repeat);
	}

	public static void secondServerWizard(Console c, Config config)
			throws UnknownHostException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, InternalErrorException, UnrecoverableKeyException, NoSuchProviderException,
			InvalidKeyException, SignatureException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied,
			KeyManagementException, UnknownUserException {
		boolean repeat;
		do {
			repeat = false;
			String remote;
			do {remote = c.readLine("Connect to a cloud service (y/n)? Enter 'n' to connect to an on-premise service: ").trim().toLowerCase().substring(0,1);} while ( !remote.startsWith("y") && !remote.startsWith("n") );
			String serverUrl;
			do {serverUrl = c.readLine("Server URL: ").trim(); } while (serverUrl.isEmpty());
			String adminTenant ;
			adminTenant = c.readLine("Tenant: [master] ").trim();
			if (adminTenant.isEmpty()) adminTenant = "master";
			String adminUser;
			do { adminUser = c.readLine("User: ").trim();} while (adminUser.isEmpty());
			String adminPassword;
			do {adminPassword = new String (c.readPassword("Password: ")).trim(); } while (adminPassword.isEmpty());
			String hostName0 = InetAddress.getLocalHost().getHostName();
			String hostName = c.readLine("This server host name [%s]: ", hostName0).trim().toLowerCase();
			if (hostName.isEmpty()) hostName = hostName0;
			String port;
			port = c.readLine("Port to listen to [1760]: ").trim(); 
			if (port.isEmpty()) port = "1760";

			try {
				configureSecondServer(config, false, adminTenant, adminUser, new Password(adminPassword), serverUrl, null, remote.startsWith("y"),
					hostName, port);
			} catch (Exception e) {
				e.printStackTrace();
				repeat = true;
			}
		} while (repeat);
	}

	public static void usage() {
		System.out.println("Parameters:");
		System.out.println("  -main [-force] -hostname .. [-port ...] -dbuser .. -dbpass .. -dburl ..");
		System.out.println("  -hostname [-force] ..  [-port ...] -server .. -tenant .. -user .. -pass ..");
		System.out.println("  -remote  -hostname [-force] .. -server .. -tenant .. -user .. -pass ..");
		System.out.println("  -renewCertificates [-force]");
	}

	private static void parseSecondParameters(String[] args) throws IOException, InvalidKeyException,
			UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, NoSuchProviderException,
			CertificateException, IllegalStateException, SignatureException, InternalErrorException,
			UnknownUserException, KeyManagementException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied, JSONException {
		Config config = Config.getConfig();
		boolean force = false;
		String adminTenant = "master";
		String adminUser = "";
		Password adminPassword = null;
		String serverUrl = "";
		String adminDomain = null;
		boolean remote = false;
		String hostName = null;
		String port = "1760";
		int i;
		for (i = 0; i < args.length - 1; i++) {
			if ("-remote".equals(args[i]))
				remote = true;
			else if ("-hostname".equals(args[i]))
				hostName = (args[++i]);
			else if ("-port".equals(args[i]))
				port = (args[++i]);
			else if ("-tenant".equals(args[i]))
				adminTenant = args[++i];
			else if ("-user".equals(args[i]))
				adminUser = (args[++i]);
			else if ("-pass".equals(args[i]))
				adminPassword = (new Password(args[++i]));
			else if ("-server".equals(args[i]))
				serverUrl = args[++i];
			else if ("-force".equals(args[i]))
				force = true;
			else
				throw new RuntimeException("Unknown parameter " + args[i]);
		}
		if (i < args.length)
			throw new RuntimeException("Unknown parameter " + args[i]);

		if (adminPassword == null) {
			char pass[] = System.console().readPassword("%s's password: ", adminUser);
			adminPassword = new Password(new String(pass));
		}

		configureSecondServer(config, force, adminTenant, adminUser, adminPassword, serverUrl, adminDomain, remote,
				hostName, port);

	}

	public static void configureSecondServer(Config config, boolean force, String adminTenant, String adminUser,
			Password adminPassword, String serverUrl, String adminDomain, boolean remote, String hostName, String port)
			throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, InternalErrorException, UnrecoverableKeyException, NoSuchProviderException,
			InvalidKeyException, SignatureException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied,
			KeyManagementException, UnknownUserException, IllegalStateException, JSONException {
		if (hostName != null)
			config.setHostName(remote ? adminTenant + "_" + hostName : hostName);
		if (port != null)
			config.setPort(port);

		try {
			/*
			 * // Configurar servidor java.io.Console cons = System.console(); if (cons !=
			 * null) { String hostname = java.net.InetAddress.getLocalHost().getHostName();
			 * config.setHostName( read (cons, "Hostname", config.getHostName(), hostname)
			 * ); adminUser = read (cons, "Admin user", adminUser, ""); char[] passwd;
			 * passwd = cons.readPassword("%s [%s]: ", "Admin Password", adminPassword); if
			 * (passwd.length > 0) { adminPassword = new Password (new String(passwd)); }
			 * serverUrl = read (cons, "Server URL", serverUrl, ""); }
			 */
			config.setRole(remote ? "remote" : "agent");
			CertificateServer cs = new CertificateServer();
			if (force || !cs.hasServerKey())
				cs.obtainCertificate(serverUrl, adminTenant, adminUser, adminPassword, null, remote);
			else {
				System.out.println(
						"This node was already configured. To regenerate security keys, you should remove conf directory to reconfigure.");
				RemoteServiceLocator rsl = new RemoteServiceLocator(serverUrl);
				ServerService serverService = rsl.getServerService();
				config.setServerList(serverUrl);
				config.setServerService(serverService);
				config.updateFromServer();
			}
			new KubernetesConfig().save();
		} catch (NoClassDefFoundError e) {
			System.out.println("Warning: JAVA 6 required");
		} catch (NoSuchMethodError e) {
			System.out.println("Warning: JAVA 6 required");
		}

		configureAgent(adminTenant, adminUser, adminPassword, adminDomain, serverUrl, remote);
		if (port != null)
			config.setPort(port);
	}

	private static void parseRenewParameters(String[] args) throws Exception {
		ServerServiceLocator.instance().getApplicationBootService();
		Security.onSyncServer();
		int i;
		boolean force = false;
		for (i = 1; i < args.length - 1; i++) {
			if ("-force".equals(args[i]))
				force = true;
			else
				throw new RuntimeException("Unknown parameter " + args[i]);
		}
		if (i < args.length)
			throw new RuntimeException("Unknown parameter " + args[i]);

		CertificateServer cs = new CertificateServer();
		cs.regenerateCertificates(force);
	}

	private static void parseMainParameters(String[] args) throws Exception {
		Config config = Config.getConfig();
		boolean force = false;
		String oldHostName = config.getHostName();
		String hostName = config.getHostName();
		String db = config.getDB();
		String dbuser = config.getDbUser();
		String port = config.getPort();
		int i;
		Password password = null;
		for (i = 1; i < args.length; i++) {
			if ("-hostname".equals(args[i]))
				hostName = args[++i];
			else if ("-dbuser".equals(args[i]))
				dbuser = args[++i];
			else if ("-port".equals(args[i]))
				port = args[++i];
			else if ("-dbpass".equals(args[i]))
				password = new Password(args[++i]);
			else if ("-dburl".equals(args[i]))
				db = args[++i];
			else if ("-force".equals(args[i]))
				force = true;
			else
				throw new RuntimeException("Unknown parameter " + args[i]);
		}
		if (i < args.length)
			throw new RuntimeException("Unknown parameter " + args[i]);

		if (db == null)
			throw new RuntimeException("Missing -dburl parameter");
		if (password == null) {
			if (System.console() == null)
				throw new RuntimeException("Password required");
			char pass[] = System.console().readPassword("%s's password: ", config.getDbUser());
			password = new Password(new String(pass));
		}
		configureFirstServer(config, force, oldHostName, hostName, db, dbuser, port, password);
	}

	public static void configureFirstServer(Config config, boolean force, String oldHostName, String hostName,
			String db, String dbuser, String port, Password password)
			throws IOException, Exception, InternalErrorException, FileNotFoundException, RemoteException,
			KeyStoreException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException,
			InvalidKeyException, SignatureException, UnrecoverableKeyException, KeyManagementException {
		// Configurar servidor
		config.setRole("server");
		config.setPassword(password);
		config.setDB(db);
		config.setDbUser(dbuser);
		config.setPort(port);

		// Create database
		updateDatabase();

		// Verificar configuracio
		ServerService service = ServerServiceLocator.instance().getServerService();
		config.setServerService(service);

		// Execute database intialization procedure
		com.soffid.iam.bpm.config.Configuration.configureForServer();
		ApplicationBootService bootService = ServerServiceLocator.instance().getApplicationBootService();
		bootService.consoleBoot();

		// Configuration has been overwritten by consoleBoot
		config = Config.getConfig();
		config.setServerService(service);
		config.reload();

		DispatcherService dispatcherSvc = ServerServiceLocator.instance().getDispatcherService();
		Collection<Server> servers = dispatcherSvc.findAllServers();
		Server server = null;
		if (!servers.isEmpty()) {
			if (!force)
				throw new InternalErrorException(
						"This server cannot be configured as the main server as long as there are some servers created at Soffid console.\nPlease remove them to proceed.");
			for (Server server2 : servers) {
				if (server2.getName().equals(oldHostName))
					server = server2;
			}
			if (server == null)
				throw new InternalErrorException("Unable to find server configuration for " + oldHostName);
			server.setName(config.getHostName());
			dispatcherSvc.update(server);
		} else {
			server = new Server();
			server.setName(hostName);
			server.setUrl("https://" + hostName + ":" + config.getPort() + "/");
			server.setType(ServerType.MASTERSERVER);
			server.setUseMasterDatabase(true);
			server = dispatcherSvc.create(server);
			TenantService tenantSvc = ServiceLocator.instance().getTenantService();
			Tenant t = tenantSvc.getMasterTenant();
			tenantSvc.addTenantServer(t, server.getName());
		}
		config.setHostName(hostName);
		CertificateServer s = new CertificateServer();

		log.info("Is server: " + config.isServer());
		log.info("Role: " + config.getRole());
		log.info("Server service: " + config.getServerService());
		log.info("Server list: " + config.getServerList());

		log.info("Generating security keys");
		s.createRoot();
	}

	private static void configureAgent(String adminTenant, String adminUser, Password adminPassword, String adminDomain,
			String serverUrl, boolean remote) throws NoSuchAlgorithmException, NoSuchProviderException,
			KeyStoreException, FileNotFoundException, CertificateException, IOException, InternalErrorException,
			InvalidKeyException, UnrecoverableKeyException, IllegalStateException, SignatureException,
			UnknownUserException, KeyManagementException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied, JSONException {
		Config config = Config.getConfig();

		CertificateServer cs = new CertificateServer();

		if (!cs.hasServerKey()) {
			cs.obtainCertificate(serverUrl, adminTenant, adminUser, adminPassword, adminDomain, remote);
			ConnectionFactory.reloadKeys();
		}
		RemoteServiceLocator rsl = new RemoteServiceLocator(serverUrl);
		ServerService serverService = rsl.getServerService();
		config.setServerList(serverUrl);
		config.setServerService(serverService);
		config.updateFromServer();
	}

	protected static void updateDatabase() throws Exception {
		Database db = new Database();
		XmlReader reader = new XmlReader();
		parseResources(db, reader, "console-ddl.xml");
		parseResources(db, reader, "core-ddl.xml");

		com.soffid.iam.sync.engine.db.DataSource ds = new com.soffid.iam.sync.engine.db.DataSource();
		Connection conn = ds.getConnection();

		String type = Config.getConfig().getDB(); // $NON-NLS-1$
		DBUpdater updater;
		if (type.contains(":mysql:") || type.contains(":mariadb:")) //$NON-NLS-2$
		{
			updater = new MySqlUpdater();
		} else if (type.contains(":oracle:")) { //$NON-NLS-1$
			updater = new OracleUpdater();
		} else if (type.contains(":sqlserver:")) {
			updater = new MsSqlServerUpdater();
		} else if (type.contains(":postgresql:")) {
			updater = new PostgresqlUpdater();
		} else {
			throw new RuntimeException("Unable to get dialect for database type [" + type + "]"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		updater.setLog(System.out);
		updater.setIgnoreFailures(true);
		updater.update(conn, db);

		IdentityGeneratorBean identityGenerator = (IdentityGeneratorBean) ServerServiceLocator.instance()
				.getService("identity-generator");
		if (!identityGenerator.isSequenceStarted()) {
			long l = getMaxIdentifier(conn, db);
			identityGenerator.initialize(l, 100, 1);
		}

		conn.close();
	}

	private static long getMaxIdentifier(Connection connection, Database db) throws SQLException {
		long l = 1;
		for (Table t : db.tables) {
			for (Column c : t.columns) {
				if (c.primaryKey) {
					PreparedStatement st = connection.prepareStatement("SELECT MAX(" + c.name + ") FROM " + t.name);
					try {
						ResultSet rs = st.executeQuery();
						try {
							if (rs.next()) {
								long l2 = rs.getLong(1);
								if (l2 >= l)
									l = l2 + 1;
							}
						} finally {
							rs.close();
						}
					} finally {
						st.close();
					}
				}
			}
		}
		return l;
	}

	private static void parseResources(Database db, XmlReader reader, String path) throws IOException, Exception {
		Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
		while (resources.hasMoreElements()) {
			reader.parse(db, resources.nextElement().openStream());
		}
	}	
}

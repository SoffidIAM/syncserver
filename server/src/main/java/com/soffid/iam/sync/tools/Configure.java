package com.soffid.iam.sync.tools;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;

import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.Tenant;
import com.soffid.iam.api.User;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.identity.IdentityGeneratorBean;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.service.ApplicationBootService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.TenantService;
import com.soffid.iam.ssl.ConnectionFactory;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.engine.log.LogConfigurator;
import com.soffid.iam.sync.service.SecretStoreService;
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

		if (args.length == 0) {
      usage();
			System.exit(1);
		}

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
			} else if ("-reencodeSecrets".equals(args[0])) {
				reencodeSecrets();
			} else if ("-exportCA".equals(args[0])) {
				exportCA(args);
			} else if ("-importCA".equals(args[0])) {
				importCA(args);
			} else if ("-generateCert".equals(args[0])) {
				generateCert(args);
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
			boolean wrong;
			do {
				wrong = false;
				hostName = c.readLine("This server host name [%s]: ", hostName0).trim().toLowerCase();
				if (hostName.isEmpty()) hostName = hostName0;
				try {
					InetAddress addr = InetAddress.getByName(hostName);
				} catch (Exception e) {
					c.printf("The address %s is not valid", hostName);
					wrong = true;
				}
			} while (wrong);
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
			String hostName;
			boolean wrong;
			do {
				wrong = false;
				hostName = c.readLine("This server host name [%s]: ", hostName0).trim().toLowerCase();
				if (hostName.isEmpty()) hostName = hostName0;
				try {
					InetAddress addr = InetAddress.getByName(hostName);
				} catch (Exception e) {
					c.printf("The address %s is not valid", hostName);
					wrong = true;
				}
			} while (wrong);
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
			System.out.println("  -exportCA -out [filename] [-alias [name]] [-pass [pass]]");
			System.out.println("  -importCA -in [filename] [-alias [name]] [-pass [pass]]");
			System.out.println("  -generateCert -out [filename] -hostname [name] [-pass [pass]]");
			System.out.println("  -reencodeSecrets");
			System.out.println("  -renewCertificates [-force]");
	}

	private static void exportCA(String args[]) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InternalErrorException, UnrecoverableKeyException {
		int i;
		String out = null;
		char[] pass = null;
		String alias = "soffid-ca";
		
		for (i = 1; i < args.length; i++) {
			if ("-out".equals(args[i]) && i < args.length - 1) 
				out = args[++i];
			else if ("-pass".equals(args[i]) && i < args.length - 1) 
				pass = args[++i].toCharArray();
			else if ("-alias".equals(args[i]) && i < args.length - 1) 
				alias = args[++i];
			else
				throw new RuntimeException("Unknown parameter " + args[i]);
		}
		if (i < args.length)
			throw new RuntimeException("Unknown parameter " + args[i]);
		if (out == null)
			throw new RuntimeException("Specify the output file");
		if (pass == null)
		{
			pass = System.console().readPassword("Output passphrase: ");
		}
        KeyStore rootks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getRootKeyStoreFile());
		Key key = rootks.getKey(SeyconKeyStore.ROOT_KEY, SeyconKeyStore.getKeyStorePassword().getPassword().toCharArray());
		Certificate[] cert = rootks.getCertificateChain(SeyconKeyStore.ROOT_KEY);
		
		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(null);
		ks.setKeyEntry(alias, key, pass, cert);

		log.info("Storing in PKCS12 file "+out);
		ks.store(new FileOutputStream(out), pass);
		
	}

	private static void importCA(String args[]) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InternalErrorException, UnrecoverableKeyException {
		int i;
		String in = null;
		char[] pass = null;
		String alias = "soffid-ca";
		for (i = 1; i < args.length; i++) {
			if ("-in".equals(args[i]) && i < args.length - 1) 
				in = args[++i];
			else if ("-pass".equals(args[i]) && i < args.length - 1) 
				pass = args[++i].toCharArray();
			else if ("-alias".equals(args[i]) && i < args.length - 1) 
				alias = args[++i];
			else
				throw new RuntimeException("Unknown parameter " + args[i]);
		}
		if (i < args.length)
			throw new RuntimeException("Unknown parameter " + args[i]);
		if (in == null)
			throw new RuntimeException("Specify the input file");
		if (pass == null)
		{
			pass = System.console().readPassword("PKCS12 file passphrase: ");
		}
		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(new FileInputStream(in), pass);
		Key key = ks.getKey(alias, pass);
		if (key == null)
			throw new RuntimeException("No key with alias "+alias+" found");
		
		Certificate[] cert = ks.getCertificateChain(alias);
		
        KeyStore rootks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getRootKeyStoreFile());
		rootks.setKeyEntry(SeyconKeyStore.ROOT_KEY, key, SeyconKeyStore.getKeyStorePassword().getPassword().toCharArray(), cert);
		log.info("Storing in Soffid keystore");
		
        Config config = Config.getConfig();
        File rootKeystore = new File (config.getHomeDir(), "conf/root.jks");

		rootks.store(new FileOutputStream( rootKeystore ),  SeyconKeyStore.getKeyStorePassword().getPassword().toCharArray());
	}


	private static void generateCert(String args[]) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InternalErrorException, UnrecoverableKeyException, NoSuchProviderException, InvalidKeyException, IllegalStateException, SignatureException {
		int i;
		String out = null;
		char[] pass = null;
		String hostname = null;
		String tenant = "master";
		String alias = null;
		for (i = 1; i < args.length; i++) {
			if ("-out".equals(args[i]) && i < args.length - 1) 
				out = args[++i];
			else if ("-pass".equals(args[i]) && i < args.length - 1) 
				pass = args[++i].toCharArray();
			else if ("-hostname".equals(args[i]) && i < args.length - 1) 
				hostname = args[++i];
			else if ("-alias".equals(args[i]) && i < args.length - 1) 
				alias = args[++i];
			else if ("-tenant".equals(args[i]) && i < args.length - 1) 
				tenant = args[++i];
			else
				throw new RuntimeException("Unknown parameter " + args[i]);
		}
		if (i < args.length)
			throw new RuntimeException("Unknown parameter " + args[i]);
		if (out == null)
			throw new RuntimeException("Specify the output file");
		if (hostname == null)
			throw new RuntimeException("Specify the certificate name");
		if (pass == null)
		{
			pass = System.console().readPassword("PKCS12 file passphrase: ");
		}
		if (alias == null) alias = hostname;

		
		KeyStore rootks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getRootKeyStoreFile());
		PrivateKey rootKey = (PrivateKey) rootks.getKey(SeyconKeyStore.ROOT_KEY, SeyconKeyStore.getKeyStorePassword().getPassword().toCharArray());
		X509Certificate rootCert = (X509Certificate) rootks.getCertificate(SeyconKeyStore.ROOT_KEY);
		
		CertificateServer s = new CertificateServer();
		KeyPair keyPair = s.generateNewKey();
		X509Certificate cert = s.createCertificate(tenant, hostname, keyPair.getPublic(), rootKey, rootCert, false);

		KeyStore ks = KeyStore.getInstance("pkcs12");
		ks.load(null);
		ks.setKeyEntry(alias, keyPair.getPrivate(), pass, new Certificate[] { cert, rootCert} );

		log.info("Storing in PKCS12 file "+out);
		ks.store(new FileOutputStream(out), pass);
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
			File configured = new File(config.getHomeDir(), "conf/configured");
			FileOutputStream out = new FileOutputStream(configured);
			out.write(new Date().toString().getBytes());
			out.close();
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
		for (Server s2: servers) {
			if (oldHostName != null && s2.getName().equals(oldHostName))
			{
				server = s2;
			}
		}
		if (server == null) {
			server = new Server();
			server.setName(hostName);
			server.setUrl("https://" + hostName + ":" + config.getPort() + "/");
			server.setType(ServerType.MASTERSERVER);
			server.setUseMasterDatabase(true);
			server = dispatcherSvc.create(server);
			TenantService tenantSvc = ServiceLocator.instance().getTenantService();
			Tenant t = tenantSvc.getMasterTenant();
			tenantSvc.addTenantServer(t, server.getName());
		} else {
			server.setName(hostName);
			server.setUrl("https://" + hostName + ":" + config.getPort() + "/");
			dispatcherSvc.update(server);
		}
		config.setHostName(hostName);
		CertificateServer s = new CertificateServer();

		log.info("Is server: " + config.isServer());
		log.info("Role: " + config.getRole());
		log.info("Server service: " + config.getServerService());
		log.info("Server list: " + config.getServerList());

		log.info("Generating security keys");
		s.createRoot(server);
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
	
	public static void reencodeSecrets() throws InternalErrorException {
		final SecretStoreService secretStoreService = ServerServiceLocator.instance().getSecretStoreService();
		final Collection<User> usuaris = secretStoreService.getUsersWithSecrets();
		final Collection<Account> accounts = secretStoreService.getAccountsWithPassword();
		int max = usuaris.size() + accounts.size();

		int processed = 0;

		for (User usuari : usuaris) {
			log.info("["+(100 * processed / max ) + "%] Reencoding secrets for user " + usuari.getUserName());
			try {
				secretStoreService.reencode(usuari);
			} catch (InternalErrorException e) {
				log.warn("Error reencoding secrets", e);
			}
			processed++;
		}

		for (Account acc : accounts) {
			log.info("["+(100 * processed / max ) + "%] Reencoding secrets for account " + acc.getName() + " @ " + acc.getSystem());
			try {
				Password p = secretStoreService.getPassword(acc.getId());
				if (p != null)
					secretStoreService.setPassword(acc.getId(), p);
			} catch (InternalErrorException e) {
				log.warn("Error reencoding secrets", e);
			}
			processed++;
		}
	}
}

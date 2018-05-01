package com.soffid.iam.sync.bootstrap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
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
import java.util.Enumeration;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.apache.commons.logging.LogFactory;
import org.mortbay.log.Log;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Configuration;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.Tenant;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.identity.IdentityGeneratorBean;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.service.ApplicationBootService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.TenantService;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.engine.log.LogConfigurator;
import com.soffid.iam.sync.jetty.SeyconLog;
import com.soffid.iam.sync.service.CertificateEnrollService;
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

import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.ServerRedirectException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class Configure {
    static org.apache.commons.logging.Log log  = LogFactory.getLog(Configure.class);

    public static void main(String args[]) throws Exception {
        LogConfigurator.configureMinimalLogging();
        Security.onSyncServer();
//        Log.setLog(new SeyconLog());

        if (args.length == 0) {
            System.out.println("Parameters:");
            System.out
                    .println("  -main -hostname .. -dbuser .. -dbpass .. -dburl ..");
            System.out
                    .println("  -hostname .. -server .. -tenant .. -user .. -pass ..");
            System.exit(1);
        }

        try {
            if ("-main".equals(args[0])) {
                parseMainParameters(args);
            } else  {

                parseSecondParameters(args);
            }
            log.info("Configuration successfully done.");
            System.exit(0);
        } catch (CertificateEnrollDenied e) {
            System.err.println ("Your certificate request has been denied.");
        } catch (CertificateEnrollWaitingForAproval e) {
            System.err.println ("Your certificate is pending for administrator aproval.");
        } catch (InternalErrorException e) {
        	System.err.println ("Internal error: "+e.toString());
        }
        System.exit(1);

    }

    private static void parseSecondParameters(String[] args) throws IOException,
            InvalidKeyException, UnrecoverableKeyException, KeyStoreException,
            NoSuchAlgorithmException, NoSuchProviderException,
            CertificateException, IllegalStateException, SignatureException,
            InternalErrorException, UnknownUserException,
            KeyManagementException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied {
        Config config = Config.getConfig();

        String adminTenant="";
        String adminUser = "";
        Password adminPassword = null;
        String serverUrl = "";
        String adminDomain = null;

        int i;
        for (i = 0; i < args.length - 1; i++) {
			if ("-hostname".equals(args[i]))
                config.setHostName(args[++i]);
            else if ("-tenant".equals(args[i]))
            	adminTenant = args[++i];
            else if ("-user".equals(args[i]))
                adminUser = (args[++i]);
//            else if ("-domain".equals(args[i]))
//                adminDomain = (args[++i]);
            else if ("-pass".equals(args[i]))
                adminPassword = (new Password(args[++i]));
            else if ("-server".equals(args[i]))
                serverUrl = args[++i];
            else
                throw new RuntimeException("Unknown parameter " + args[i]);
        }
        if (i < args.length)
            throw new RuntimeException("Unknown parameter " + args[i]);

        if (adminPassword == null)
        {
        	char pass [] = System.console().readPassword("%s's password: ", adminUser);
        	adminPassword = new Password(new String(pass));
        }
        
        try {
            /*
             * // Configurar servidor java.io.Console cons = System.console();
             * if (cons != null) { String hostname =
             * java.net.InetAddress.getLocalHost().getHostName();
             * config.setHostName( read (cons, "Hostname", config.getHostName(),
             * hostname) ); adminUser = read (cons, "Admin user", adminUser,
             * ""); char[] passwd; passwd = cons.readPassword("%s [%s]: ",
             * "Admin Password", adminPassword); if (passwd.length > 0) {
             * adminPassword = new Password (new String(passwd)); } serverUrl =
             * read (cons, "Server URL", serverUrl, ""); }
             */
            config.setRole("agent");
            CertificateServer cs = new CertificateServer();
            if (!cs.hasServerKey())
                cs.obtainCertificate(serverUrl, adminTenant, adminUser, adminPassword, null);
            else
                System.out.println ("This node is already configured. Remove conf directory to reconfigure.");
        } catch (NoClassDefFoundError e) {
            System.out.println("Warning: JAVA 6 required");
        } catch (NoSuchMethodError e) {
            System.out.println("Warning: JAVA 6 required");
        }

        configureAgent(adminTenant, adminUser, adminPassword, adminDomain, serverUrl);

    }
    
    private static void parseMainParameters(String[] args)
            throws Exception
    {
        Config config = Config.getConfig();

        int i;
        Password password = null;
        for (i = 1; i < args.length - 1; i++) {
            if ("-hostname".equals(args[i]))
                config.setHostName(args[++i]);
            else if ("-dbuser".equals(args[i]))
                config.setDbUser(args[++i]);
            else if ("-dbpass".equals(args[i]))
                password = new Password(args[++i]);
            else if ("-dburl".equals(args[i]))
                config.setDB(args[++i]);
            else
                throw new RuntimeException("Unknown parameter " + args[i]);
        }
        if (i < args.length)
            throw new RuntimeException("Unknown parameter " + args[i]);

        if (config.getDB() == null)
        	throw new RuntimeException ("Missing -dburl parameter");
        if (password == null)
        {
        	if (System.console() == null)
        		throw new RuntimeException ("Password required");
        	char pass [] = System.console().readPassword("%s's password: ", config.getDbUser());
        	password = new Password(new String(pass));
        }
        config.setPassword(password);
        config.setRole("server");
        // Configurar servidor
        /*
         * java.io.Console cons = System.console(); if (cons != null) { String
         * hostname = java.net.InetAddress.getLocalHost().getHostName();
         * config.setHostName( read (cons, "Hostname", config.getHostName(),
         * hostname) ); config.setDbUser( read (cons, "DB User",
         * config.getDbUser(), "seycon")); char[] passwd; passwd =
         * cons.readPassword("%s [%s]: ", "DB Password", config.getPassword());
         * if (passwd.length > 0) { Password p = new Password (new
         * String(passwd)); config.setPassword(p); } config.setDB( read (cons,
         * "DB URL", config.getDB(), "")); config.setDbPool( read (cons, "DB
         * Pool", config.getDbPool(), "15")); }
         */
        config.setRole("server");
        
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
        if ( ! dispatcherSvc.findAllServers().isEmpty())
        {
        	throw new InternalErrorException ("This server cannot be configured as the main server as long as there are some servers created at Soffid console.\nPlease remove them to proceed.");
        }
        
        CertificateServer s = new CertificateServer();

        Server server = new Server();
        server.setName(config.getHostName());
        server.setUrl("https://"+config.getHostName()+":"+config.getPort()+"/");
        server.setType(ServerType.MASTERSERVER);
        server.setUseMasterDatabase(true);
        server = dispatcherSvc.create(server);
        
        TenantService tenantSvc = ServiceLocator.instance().getTenantService();
        Tenant t = tenantSvc.getMasterTenant();
        tenantSvc.addTenantServer(t, server.getName());

        log.info("Is server: "+config.isServer());
        log.info("Role: "+config.getRole());
        log.info("Server service: "+config.getServerService());
        log.info("Server list: "+config.getServerList());
        
        log.info("Generating security keys");
        s.createRoot();
    }

    private static void configureAgent(String adminTenant, String adminUser,
            Password adminPassword, String adminDomain, String serverUrl)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            KeyStoreException, FileNotFoundException, CertificateException,
            IOException, InternalErrorException, InvalidKeyException,
            UnrecoverableKeyException, IllegalStateException,
            SignatureException, UnknownUserException, KeyManagementException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied {
        Config config = Config.getConfig();

        CertificateServer cs = new CertificateServer();
        
        if (!cs.hasServerKey()) {
            cs.obtainCertificate(serverUrl, adminTenant, adminUser,
                    adminPassword, adminDomain);
        }
        RemoteServiceLocator rsl = new RemoteServiceLocator(serverUrl);
        ServerService serverService = rsl.getServerService();
        config.setServerList(serverUrl);
        config.setServerService(serverService);
        config.updateFromServer();
    }


    protected static void updateDatabase () throws Exception
    {
    	Database db = new Database();
    	XmlReader reader = new XmlReader();
    	parseResources(db, reader, "console-ddl.xml");
    	parseResources(db, reader, "core-ddl.xml");
    	
    	com.soffid.iam.sync.engine.db.DataSource ds = new com.soffid.iam.sync.engine.db.DataSource();
        Connection conn = ds.getConnection();
        
        String type = Config.getConfig().getDB(); //$NON-NLS-1$
        DBUpdater updater ;
        if (type.contains(":mysql:") ||
        		type.contains(":mariadb:"))  //$NON-NLS-1$
        {
        	updater = new MySqlUpdater();
        } else if (type.contains(":oracle:")) { //$NON-NLS-1$
        	updater = new OracleUpdater();
        } else if (type.contains(":sqlserver:")) {
        	updater = new MsSqlServerUpdater();
        } else {
            throw new RuntimeException("Unable to get dialect for database type ["+type+"]"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        updater.setLog(System.out);
        updater.setIgnoreFailures(true);
        updater.update(conn, db);
        
    	IdentityGeneratorBean identityGenerator = (IdentityGeneratorBean) ServerServiceLocator.instance().getService("identity-generator");
    	if (! identityGenerator.isSequenceStarted())
    	{
    		long l = getMaxIdentifier(conn, db);
    		identityGenerator.initialize(l, 100, 1);
    	}

        conn.close();
    }

	private static long getMaxIdentifier(Connection connection, Database db) throws SQLException {
		long l = 1;
		for (Table t: db.tables)
		{
			for (Column c: t.columns)
			{
				if (c.primaryKey)
				{
					PreparedStatement st = connection.prepareStatement("SELECT MAX("+c.name+") FROM "+t.name);
					try
					{
						ResultSet rs = st.executeQuery();
						try
						{
							if (rs.next())
							{
								long l2 = rs.getLong(1);
								if (l2 >= l)
									l = l2+1;
							}
						}
						finally
						{
							rs.close();
						}
					}
					finally
					{
						st.close();
					}
				}
			}
		}
		return l;
	}

	private static void parseResources(Database db,
			XmlReader reader, String path) throws IOException, Exception {
		Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
    	while (resources.hasMoreElements())
    	{
    		reader.parse(db, resources.nextElement().openStream());
    	}
	}
}

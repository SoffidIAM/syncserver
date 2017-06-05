package es.caib.seycon.ng.sync.bootstrap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;

import org.apache.commons.logging.LogFactory;
import org.mortbay.log.Log;

import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.Server;
import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.ServerRedirectException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.cert.CertificateServer;
import es.caib.seycon.ng.sync.engine.log.LogConfigurator;
import es.caib.seycon.ng.sync.jetty.SeyconLog;
import es.caib.seycon.ng.sync.servei.CertificateEnrollService;
import es.caib.seycon.ng.sync.servei.ServerService;

public class Configure {

    public static void main(String args[]) throws Exception {
        LogConfigurator.configureMinimalLogging();
        org.apache.commons.logging.Log log  = LogFactory.getLog(Configure.class);
//        Log.setLog(new SeyconLog());

        if (args.length == 0) {
            System.out.println("Parameters:");
            System.out
                    .println("  -main -hostname .. -dbuser .. -dbpass .. -dburl ..");
            System.out
                    .println("  -hostname .. -server .. -user .. -pass ..");
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

        String adminUser = "";
        Password adminPassword = null;
        String serverUrl = "";
        String adminDomain = null;

        int i;
        for (i = 0; i < args.length - 1; i++) {
            if ("-hostname".equals(args[i]))
                config.setHostName(args[++i]);
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
                cs.obtainCertificate(serverUrl, adminUser, adminPassword, null);
            else
                System.out.println ("This node is already configured. Remove conf directory to reconfigure.");
        } catch (NoClassDefFoundError e) {
            System.out.println("Warning: JAVA 6 required");
        } catch (NoSuchMethodError e) {
            System.out.println("Warning: JAVA 6 required");
        }

        configureAgent(adminUser, adminPassword, adminDomain, serverUrl);

    }
    
    private static void parseMainParameters(String[] args)
            throws IOException, InvalidKeyException, UnrecoverableKeyException,
            KeyStoreException, NoSuchAlgorithmException,
            NoSuchProviderException, CertificateException,
            IllegalStateException, SignatureException, InternalErrorException,
            UnknownUserException, InstantiationException,
            IllegalAccessException, ClassNotFoundException,
            CertificateEnrollWaitingForAproval, CertificateEnrollDenied,
            KeyManagementException
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
        	char pass [] = System.console().readPassword("%s's password: ", config.getDbUser());
        	password = new Password(new String(pass));
        }
        config.setPassword(password);
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
        
        // Verificar configuracio
        ServerService service = ServerServiceLocator.instance().getServerService();
        config.setServerService(service);
        
        DispatcherService dispatcherSvc = ServerServiceLocator.instance().getDispatcherService(); 
        if ( ! dispatcherSvc.findAllServers().isEmpty())
        {
        	throw new InternalErrorException ("This server cannot be configured as the main server as long as there are some servers created at Soffid console.\nPlease remove them to proceed.");
        }
        
        Server server = new Server();
        server.setNom(config.getHostName());
        server.setUrl("https://"+config.getHostName()+":"+config.getPort()+"/");
        server.setType(ServerType.MASTERSERVER);
        server.setUseMasterDatabase(true);
        dispatcherSvc.create(server);

        CertificateServer s = new CertificateServer();
        s.createRoot();
    }

    private static void configureAgent(String adminUser,
            Password adminPassword, String adminDomain, String serverUrl)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            KeyStoreException, FileNotFoundException, CertificateException,
            IOException, InternalErrorException, InvalidKeyException,
            UnrecoverableKeyException, IllegalStateException,
            SignatureException, UnknownUserException, KeyManagementException, CertificateEnrollWaitingForAproval, CertificateEnrollDenied {
        Config config = Config.getConfig();

        CertificateServer cs = new CertificateServer();
        
        if (!cs.hasServerKey()) {
            cs.obtainCertificate(serverUrl, adminUser,
                    adminPassword, adminDomain);
        }
        RemoteServiceLocator rsl = new RemoteServiceLocator(serverUrl);
        CertificateEnrollService server = rsl.getCertificateEnrollService();
        ServerService serverService = rsl.getServerService();
        config.setServerList(serverUrl);
        config.setServerService(serverService);
        config.updateFromServer();
    }


}

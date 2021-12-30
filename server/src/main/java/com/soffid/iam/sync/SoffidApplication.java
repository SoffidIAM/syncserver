/*
 * ServerApplication.java
 *
 * Created on May 9, 2000, 8:46 AM
 */

package com.soffid.iam.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.security.Policy;
import java.util.Properties;

import javax.security.auth.login.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteInvokerFactory;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.sync.agent.AgentManager;
import com.soffid.iam.sync.agent.AgentManagerImpl;
import com.soffid.iam.sync.engine.Engine;
import com.soffid.iam.sync.engine.kerberos.ChainConfiguration;
import com.soffid.iam.sync.engine.log.LogConfigurator;
import com.soffid.iam.sync.hub.client.RemoteThread;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.jetty.SecurityHeaderFactory;
import com.soffid.iam.sync.jetty.SeyconLog;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.comu.Server;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.sync.agent.AgentManagerBaseProxy;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.util.TimedProcess;


public class SoffidApplication extends Object {

    /** pendiente de finalizar ordenadamente */
    private static boolean shutdownPending;

    public static class InputChecking implements Runnable {
        public void run() {
            try {
                while (System.in.read() > 0)
                    ;
            } catch (IOException e) {
                e.printStackTrace();
            }
        	System.exit(2);
        }
    }

    /** Creates new ServerApplication */
    public SoffidApplication() {
    }

    /**
     * consultar si se solicitado la finalización del servidor
     * 
     * @return true si está en proceso de finalización
     */
    public static boolean isShutdownPending() {
        return shutdownPending;
    }

    /**
     * Cargar propiedades del fichero seycon.properties
     * 
     * @throws java.io.IOException
     *             error de entrada/salida
     */
    protected static com.soffid.iam.sync.engine.session.SessionManager ssoDaemon = null;
    protected static AgentManager agentManager;
    protected static AgentManagerBaseProxy agentManagerV1;
    private static JettyServer jetty;
    protected static Logger log;

    private static void copyFile(File in, File out) throws Exception {
        FileInputStream fis = new FileInputStream(in);
        FileOutputStream fos = new FileOutputStream(out);
        try {
            byte[] buf = new byte[1024];
            int i = 0;
            while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (fis != null)
                fis.close();
            if (fos != null)
                fos.close();
        }
    }

    /**
     * Procedimiento principal. 1. Ajusta el huso horario<BR>
     * 2. Crea la corriente de salida por defecto según la propiedad seycon.log<BR>
     * 3. Crea el registro RMI según la propiedad seycon.server.url<BR>
     * 4. Instancia al {@link TaskGenerator} a partir de seycon.db.user,
     * seycon.db.pass y seycon.db.string <BR>
     * 5. Instancia el
     * {@link com.soffid.iam.sync.engine.session.SessionManager} <BR>
     * 6. instancia el {@link es.caib.seycon.impl.sso.LogonImpl}<BR>
     * 
     * @param args
     *            no se espera ningún parámero
     * @throws InternalErrorException 
     * @throws RemoteException 
     */
    public static void main(String args[]) throws RemoteException, InternalErrorException {
    	LogConfigurator.configureLogging();

    	Security.onSyncServer();
    	
        log = LoggerFactory.getLogger("main");
        
        
//        new DiagThread().start();
        try {

            InputChecking inputChecking = new InputChecking();
            Thread checker = new Thread(inputChecking);
            checker.start();

            // Ajustar el huso horario
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm:ss, zzzz");
            Config config = Config.getConfig();
            File tmp = new File (config.getHomeDir(), "tmp");
            tmp.mkdir();
            System.setProperty("user.dir", tmp.getPath());
            log.info("*************************************************");
            log.info("Soffid IAM Sync Server version {} starting", config.getVersion());
            if ("server".equals(config.getRole()))
            	log.info("Running as a SYNC SERVER");
            else if ("gateway".equals(config.getRole()))
            {
            	log.info("Running as a SYNC SERVER GATEWAY");
            	Security.onSyncProxy();
            }
            else if ("remote".equals(config.getRole()))
            {
            	log.info("Running as a REMOTE PROXY SERVER");
            	Security.onSyncProxy();
            }
            else
            {
            	log.info("Running as a PROXY SERVER");
            	Security.onSyncProxy();
            }
            
            log.info("*************************************************");
            if (config.isDebug())
                SeyconLog.setDebug(true);
            
            configureSecurityHeaders();
            configureSecurity ();
            configureSystemOut();
            
            configureCerts(config);

            if ("gateway".equals(config.getRole()))
            {
	            URLManager url = config.getURL();
	            if (config.isBroadcastListen())
	            {
	               	jetty = new JettyServer(null, Integer.parseInt(config.getPort()));
	            } else {
	            	jetty = new JettyServer(config.getHostName(), Integer.parseInt(config.getPort()));
	            }
	            jetty.startGateway();
            }
            else if ("remote".equals(config.getRole()))
            {
            	// Configure login auth
            	Configuration.setConfiguration(new ChainConfiguration());

           		jetty = new JettyServer(config.getHostName(), 443);

           		// Iniciar el Agente
        		agentManager = new AgentManagerImpl();
        		agentManagerV1 = new AgentManagerBaseProxy();
        		agentManagerV1.setAgentManager(agentManager);
        		jetty.bind("/seycon/AgentManager-en", agentManager, "server");
        		jetty.bind("/seycon/AgentManager", agentManagerV1, "server");
            	// Notificar el arranque
            	notifyStart();
            	
            	new RemoteThread(jetty).run();
            }
            else
            {
            	// Configure login auth
            	Configuration.setConfiguration(new ChainConfiguration());

            	// enableIPTables(config);
            	if ("server".equals (config.getRole())) {
            		ServerApplication.configure ();
            	}

            	URLManager url = config.getURL();
            	int port = 760;
            	try
            	{
            		port = Integer.parseInt(config.getPort());
            	} catch (Exception e ) {
            		log.info("Error parsing port "+config.getPort());
            	}
            	if (config.isBroadcastListen())
            	{
            		jetty = new JettyServer(null, port);
            	} else {
            		jetty = new JettyServer(config.getHostName(), port);
            	}
            	jetty.start();

            	// Iniciar diagnósticos remotos
            	jetty.bindDiagnostics();

            	// Configurar el servidor
            	if (config.isServer()) {
            		ServerApplication.start();
            	} else {
            		// Iniciar el Agente
            		agentManager = new AgentManagerImpl();
            		agentManagerV1 = new AgentManagerBaseProxy();
            		agentManagerV1.setAgentManager(agentManager);
            		jetty.bind("/seycon/AgentManager-en", agentManager, "server");
            		jetty.bind("/seycon/AgentManager", agentManagerV1, "server");
            	}
            	// Notificar el arranque
            	notifyStart();
            }
        } catch (Throwable e) {
            log.warn("Unrecoverable error", e);
            // out.println (e.getMessage() );
            System.exit(1);
        }
    }

    private static void configureSystemOut() {
    	System.setOut( new SystemOutMultiplexer(System.out) );
	}

	private static void configureSecurityHeaders() {
    	RemoteInvokerFactory.addHeadersFactory(new SecurityHeaderFactory());
	}

	private static void configureSecurity() throws FileNotFoundException, IOException {
    	File homeDir = Config.getConfig().getHomeDir();
    	File securityPolicy = new File (new File (homeDir, "conf"), "security.policy");
    	if (! securityPolicy.canRead())
    	{
    		InputStream in = SoffidApplication.class.getResourceAsStream("security.policy");
    		FileOutputStream out = new FileOutputStream(securityPolicy);
    		int i;
    		while ((i = in.read()) >= 0)
    			out.write (i);
    		out.close();
    		in.close();
    	}
    	System.setProperty("syncserver.home", homeDir.getAbsolutePath());
    	System.setProperty("java.security.policy",securityPolicy.toURI().toString());
    	System.setSecurityManager(new SecurityManager());
	}

	private static void configureCerts(Config config) {
        try {
            // Directorio conf del seycon
            String configDir = config.getHomeDir().getAbsolutePath() + File.separatorChar
                    + "conf";
            // log.info("Seycon Config directory {}", configDir ,null);
            File cacertsConf = new File(configDir, "cacerts");
            if (!cacertsConf.exists()) {
                log.info("Soffid cacerts file DOES NOT exist at {}, checking JVM's cacerts",
                        configDir, null);
                // copiamos el de la JVM al directorio conf del seycon
                // Verificamos que existe en el JVM
                String java_home_path = System.getProperty("java.home");
                // directorio security de java (donde residen los cacerts)
                String java_security_path = java_home_path + File.separatorChar + "lib"
                        + File.separatorChar + "security";
                File cacertsJVM = new File(java_security_path, "cacerts");
                if (!cacertsJVM.exists()) {
                    log.info("Seycon cacerts file DOES NOT exist at JVM {}",
                            java_security_path, null);
                } else {
                    log.info("Copying cacerts from JVM at {} to {}", java_security_path,
                            configDir);
                    copyFile(cacertsJVM, cacertsConf);
                }
            } else {
                log.info("Using cacerts file at  {}", configDir, null);
            }
            // Si llegamos aquí, se ha copiado el cacerts al directorio conf
            File cacertsCheck = new File(configDir, "cacerts");
            if (cacertsCheck.exists()) { // ¿Se ha copiado correctamente?
                System.setProperty("javax.net.ssl.trustStore", cacertsConf.getAbsolutePath());
                log.info("Setting javax.net.ssl.trustStore at {} ",
                        cacertsConf.getAbsolutePath(), null);
            } else {
                log.warn("Important: Could not establish the cacerts file");
            }
        } catch (Throwable th) {
            log.warn("Error setting the cacerts file: ", th);
        }
    }

    /**
     * Notifica a los seycon servers que el sistema está listo para instanciar
     * agentes. Para ello recorre la propiedad seycon.server.list, accede al
     * objeto remote {@link Server} y ejecuta el metodo
     * {@link Server#ClientAgentStarted(String)}, a consecuencia del cual el
     * servidor reconectará todos los agentes que se encuentren desconectados.
     * Adicionalmente, recuperará del servidor la propiedad seycon.server.list y
     * actualizara el fichero de propiedades (seycon.properties)
     * 
     * @throws IOException
     * @throws FileNotFoundException
     * @throws InternalErrorException
     */

    public static void notifyStart() throws FileNotFoundException, IOException, InternalErrorException {
        Config config = Config.getConfig();

        // Check server list
        if (config.getServerList()==null || config.getServerList().trim().isEmpty()) {
        	String m = "Server list not found, please: 1) stop syncserver, 2) unpublish syncserver in IAM, 3) configure again, 4) start service";
        	log.error(m);
		try {
	                Thread.sleep(60000);
		} catch (InterruptedException e) {}
        	throw new InternalErrorException(m);
        }

        String serverList[] = config.getServerList().split("[, ]+");
        for (int i = 0; i < serverList.length; i++) {
        	if (! config.getHostName().equals(serverList[i]))
        	{
                log.info("Notifying start to {}", serverList[i], null);
                try {
                    RemoteServiceLocator rsl = new RemoteServiceLocator(serverList[i]);
    
                    ServerService server = rsl.getServerService();
                    // Notificar inicia
                    server.clientAgentStarted(config.getHostName());
                    if (!config.isServer()) {
                        config.setServerList(server.getConfig("seycon.server.list"));
                    }
                } catch (Exception e) {
                    log.info("Notification failure: {}", e.toString(), null);
                }
        	}
        }
    }


    protected static String FILE_SEPARATOR = File.separator;

    @Deprecated
    private static void enableIPTables(Config config) 
            throws InternalErrorException {
        File ipTables = new File("/sbin/iptables");
        if (false) {
            try {
                if (config.isServer()) {
                    TimedProcess p = new TimedProcess(5000);
                    p.exec(new String[] { ipTables.getAbsolutePath(), "-I", "INPUT", "1", "-d",
                            config.getHostName(), "-p", "tcp", "-m", "tcp", "--destination-port",
                            config.getPort(), "-j", "ACCEPT" });
                } else {
                    TimedProcess p = new TimedProcess(5000);
                    p.exec(new String[] { ipTables.getAbsolutePath(), "-I", "INPUT", "1", "-d",
                            config.getHostName(), "-s", config.getHostName(), "-p", "tcp", "-m",
                            "tcp", "--destination-port", config.getPort(), "-j", "ACCEPT" });
                }
            } catch (Exception e) {
                log.warn("Error adjustint ip-tables", e);
            }
        }
    }

    /**
     * Finalizar la aplicación.
     * @throws IOException 
     * @throws FileNotFoundException 
     * 
     * @see TaskGenerator#shutDown
     * @see es.caib.seycon.impl.sso.LogonImpl#shutDown
     * @see com.soffid.iam.sync.engine.session.SessionManager#shutDown
     */
    public static void shutDown() throws FileNotFoundException, IOException {
        new Thread () {
            public void run() {
                try {
                	log.info("Shutting down....");
                    sleep(3000);
                } catch (InterruptedException e) {
                }
                Config config;
				try {
					config = Config.getConfig();
					jetty.getServer().stop();
	                if (config.isServer()) {
	                    shutdownPending = true;
	                    Engine.getEngine().shutDown ();
	                    ssoDaemon.shutDown();
	                    sleep (5000);
	                } else {
	                	System.exit(2);
	                }
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					System.exit(2);
				}
            };
        }.start();
    }

    public static com.soffid.iam.sync.engine.session.SessionManager getSsoDaemon() {
        return ssoDaemon;
    }

    public static JettyServer getJetty() {
        return jetty;
    }
}

/*
 * ServerApplication.java
 *
 * Created on May 9, 2000, 8:46 AM
 */

package es.caib.seycon.ng.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.Properties;

import javax.security.auth.login.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.caib.seycon.ng.comu.Server;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.sync.agent.AgentManager;
import es.caib.seycon.ng.sync.agent.AgentManagerImpl;
import es.caib.seycon.ng.sync.engine.Engine;
import es.caib.seycon.ng.sync.engine.diag.DiagThread;
import es.caib.seycon.ng.sync.engine.kerberos.ChainConfiguration;
import es.caib.seycon.ng.sync.engine.log.LogConfigurator;
import es.caib.seycon.ng.sync.jetty.JettyServer;
import es.caib.seycon.ng.sync.jetty.SeyconLog;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.util.TimedProcess;

/**
 * Clase principal del servidor SEYCON
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.1 $
 */

// $Log: SeyconApplication.java,v $
// Revision 1.1  2012-11-07 07:50:12  u07286
// Refactoring hibernate
//
// Revision 1.38 2012-06-06 12:12:53 u07286
// Suport per a SAML
//
// Revision 1.36 2012-05-16 10:57:57 u07286
// Reestructuració de paquets seycon antics
//
// Revision 1.35 2012-03-27 11:19:19 u88683
// tornem a posar el Naming.rebind(url.getRMIString(), new ServerAgent());
//
// Revision 1.34 2012-02-28 11:33:31 u88683
// fem binding del serverstatus per a la crida des de seu
//
// Revision 1.33 2012-02-24 07:41:20 u07286
// Protegir paths SEU
//
// Revision 1.32 2012-02-24 07:35:04 u07286
// Usuari virtual per a SEU
//
// Revision 1.31 2012-02-16 11:20:24 u07286
// Llevat ServerAgent
//
// Revision 1.30 2012-02-10 13:34:28 u88683
// Afegim el SeyconServerStatus per obtindre informaci� de l'estat del server i
// els seus agents
//
// Revision 1.29 2011-10-10 08:42:28 u07286
// Solo crear servidor RMI si la url es RMI
//
// Revision 1.28 2011-05-02 11:41:48 u07286
// Corregir error RMI
//
// Revision 1.27 2011-04-05 12:02:42 u07286
// Activación syslog
//
// Revision 1.26 2011-01-11 07:07:00 u07286
// Suport per a secrets
//
// Revision 1.25 2010-12-07 13:37:14 u07286
// Renombrar LogoffDaemon a SessionManager
//
// Revision 1.24 2010-08-27 12:26:31 u88683
// cacerts: fem que es faci una c�pia al directori conf del seycon3 des de la
// m�quina virtual java, i establim la propietat javax.net.ssl.trustStore al
// fitxer cacerts de conf
//
// Revision 1.23 2010-03-15 10:23:31 u07286
// Movido a tag HEAD
//
// Revision 1.9.2.3 2009-07-17 10:02:40 u88683
// Cambios en la versi�n 3.0.16 aplicados a la 3.1
//
// Revision 1.9.2.3 2009-07-17 12:01:01 u88683
// Merge a seycon-3.0.16
//
// Revision 1.22 2009-07-01 07:48:25 u07286
// Generación de diagnósticos
//
// Revision 1.9.2.2 2009-06-16 11:23:01 u07286
// Merge a seycon-3.0.15
//
// Revision 1.9.2.1 2009-03-23 07:52:00 u89559
// *** empty log message ***
//
// Revision 1.21 2009-04-28 02:45:16 u07286
// Desproteger el downloadLibrary
//
// Revision 1.20 2009-04-23 12:22:46 u07286
// Soporte para JVM_OPTS
//
// Revision 1.19 2009-04-22 07:30:18 u07286
// Eliminada actualización automática de IPTables
//
// Revision 1.18 2009-04-16 10:24:24 u07286
// Proteger download en un contexto privado
//
// Revision 1.17 2009-03-04 09:07:42 u07286
// Cambiado nivel de protección de DownloadLibrary
//
// Revision 1.16 2009-03-03 13:46:57 u07286
// Posibilidad de reiniciar servidores y agentes
//
// Revision 1.15 2009-03-03 13:30:02 u07286
// Cambiado mecanismo de registro de la aplicación web de administración
//
// Revision 1.14 2009-03-02 13:16:07 u89559
// *** empty log message ***
//
// Revision 1.13 2009-02-27 07:54:58 u89559
// *** empty log message ***
//
// Revision 1.12 2009-02-25 08:55:51 u89559
// *** empty log message ***
//
// Revision 1.11 2009-02-24 14:16:01 u89559
// *** empty log message ***
//
// Revision 1.10 2009-02-03 13:01:13 u89559
// *** empty log message ***
//
// Revision 1.9 2008-12-17 09:31:30 u89559
// *** empty log message ***
//
// Revision 1.8 2008-12-16 08:39:16 u89559
// *** empty log message ***
//
// Revision 1.7 2008-10-31 07:15:56 u89559
// *** empty log message ***
//
// Revision 1.6 2008-10-29 10:35:36 u89559
// *** empty log message ***
//
// Revision 1.5 2008-10-24 12:05:56 u89559
// *** empty log message ***
//
// Revision 1.4 2008-10-21 10:24:40 u89559
// *** empty log message ***
//
// Revision 1.3 2008-10-20 10:03:44 u07286
// Habilitar gestión de IPTABLES
//
// Revision 1.2 2008-10-17 09:58:36 u07286
// Adaptación al paso intermedio de migración
//
// Revision 1.1 2008-10-16 11:43:42 u07286
// Migrado de RMI a HTTP
//
// Revision 1.2 2008-10-03 13:29:04 u89559
// *** empty log message ***
//
// Revision 1.1 2007-09-06 12:51:10 u89559
// [T252]
//
// Revision 1.4 2005-05-20 08:41:10 u07286
// Migracion a SSL
//
// Revision 1.3 2004/03/15 12:08:05 u07286
// Conversion UTF-8
//
// Revision 1.2 2004/03/15 11:57:49 u07286
// Agregada documentacion JavaDoc
//
public class SeyconApplication extends Object {

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
    public SeyconApplication() {
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
    protected static es.caib.seycon.ng.sync.engine.session.SessionManager ssoDaemon = null;
    protected static es.caib.seycon.ng.sync.engine.socket.SSOServer sso = null;
    protected static AgentManager agentManager;
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
     * {@link es.caib.seycon.ng.sync.engine.session.SessionManager} <BR>
     * 6. instancia el {@link es.caib.seycon.impl.sso.LogonImpl}<BR>
     * 
     * @param args
     *            no se espera ningún parámero
     * @throws InternalErrorException 
     * @throws RemoteException 
     */
    public static void main(String args[]) throws RemoteException, InternalErrorException {


    	LogConfigurator.configureLogging();

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
            log.info("*************************************************");
            log.info("Soffid IAM Sync Server version {} starting", config.getVersion());
            if ("server".equals(config.getRole()))
            	log.info("Running as a SYNC SERVER");
            else
            	log.info("Running as a PROXY SERVER");
            log.info("*************************************************");
            if (config.isDebug())
                SeyconLog.setDebug(true);

            // Establecemos el cacerts (copiamos el de JVM a conf si no existe)
            configureCerts(config);
            // Configure login auth
            Configuration.setConfiguration(new ChainConfiguration());

            // enableIPTables(config);
            if ("server".equals (config.getRole())) {
                ServerApplication.configure ();
            }

            URLManager url = config.getURL();
            String port = config.getPort();
            
            try {
            	log.info("Checking local port");
				File propsFile = new File(Config.getConfig().getHomeDir(), "conf/seycon.properties");
				Properties prop = new Properties();
				prop.load(new FileInputStream(propsFile));
				String localPort = prop.getProperty("local_port");
            	log.info("local port="+localPort);
				if (localPort != null)
					port = localPort;
			} catch (Exception e) {
				e.printStackTrace();
			}
            
            if (config.isBroadcastListen())
            {
               	jetty = new JettyServer(null, Integer.parseInt(port));
            } else {
            	jetty = new JettyServer(config.getHostName(), Integer.parseInt(port));
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
                jetty.bind("/seycon/AgentManager", agentManager, "server");
                // web d'administració
                jetty.bindAdministrationWeb();

            }
            // Notificar el arranque
            notifyStart();
        } catch (Throwable e) {
            log.warn("Unrecoverable error", e);
            // out.println (e.getMessage() );
            System.exit(1);
        }
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
     * @see es.caib.seycon.ng.sync.engine.session.SessionManager#shutDown
     */
    public static void shutDown() throws FileNotFoundException, IOException {
        new Thread () {
            public void run() {
                try {
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
	                    sso.shutDown();
	                    ssoDaemon.shutDown();
	                } else {
	                	System.exit(2);
	                }
				} catch (Exception e) {
					e.printStackTrace();
				}
            };
        }.start();
    }

    public static es.caib.seycon.ng.sync.engine.session.SessionManager getSsoDaemon() {
        return ssoDaemon;
    }

    public static es.caib.seycon.ng.sync.engine.socket.SSOServer getSso() {
        return sso;
    }

    public static JettyServer getJetty() {
        return jetty;
    }
}

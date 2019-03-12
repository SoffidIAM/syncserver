/*
 * ServerApplication.java
 *
 * Created on May 9, 2000, 8:46 AM
 */

package com.soffid.iam.sync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.config.Config;
import com.soffid.iam.sync.bootstrap.QueryHelper;
import com.soffid.iam.sync.engine.DispatcherHandler;
import com.soffid.iam.sync.engine.Engine;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.session.SessionManager;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.web.internal.DownloadLibraryServlet;
import com.soffid.iam.util.Syslogger;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.agent.AgentManager;

/**
 * Clase principal del servidor SEYCON
 * 
 */

public class ServerApplication extends SoffidApplication {
    private static ServerService server;

    public static void configure () throws FileNotFoundException, IOException, InternalErrorException, SQLException {
        Config config = Config.getConfig();
        
        setCacheParams();

        server = ServerServiceLocator.instance().getServerService();
        config.setServerService(server);
    }
    
    protected static void setCacheParams () throws InternalErrorException, SQLException, FileNotFoundException, IOException
    {
   		Connection c = ConnectionPool.getPool().getPoolConnection();
   		try {
			QueryHelper qh = new QueryHelper( c );
	   		for (Object[] row: qh.select("SELECT CON_VALOR "
	   				+ "FROM SC_CONFIG, SC_TENANT "
	   				+ "WHERE TEN_ID=CON_TEN_ID AND CON_CODI='soffid.cache.enable' and TEN_NAME='master'", new String[0]))
	   		{
	   			System.setProperty("soffid.cache.enable", (String) row[0]);
	    	}
	   		File confDir = Config.getConfig().getHomeDir();
	   		File cfgFile = new File ( new File (confDir, "conf"), "jcs.properties");
	   		if (cfgFile.canRead())
	   		{
	   			System.setProperty("soffid.cache.configFile", cfgFile.getAbsolutePath());
	   			log.info("Using cache configuration file : "+cfgFile.getAbsolutePath());
	   		}
	   		else
	   		{
				for ( Object[] data: qh.select(
						  "SELECT BCO_NAME, BCO_VALUE "
						+ "FROM   SC_BLOCON "
						+ "WHERE  BCO_NAME = 'soffid.cache.config'", new Object [0]))
				{
					byte b[] = null;
					if (data[1] == null)
						b = null;
					else if (data[1] instanceof byte[])
						b = (byte[]) data[1];
					if (b != null)
					{
						System.setProperty  ((String) data[0], new String(b, "UTF-8"));
						log.info("Using stored jcs configuration");
					}
				}
	   		}
   		} finally {
   			ConnectionPool.getPool().releaseConnection(c);
   		}
   	}


    
    public static void start() throws InterruptedException, FileNotFoundException, IOException, InternalErrorException {
        Config config = Config.getConfig();

        // Ajustar el tamaño del pool
        String poolSize = server.getConfig("seycon.db.poolsize");
        if (poolSize == null)
            poolSize = "15";
        ConnectionPool pool = ConnectionPool.getPool();
        // pool.allocate(Integer.parseInt(poolSize));

        Config.getConfig().setServerService(server);
        // Set kerberos properties
        setKerberosProperties ();
        // Configuramos el syslog
        Syslogger.configure();
        // Cambiar las claves de activación
        ServiceLocator sl = ServerServiceLocator.instance();
        sl.getSecretConfigurationService().changeAuthToken();
        // Publicar servicios
        JettyServer jetty = getJetty();
        // web d'administració
        jetty.bindAdministrationWeb();
        // Download del codi font
        if (config.isActiveServer()) {
            jetty.bindServiceServlet("/", null, DownloadLibraryServlet.class);
        }

        for ( Object service: 
        	ServerServiceLocator.instance().getContext().
        		getBeansOfType(com.soffid.iam.service.ApplicationBootService.class).
        			values())
        {
        	((com.soffid.iam.service.ApplicationBootService) service).syncServerBoot();
        }
        
        // Iniciar el generador de tareas
        Engine engine = Engine.getEngine();
        engine.start();
        Thread.sleep(15000);

        // Iniciar la parte activa
        if (config.isActiveServer()) {
            // Daemon de logoff
            Thread.sleep(1000);
            ssoDaemon = new SessionManager();
            ssoDaemon.start();
            Thread.sleep(1000);
            // Servidor de Single Sign-On para PCs
            sso = new com.soffid.iam.sync.engine.socket.SSOServer();
            sso.start();
            Thread.sleep(1000);
        }
        log.info("Seycon Server started", null, null);
        tryToResetAgents();
        agentManager = ServerServiceLocator.instance().getAgentManager();
        
    }
    
    private static void setKerberosProperties() throws FileNotFoundException, IOException {
    	if (System.getProperty("java.security.krb5.conf") == null)
    	{
    		String krb5File = Config.getConfig().getHomeDir() + "/conf/krb5.conf";
    		java.lang.System.setProperty("java.security.krb5.conf", krb5File);
    	}
    	if (System.getProperty("sun.security.krb5.debug") == null)
    		java.lang.System.setProperty("sun.security.krb5.debug", "true");
    	if (System.getProperty("javax.security.auth.useSubjectCredsOnly") == null)
    		java.lang.System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
	}

	private static void tryToResetAgents() throws IOException, InternalErrorException {
        Config config = Config.getConfig();
        String BASE_DIRECTORY = config.getHomeDir().getAbsolutePath();
        File resetFile = new File(BASE_DIRECTORY + FILE_SEPARATOR + "tmp" + FILE_SEPARATOR
                + "reset_agents");
        if (resetFile.exists()) {
            resetAgents();
            resetFile.delete();
        }
    }

    private static void resetAgents() throws IOException, InternalErrorException {
        String taskDispatcherURL = "";
        Config config = Config.getConfig();
        String servidors[] = config.getSeyconServerHostList();

        LinkedList<String> v = new LinkedList<String>();
        v.add(servidors[0]);
        for (int i = 1; i < servidors.length; i++) {
            resetHost(v, servidors[i]);
        }
        TaskGenerator tg = ServerServiceLocator.instance().getTaskGenerator();
        for (Iterator<DispatcherHandler> it = tg.getDispatchers().iterator(); it.hasNext();) {
            DispatcherHandler dispatcher = it.next();
            if (dispatcher != null && dispatcher.getSystem() != null
                    && dispatcher.getSystem().getUrl() != null) {
                String url = dispatcher.getSystem().getUrl();
                resetHost(v, url);
            }
        }
    }

    private static void resetHost(LinkedList<String> v, String host) throws FileNotFoundException,
            IOException {
        if (!v.contains(host) && !host.equals("local")
                && !host.equals(Config.getConfig().getHostName())) {
        	// Mind this methos is still using the old (spanish) service point
            es.caib.seycon.ng.remote.RemoteServiceLocator locator = new es.caib.seycon.ng.remote.RemoteServiceLocator();
            locator.setServer(host);

            v.add(host);
            log.info("Restarting {}", host, null);
            try {
                AgentManager mgr = locator.getAgentManager();
                mgr.reset();
                log.info("Restarting {}", host, null);
            } catch (Exception e) {
                log.warn("Unable to reset agent " + host, e);

            }
        }
    }


}

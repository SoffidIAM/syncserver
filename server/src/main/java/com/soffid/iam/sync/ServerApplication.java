/*
 * ServerApplication.java
 *
 * Created on May 9, 2000, 8:46 AM
 */

package com.soffid.iam.sync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import com.soffid.iam.utils.Security;

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
   		} finally {
   			ConnectionPool.getPool().releaseConnection(c);
   		}
   		File confDir = Config.getConfig().getHomeDir();
   		File cfgFile = new File ( new File (confDir, "conf"), "jcs.properties");
   		if (cfgFile.canRead())
   			System.setProperty("soffid.cache.configFile", cfgFile.getAbsolutePath());
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

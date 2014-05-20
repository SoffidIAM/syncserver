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
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.mortbay.log.Slf4jLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate3.HibernateAccessor;
import org.springframework.orm.hibernate3.HibernateInterceptor;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.RemoteServicePublisher;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.servei.ApplicationBootService;
import es.caib.seycon.ng.servei.SeyconServiceLocator;
import es.caib.seycon.ng.sync.agent.AgentManager;
import es.caib.seycon.ng.sync.agent.AgentManagerImpl;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.Engine;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.log.LogConfigurator;
import es.caib.seycon.ng.sync.engine.session.SessionManager;
import es.caib.seycon.ng.sync.jetty.JettyServer;
import es.caib.seycon.ng.sync.jetty.SeyconLog;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.ng.sync.web.internal.DownloadLibraryServlet;
import es.caib.seycon.ng.utils.Security;
import es.caib.seycon.util.Syslogger;
import es.caib.seycon.util.TimedProcess;

/**
 * Clase principal del servidor SEYCON
 * 
 */

public class ServerApplication extends SeyconApplication {
    private static ServerService server;

    public static void configure () throws FileNotFoundException, IOException {
        Config config = Config.getConfig();
        
//        HibernateInterceptor hi = (HibernateInterceptor) ServerServiceLocator.instance().getService("hibernateInterceptor");
//        hi.setFlushMode(HibernateAccessor.FLUSH_EAGER);

        server = ServerServiceLocator.instance().getServerService();
        config.setServerService(server);
    }
    
    public static void start() throws InterruptedException, FileNotFoundException, IOException, InternalErrorException {
        Config config = Config.getConfig();

        Security.disableAllSecurityForEver();
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
        jetty.bindReplicaServlet();

        for ( Object service: 
        	ServerServiceLocator.instance().getContext().
        		getBeansOfType(ApplicationBootService.class).
        			values())
        {
        	((ApplicationBootService) service).syncServerBoot();
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
            sso = new es.caib.seycon.ng.sync.engine.socket.SSOServer();
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
            if (dispatcher != null && dispatcher.getDispatcher() != null
                    && dispatcher.getDispatcher().getUrl() != null) {
                String url = dispatcher.getDispatcher().getUrl();
                resetHost(v, url);
            }
        }
    }

    private static void resetHost(LinkedList<String> v, String host) throws FileNotFoundException,
            IOException {
        if (!v.contains(host) && !host.equals("local")
                && !host.equals(Config.getConfig().getHostName())) {
            RemoteServiceLocator locator = new RemoteServiceLocator();
            locator.setServer(host);

            v.add(host);
            log.info("Reiniciant {}", host, null);
            try {
                AgentManager mgr = locator.getAgentManager();
                mgr.reset();
                log.info("Reiniciat {}", host, null);
            } catch (Exception e) {
                log.warn("Imposible reiniciar agent " + host, e);

            }
        }
    }


}

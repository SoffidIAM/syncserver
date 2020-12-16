// Copyright (c) 2000 Govern  de les Illes Balears
package com.soffid.iam.sync.agent;

import java.rmi.server.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;

import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.ServerPlugin;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.agent.Agent;
import es.caib.seycon.ng.sync.agent.AgentManager;
import es.caib.seycon.ng.sync.agent.AgentManagerBaseCustom;

import java.rmi.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteInvokerFactory;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.ServerApplication;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.api.System;

/**
 * Gestor de agentes. Clase que implementa en interfaz remoto (RMI) AgentMgr <BR>
 * 
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.1 $
 * @see AgentMgr
 * @see Agent
 */

public class AgentManagerImpl extends AgentManagerBase {
    // private static Server server;
    private static Logger log = Log.getLogger("AgentManager");
    static Hashtable serverTable = new Hashtable();
    static Hashtable<String, PluginInfo> pluginsLoader = new Hashtable<String, PluginInfo>();
    static HashMap<String,Object> singletons = new HashMap<String, Object>();
    /**
     * Constructor
     * 
     * @param jetty
     * 
     * @throws java.rmi.RemoteException
     *             Error de comunicaciones
     */
    public AgentManagerImpl() throws java.rmi.RemoteException {
    }

    /**
     * Instanciar agente
     * 
     * @return URL del agente creado
     * @see Agent
     */
    public String handleCreateAgent(System system) throws java.rmi.RemoteException,
            es.caib.seycon.ng.exception.InternalErrorException {
    	return handleCreateAgent(system, false);
    }
    
    public String handleCreateAgentDebug(System system) throws java.rmi.RemoteException,
    	es.caib.seycon.ng.exception.InternalErrorException {
    	return handleCreateAgent(system, true);
    }

    public String handleCreateAgent(System system, boolean debug) throws java.rmi.RemoteException,
        es.caib.seycon.ng.exception.InternalErrorException {
    	Object o = singletons.get(system.getName());
    	if (o != null)
    	{
            return "/seycon/Agent/" + o.hashCode();    		
    	}
    	
        try {
            final AgentInterface agent = performCreateAgent(system);
            final String url = "/seycon/Agent/" + agent.hashCode();
            SoffidApplication.getJetty().bind(url, agent, "server");
            String serverName = Invoker.getInvoker().getUser();
            if (serverName.indexOf('\\') > 0)
            	serverName = serverName.substring(serverName.indexOf('\\')+1);
            
            if (debug)
            {
            	agent.startCaptureLog();
            	agent.setDebug(true);
            }
   
            final Runnable onClose = new Runnable ()
            		{
						public void run() {
				            try {
								SoffidApplication.getJetty().unbind(url);
							} catch (IOException e) {
							}
						}
            	
            		};
            if (agent instanceof es.caib.seycon.ng.sync.agent.Agent)
            {
            	es.caib.seycon.ng.sync.agent.Agent v1Agent = (es.caib.seycon.ng.sync.agent.Agent) agent;
            	v1Agent.setServerName(serverName);
            	v1Agent.setOnClose(onClose);
            	v1Agent.init();
            	if (v1Agent.isSingleton())
            		singletons.put(system.getName(), v1Agent);
            } else {
            	com.soffid.iam.sync.agent.Agent v2Agent = (com.soffid.iam.sync.agent.Agent) agent;
            	v2Agent.setServerName(serverName);
            	v2Agent.setOnClose(onClose);
            	v2Agent.init();
            	if (v2Agent.isSingleton())
            		singletons.put(system.getName(), v2Agent);
            }
            if (debug)
            	agent.setDebug(true);
            return url;
        } catch (Exception e) {
            log.warn("Error creating object " + system.getName(), e);
            throw new InternalErrorException("Error creando objecto " + system.getClassName(), e);
        }
    }

    public Object handleCreateLocalAgent(System system) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, InvocationTargetException,
            InternalErrorException, IOException {
    	return handleCreateLocalAgent(system, false);
    }
    
    public Object handleCreateLocalAgentDebug(System system) throws ClassNotFoundException,
        InstantiationException, IllegalAccessException, InvocationTargetException,
        InternalErrorException, IOException {
    	return handleCreateLocalAgent(system, true);

    }
    
    public Object handleCreateLocalAgent(System system, boolean debug) throws ClassNotFoundException,
        InstantiationException, IllegalAccessException, InvocationTargetException,
        InternalErrorException, IOException {
        try {
            AgentInterface agent = performCreateAgent(system);
            if (debug)
            {
            	agent.startCaptureLog();
            	agent.setDebug(true);
            }
            
            if (agent instanceof com.soffid.iam.sync.agent.Agent)
            {
            	com.soffid.iam.sync.agent.Agent v2Agent = (com.soffid.iam.sync.agent.Agent) agent;
	            v2Agent.setServerName(Config.getConfig().getHostName());
	            v2Agent.setServer(ServerServiceLocator.instance().getServerService());
	            v2Agent.init();
	            if (v2Agent.isSingleton())
	            	singletons.put(system.getName(), v2Agent);
            } else {
            	es.caib.seycon.ng.sync.agent.Agent v1Agent = (es.caib.seycon.ng.sync.agent.Agent) agent;
	            v1Agent.setServerName(Config.getConfig().getHostName());
	            v1Agent.setServer(es.caib.seycon.ng.sync.ServerServiceLocator.instance().getServerService());
	            v1Agent.init();
	            if (v1Agent.isSingleton())
	            	singletons.put(system.getName(), v1Agent);
            }
            if (debug)
            	agent.setDebug(true);
            return agent;
        } catch (Exception e) {
            log.warn("Error creating object " + system.getName(), e);
            throw new InternalErrorException("Error creando objecto " + system.getClassName(), e);
        }
    }

    private AgentInterface performCreateAgent(System system) throws IOException,
            InternalErrorException, ClassNotFoundException, InstantiationException,
            IllegalAccessException {
        String agentClass = system.getClassName();
        String version;
        Class<?> agentClassObject;
        try {
            agentClassObject = getClass().getClassLoader().loadClass(agentClass);
            version = Config.getConfig().getVersion();
        } 
        catch (ClassNotFoundException e)
        {
        	loadPlugin(agentClass);
        	PluginInfo pi = pluginsLoader.get(agentClass);
            agentClassObject = pi.classLoader.loadClass(agentClass);
            version = pi.version;
            // Obtiene el constructor
        }
        AgentInterface agent = (AgentInterface) agentClassObject.newInstance();
        if (agent instanceof es.caib.seycon.ng.sync.agent.Agent) 
        {
        	es.caib.seycon.ng.sync.agent.Agent v1Agent = (es.caib.seycon.ng.sync.agent.Agent) agent;
        	v1Agent.setDispatcher(Dispatcher.toDispatcher(system));
        	v1Agent.setAgentVersion(version);
            if (Invoker.getInvoker() == null) {
            	v1Agent.setServer(es.caib.seycon.ng.sync.ServerServiceLocator.instance().getServerService());
            } else {
                Invoker invoker = Invoker.getInvoker();
                try {
                	String user = invoker.getUser();
                	String host = user.contains("\\") ? user.substring(user.indexOf("\\")+1) : user;
                	es.caib.seycon.ng.remote.RemoteServiceLocator rsl = new es.caib.seycon.ng.remote.RemoteServiceLocator();
                	for ( String server: Config.getConfig().getServerList().split("[, ]+"))
                	{
                		try {
							URL url = new URL(server);
							if ( url.getHost().equalsIgnoreCase(host))
								rsl.setServer(server);
						} catch (Exception e) {
						}
                	}
    	            v1Agent.setServer(rsl.getServerService());
                } catch (Exception e) {
                	es.caib.seycon.ng.remote.RemoteServiceLocator rsl = new es.caib.seycon.ng.remote.RemoteServiceLocator();
    	            v1Agent.setServer(rsl.getServerService());
                }
            }
            v1Agent.setJettyServer(ServerApplication.getJetty());
        }
        else
        {
        	com.soffid.iam.sync.agent.Agent v2Agent = (com.soffid.iam.sync.agent.Agent) agent;
        	v2Agent.setSystem(system);
        	v2Agent.setAgentVersion(version);
            if (Invoker.getInvoker() == null) {
            	v2Agent.setServer(ServerServiceLocator.instance().getServerService());
            } else {
                Invoker invoker = Invoker.getInvoker();
                try {
                	String user = invoker.getUser();
                	String host = user.contains("\\") ? user.substring(user.indexOf("\\")+1) : user;
                	RemoteServiceLocator rsl = new RemoteServiceLocator();
                	for ( String server: Config.getConfig().getServerList().split("[, ]+"))
                	{
                		try {
							URL url = new URL(server);
							if ( url.getHost().equalsIgnoreCase(host))
								rsl.setServer(server);
						} catch (Exception e) {
						}
                	}
    	            v2Agent.setServer(rsl.getServerService());
                } catch (Exception e) {
    	            RemoteServiceLocator rsl = new RemoteServiceLocator();
    	            v2Agent.setServer(rsl.getServerService());
                }
            }
            v2Agent.setJettyServer(ServerApplication.getJetty());
        	
        }

        log.info("Created agent {}: {}", system.getName(), agentClass);
        return agent;
    }

    private static void loadPlugin(String agentClass) throws IOException, InternalErrorException
    {
    	synchronized (pluginsLoader)
    	{
	    	PluginInfo pi = pluginsLoader.get(agentClass);
	    	if (pi == null)
	    	{
	    		Plugin sp = getAnyServer().getPlugin(agentClass);
	            if (sp == null || sp.getContent() == null) {
	                throw new InternalErrorException("No plugin available for class " + agentClass);
	            }
	            File home = Config.getConfig().getHomeDir();
	            File plugins = new File (home, "plugins");
	            plugins.mkdir();
	            File f = new File(plugins, agentClass + "-"+ sp.getVersion()+".jar");
	            OutputStream out = new FileOutputStream(f);
	            out.write(sp.getContent());
	            out.close();
	            f.deleteOnExit();
	
	
	            pi = new PluginInfo();
	            pi.expiration = new Date (java.lang.System.currentTimeMillis() + 600000); // 10 mins cache
	            pi.name = sp.getName();
	            pi.version = sp.getVersion();
	            pi.classLoader = new AgentClassLoader(new URL[] { f.toURI().toURL() });
	    		pluginsLoader.put(agentClass, pi);
	    	} 
	    	else if (new Date().after(pi.expiration) && false)
	    	{
	    		Plugin sp = getAnyServer().getPlugin(agentClass);
	            if (sp == null || sp.getContent() == null) {
	                throw new InternalErrorException("No plugin available for class " + agentClass);
	            }
	            // Version has changed
	            if (! sp.getVersion().equals(pi.version))
	            {
	                File f = File.createTempFile("seycon-" + agentClass + "-"+ sp.getVersion(), ".jar");
	                OutputStream out = new FileOutputStream(f);
	                out.write(sp.getContent());
	                out.close();
	                f.deleteOnExit();
	
	                pi.expiration = new Date (java.lang.System.currentTimeMillis() + 600000); // 10 mins cache
	                pi.version = sp.getVersion();
	                pi.classLoader = new AgentClassLoader(new URL[] { f.toURI().toURL() });
	            }
	            else
	                pi.expiration = new Date (java.lang.System.currentTimeMillis() + 600000); // 10 mins cache
	    	}
    	}
    }

 
    /**
     * Obtener acceso al servidor Seycon. Este metodo recorre la lista de
     * servidores accesibles en la lista seycon.server.list hasta contactar con
     * uno. En caso de haber contactado previamente, no crea una nueva conexión
     * sino que reutiliza la existente.
     * 
     * @throws InternalErrorException
     *             Imposible contactar con el servidor
     * @return Acceso remoto al servidor Seycon
     * @throws IOException
     * @throws FileNotFoundException
     */
    public static ServerService getAnyServer() throws InternalErrorException,
            FileNotFoundException, IOException {
        Config config = Config.getConfig();
        if (config.isServer()) {
            return ServerServiceLocator.instance().getServerService();
        }

        Collection c = serverTable.values();
        Iterator it = c.iterator();
        while (it.hasNext()) {
            ServerInfo s = (ServerInfo) it.next();
            if (s != null && s.isValid())
                return s.getServer();
        }
        return getServer(null);
    }

    /**
     * Obtener acceso al servidor Seycon. Este metodo recorre la lista de
     * servidores accesibles en la lista seycon.server.list hasta contactar con
     * uno. En caso de haber contactado previamente, no crea una nueva conexión
     * sino que reutiliza la existente.
     * 
     * @throws InternalErrorException
     *             Imposible contactar con el servidor
     * @return Acceso remoto al servidor Seycon
     * @throws IOException
     */
    public static ServerService getServer(String name) throws InternalErrorException, IOException {
        Config config = Config.getConfig();
        if (config.isServer()) {
            if (name == null || name.equals(config.getHostName()))
                return ServerServiceLocator.instance().getServerService();
        }

        String ip = null;
        try {
            if (name != null) {
                ip = java.net.InetAddress.getByName(name).getHostAddress();
                ServerInfo si = (ServerInfo) serverTable.get(ip);
                if (si != null && si.isValid())
                    return si.getServer();
            }
        } catch (UnknownHostException e1) {
            throw new InternalErrorException(e1.toString());
        }

        ServerService robj;
        RemoteInvokerFactory factory = new RemoteInvokerFactory();
        String list[] = config.getServerList().split("[, ]+");
        Exception lastException = null;
        String lastServer = null;
        for (int i = 0; i < list.length; i++) {
            URLManager m = null;
            try {
                lastServer = list[i];
                m = new URLManager(list[i]);
                String ip2 = java.net.InetAddress.getByName(m.getServerURL().getHost())
                        .getHostAddress();
                if (ip == null || ip2.equals(ip)) {
                    log.debug("Contacting {} at {}", m.getServerURL(), ip2);
                    RemoteServiceLocator rsl = new RemoteServiceLocator(list[i]);
                    ServerInfo si = new ServerInfo(rsl.getServerService());
                    serverTable.put(ip2, si);
                    return si.getServer();
                }
            } catch (Exception e) {
                log.warn("Unable to locate server at " + m.getServerURL(), e);
                lastException = e;
            }
        }
        if (lastException == null)
            throw new InternalErrorException("Imposible contactar con el servidor SEYCON ");
        else
            throw new InternalErrorException("Imposible contactar con el servidor SEYCON " + lastServer, lastException);
    }

    /**
     * Gestionar error de comunicaciones. Debe ser invocado desde cualquier
     * agente que haya recibido un error del tipo java.rmi.RemoteException como
     * consecuencia de utilizar el objeto server obtenido a través del método
     * getServer. Al procesar este error el gestor de agentes invalidará el
     * objeto server actual, por lo que la siguiente llamada al método getServer
     * intentará conectar con cualquiera de los servidores de la lista.
     * 
     * @param re
     *            Excepcion recibida por el agente
     */
    public static void HandleRemoteException(java.rmi.RemoteException re) {
        re.printStackTrace(java.lang.System.out);
        serverTable = new Hashtable();
    }

    public Date getCertificateNotValidAfter() throws java.rmi.RemoteException {
        try {
            File f = SeyconKeyStore.getKeyStoreFile();
            if (f.canRead()) {
                KeyStore s = SeyconKeyStore.loadKeyStore(f);
                X509Certificate cert = (X509Certificate) s.getCertificate(SeyconKeyStore.MY_KEY);
                Date notAfter = cert.getNotAfter();
                if (notAfter != null)
                    return notAfter;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Override
    protected void handleReset() throws Exception {
    	log.info("Received reset request", null, null);
        SoffidApplication.shutDown();
    }

    @Override
    protected Date handleGetCertificateValidityDate() throws Exception {
        try {
            File f = SeyconKeyStore.getKeyStoreFile();
            if (f.canRead()) {
                KeyStore s = SeyconKeyStore.loadKeyStore(f);
                X509Certificate cert = (X509Certificate) s.getCertificate(SeyconKeyStore.MY_KEY);
                Date notAfter = cert.getNotAfter();
                if (notAfter != null)
                    return notAfter;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static KeyPair temporaryKey = null;
    
	@Override
	protected PublicKey handleGenerateNewKey() throws Exception {
		temporaryKey = new CertificateServer().generateNewKey();
		return temporaryKey.getPublic();
	}

	@Override
	protected void handleStoreNewCertificate(X509Certificate cert, X509Certificate root) throws Exception {
		if (temporaryKey == null)
			throw new InternalErrorException ("Error storing certificate. No private key has been generated. Maybe the agent has been restarted since the private key was generated");
		new CertificateServer().storeCertificate(temporaryKey, cert, root);
		new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
				}
				log.info("Restarting after installing new certificate", null, null);
				java.lang.System.exit(1);
			}
			
		}).start();
	}

	@Override
	protected String[] handleTailServerLog() throws Exception {
		String logPath = Config.getConfig().getLogFile().getAbsolutePath();
		LinkedList<String> s = new LinkedList<String>();
		
		BufferedReader r = new BufferedReader( new FileReader(logPath));
		for ( String line = r.readLine(); line != null; line = r.readLine())
		{
			s.add(line);
			if (s.size() > 10000)
				s.removeFirst();
		}
		return s.toArray(new String[0]);
	}

}

class ServerInfo {
    private ServerService server;
    private long timeStamp;

    public boolean isValid() {
        if (server == null)
            return false;
        long now = java.lang.System.currentTimeMillis();
        if (timeStamp + 600000L < now) // diez minutos
            return false;
        return true;
    }

    public ServerService getServer() {
        return server;
    }

    public ServerInfo(ServerService s) {
        timeStamp = java.lang.System.currentTimeMillis();
        server = s;
    }
}

class PluginInfo {
	protected String name;
	protected String version;
	protected ClassLoader classLoader;
	protected Date expiration;
}
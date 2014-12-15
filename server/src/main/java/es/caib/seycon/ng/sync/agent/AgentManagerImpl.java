// Copyright (c) 2000 Govern  de les Illes Balears
package es.caib.seycon.ng.sync.agent;

import java.rmi.server.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;

import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.ServerPlugin;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.remote.RemoteInvokerFactory;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.sync.ServerApplication;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.SeyconApplication;
import es.caib.seycon.ng.sync.intf.AgentMgr;
import es.caib.seycon.ng.sync.jetty.Invoker;
import es.caib.seycon.ng.sync.jetty.JettyServer;
import es.caib.seycon.ng.sync.servei.ServerService;
import es.caib.seycon.ssl.SeyconKeyStore;

import java.rmi.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

/**
 * Gestor de agentes. Clase que implementa en interfaz remoto (RMI) AgentMgr <BR>
 * 
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.1 $
 * @see AgentMgr
 * @see Agent
 */

public class AgentManagerImpl extends AgentManagerBaseCustom implements AgentManager {
    // private static Server server;
    private static Logger log = Log.getLogger("AgentManager");
    static Hashtable serverTable = new Hashtable();
    static Hashtable<String, PluginInfo> pluginsLoader = new Hashtable<String, PluginInfo>();

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
    public String handleCreateAgent(Dispatcher dispatcher) throws java.rmi.RemoteException,
            es.caib.seycon.ng.exception.InternalErrorException {
        try {
            Agent agent = performCreateAgent(dispatcher);
            String url = "/seycon/Agent/" + agent.hashCode();
            SeyconApplication.getJetty().bind(url, agent, "server");
            RemoteServiceLocator rsl = new RemoteServiceLocator();
            String serverName = Invoker.getInvoker().getUser();
            rsl.setServer(serverName);
            agent.setServerName(serverName);
            agent.setServer(rsl.getServerService());
            agent.init();
            return url;
        } catch (Exception e) {
            log.warn("Error creating object " + dispatcher.getCodi(), e);
            throw new InternalErrorException("Error creando objecto " + dispatcher.getNomCla(), e);
        }
    }

    public Agent handleCreateLocalAgent(Dispatcher dispatcher) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, InvocationTargetException,
            InternalErrorException, IOException {
        try {
            Agent agent = performCreateAgent(dispatcher);
            agent.setServerName(Config.getConfig().getHostName());
            agent.setServer(ServerServiceLocator.instance().getServerService());
            agent.init();
            return agent;
        } catch (Exception e) {
            log.warn("Error creating object " + dispatcher.getCodi(), e);
            throw new InternalErrorException("Error creando objecto " + dispatcher.getNomCla(), e);
        }
    }

    private Agent performCreateAgent(Dispatcher dispatcher) throws IOException,
            InternalErrorException, ClassNotFoundException, InstantiationException,
            IllegalAccessException {
        String agentClass = dispatcher.getNomCla();
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
        Agent agent = (Agent) agentClassObject.newInstance();
        agent.setDispatcher(dispatcher);
        agent.setAgentVersion(version);
        if (Invoker.getInvoker() == null) {
            agent.setServer(ServerServiceLocator.instance().getServerService());
        } else {
            Invoker invoker = Invoker.getInvoker();
            try {
	            RemoteServiceLocator rsl = new RemoteServiceLocator(invoker.getUser());
	            agent.setServer(rsl.getServerService());
            } catch (Exception e) {
	            RemoteServiceLocator rsl = new RemoteServiceLocator();
	            agent.setServer(rsl.getServerService());
            }
        }
        agent.setJettyServer(ServerApplication.getJetty());

        log.info("Created agent {}: {}", dispatcher.getCodi(), agentClass);
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
	            File f = File.createTempFile("seycon-" + agentClass + "-"+ sp.getVersion(), ".jar");
	            OutputStream out = new FileOutputStream(f);
	            out.write(sp.getContent());
	            out.close();
	            f.deleteOnExit();
	
	
	            pi = new PluginInfo();
	            pi.expiration = new Date (System.currentTimeMillis() + 600000); // 10 mins cache
	            pi.name = sp.getName();
	            pi.version = sp.getVersion();
	            pi.classLoader = new AgentClassLoader(new URL[] { f.toURI().toURL() });
	    		pluginsLoader.put(agentClass, pi);
	    	} 
	    	else if (new Date().after(pi.expiration))
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
	
	                pi.expiration = new Date (System.currentTimeMillis() + 600000); // 10 mins cache
	                pi.version = sp.getVersion();
	                pi.classLoader = new AgentClassLoader(new URL[] { f.toURI().toURL() });
	            }
	            else
	                pi.expiration = new Date (System.currentTimeMillis() + 600000); // 10 mins cache
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
        re.printStackTrace(System.out);
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
        SeyconApplication.shutDown();
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

}

class ServerInfo {
    private ServerService server;
    private long timeStamp;

    public boolean isValid() {
        if (server == null)
            return false;
        long now = System.currentTimeMillis();
        if (timeStamp + 600000L < now) // diez minutos
            return false;
        return true;
    }

    public ServerService getServer() {
        return server;
    }

    public ServerInfo(ServerService s) {
        timeStamp = System.currentTimeMillis();
        server = s;
    }
}

class PluginInfo {
	protected String name;
	protected String version;
	protected ClassLoader classLoader;
	protected Date expiration;
}
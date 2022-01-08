package com.soffid.iam.sync.jetty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.SslClientCertAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.PublisherInterface;
import com.soffid.iam.remote.RemoteServicePublisher;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.InstanceRegistrationThread;
import com.soffid.iam.sync.hub.server.HubFromServerServlet;
import com.soffid.iam.sync.hub.server.HubMonitorThread;
import com.soffid.iam.sync.hub.server.HubServlet;
import com.soffid.iam.sync.tools.FileVersionManager;
import com.soffid.iam.sync.tools.KubernetesConfig;
import com.soffid.iam.sync.web.admin.DiagnosticServlet;
import com.soffid.iam.sync.web.admin.GatewayDiagnosticServlet;
import com.soffid.iam.sync.web.admin.PlainLogServlet;
import com.soffid.iam.sync.web.admin.QueryServlet;
import com.soffid.iam.sync.web.admin.ResetServlet;
import com.soffid.iam.sync.web.admin.StatusServlet;
import com.soffid.iam.sync.web.admin.TraceIPServlet;
import com.soffid.iam.sync.web.admin.ViewLogServlet;
import com.soffid.iam.sync.web.esso.AuditPasswordQueryServlet;
import com.soffid.iam.sync.web.esso.CertificateLoginServlet;
import com.soffid.iam.sync.web.esso.ChangePasswordServlet;
import com.soffid.iam.sync.web.esso.ChangeSecretServlet;
import com.soffid.iam.sync.web.esso.CreateSessionServlet;
import com.soffid.iam.sync.web.esso.GeneratePasswordServlet;
import com.soffid.iam.sync.web.esso.GetHostAdministrationServlet;
import com.soffid.iam.sync.web.esso.GetSecretsServlet;
import com.soffid.iam.sync.web.esso.KeepaliveSessionServlet;
import com.soffid.iam.sync.web.esso.KerberosLoginServlet;
import com.soffid.iam.sync.web.esso.LogoutServlet;
import com.soffid.iam.sync.web.esso.MazingerIconsServlet;
import com.soffid.iam.sync.web.esso.MazingerMenuEntryServlet;
import com.soffid.iam.sync.web.esso.MazingerMenuServlet;
import com.soffid.iam.sync.web.esso.MazingerServlet;
import com.soffid.iam.sync.web.esso.PasswordLoginServlet;
import com.soffid.iam.sync.web.esso.SetHostAdministrationServlet;
import com.soffid.iam.sync.web.esso.UpdateHostAddress;
import com.soffid.iam.sync.web.internal.InvokerServlet;
import com.soffid.iam.sync.web.internal.PropagatePasswordServlet;
import com.soffid.iam.sync.web.internal.PublicCertServlet;
import com.soffid.iam.sync.web.internal.ServerInvokerServlet;
import com.soffid.iam.sync.web.pam.PamSessionServlet;
import com.soffid.iam.sync.web.wsso.WebSessionServlet;
import com.soffid.iam.utils.ConfigurationCache;

public class JettyServer implements PublisherInterface 
{
    String host;
    int port;
    Server server;
    public Server getServer() {
        return server;
    }

    Logger log = Log.getLogger("JettyServer");
    private ServletContextHandler ctx;
    private ConstraintSecurityHandler sh;

    private ServletContextHandler administracioContext;
    private ConstraintSecurityHandler basicSecurityHandler;
    private ServletContextHandler downloadContext;
    private ServletContextHandler wsContext;
	private Map<String,Object> remoteServices = new Hashtable<String, Object>();
	private ServletContextHandler kubernetesContext;
	private Server kubernetesServer;

    public JettyServer(String host, int port) {
        super();
        this.host = host;
        this.port = port;
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    public void start() throws Exception {
    	boolean k8s = new KubernetesConfig().isKubernetes();
    	
        server = new Server();

        // Dimensionar el pool
        Config config = Config.getConfig();
        int threadNumber;
        if (config.isServer()) {
            String threads = config.getServerService().getConfig("seycon.jetty.threads");
            if (threads == null)
                threadNumber = 30;
            else
                threadNumber = Integer.decode(threads).intValue();
        } else
            threadNumber = 30;
        QueuedThreadPool pool = new QueuedThreadPool();
        pool.setLowThreadsThreshold(2);
        pool.setMaxThreads(threadNumber);
        server = new Server(pool);

        if (host == null)
        	addConnector(null);
        else
        {
        	InetAddress[] addrList = InetAddress.getAllByName(host);
        	if (addrList.length == 0)
        		addConnector(host);
        	else
        	{
	        	for (InetAddress addr: addrList)
	        	{
	        		addConnector(addr.getHostAddress());
	        	}
        	}
        }
        

        
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        // basic authentication
        administracioContext = new ServletContextHandler(contexts, "/");
        administracioContext.addFilter(DiagFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        administracioContext.addFilter(InvokerFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        administracioContext.setErrorHandler(new ErrorServlet());
        if (config.isServer())
        {
            basicSecurityHandler = new ConstraintSecurityHandler();
            basicSecurityHandler.setLoginService(new SeyconBasicRealm());
            basicSecurityHandler.setRealmName("Soffid user");
            basicSecurityHandler.setAuthenticator(new BasicAuthenticator());
            basicSecurityHandler.setAuthMethod("BASIC");
            administracioContext.setSecurityHandler(basicSecurityHandler);
            if (k8s && config.isServer()) {
            	log.info("Starting kubernetes internal listener", null, null);
            	kubernetesContext = createKubernetesServer(host);
            	kubernetesContext.addFilter(DiagFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
            	kubernetesContext.addFilter(InvokerFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
            	
                ConstraintSecurityHandler basicSecurityHandler2 = new ConstraintSecurityHandler();
                basicSecurityHandler2.setLoginService(new SeyconBasicRealm());
                basicSecurityHandler2.setRealmName("Soffid user");
                basicSecurityHandler2.setAuthenticator(new BasicAuthenticator());
                basicSecurityHandler2.setAuthMethod("BASIC");
            	kubernetesContext.setSecurityHandler(basicSecurityHandler2);
            	kubernetesContext.setErrorHandler(new ErrorServlet());
            	kubernetesServer.start();
            	
    			publish(ServerServiceLocator.instance().getServerService(), "/seycon/ServerService-en", "SEU_CONSOLE");
            } 
        }

        // certificate authentication
        ctx = new ServletContextHandler(contexts, "/seycon");
        ctx.addFilter(DiagFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        ctx.addFilter(InvokerFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        ctx.setErrorHandler(new ErrorServlet());
        
        sh = new ConstraintSecurityHandler();
        sh.setLoginService(new SeyconUserRealm());
        sh.setAuthMethod("CLIENT-CERT");
        SoffidSslContextFactory sslContextFactory = new SoffidSslContextFactory();
        sslContextFactory.setTrustStore(loadKeyStore());
		sslContextFactory.setKeyStore(loadKeyStore());
        sslContextFactory.setKeyStorePassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sslContextFactory.setKeyManagerPassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sh.setAuthenticator(new SslClientCertAuthenticator(sslContextFactory));

        ctx.setSecurityHandler(sh);

        // Public download site
        downloadContext = new ServletContextHandler(contexts, "/downloadLibrary");
        downloadContext.addFilter(DiagFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        downloadContext.addFilter(InvokerFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        downloadContext.setErrorHandler(new ErrorServlet());
        
        if (config.isServer()) 
        {
            new RemoteServicePublisher().publish (ServerServiceLocator.instance(), this);
        }
        server.start();
    }

	public boolean enableProxyProtocol() {
		return null != System.getenv("PROXY_PROTOCOL_ENABLED");
	}

    private ServletContextHandler createKubernetesServer(String host) throws FileNotFoundException, IOException {   	

        QueuedThreadPool pool = new QueuedThreadPool();
        pool.setLowThreadsThreshold(2);
        pool.setMaxThreads(10);
        kubernetesServer = new Server(pool);

    	HttpConfiguration httpConfig = new HttpConfiguration();
    	httpConfig.setSendServerVersion(false);
    	HttpConnectionFactory http11 = new HttpConnectionFactory();
    	ServerConnector connector = new ServerConnector(kubernetesServer, http11);
    	connector.setPort(port+1);

        connector.setAcceptedReceiveBufferSize( 64 * 1024);
        connector.setAcceptedSendBufferSize( 64 * 1024);
        String s =  "server".equals(Config.getConfig().getRole()) ?
        		ConfigurationCache.getMasterProperty("soffid.syncserver.bufferSize") :
        		null ;
        if (s != null) {
        	connector.setAcceptedReceiveBufferSize( Integer.parseInt(s));
            connector.setAcceptedSendBufferSize( Integer.parseInt(s));
        }
        
        if (host != null)
        	connector.setHost(host);
        String hostName = InetAddress.getLocalHost().getHostName();
        String hostAddress = InetAddress.getLocalHost().getHostAddress();

        String url = "http://"+hostAddress+":"+Integer.toString(port+1);
        log.info("Listening on {}", url, null);
        connector.setAcceptQueueSize(10);
        connector.setIdleTimeout(2000);

        kubernetesServer.addConnector(connector);
        
        new InstanceRegistrationThread(hostName, url).start();;

        return new ServletContextHandler(kubernetesServer, "/");
    }

	public void startGateway() throws Exception {
        if ("true".equals(System.getProperty("soffid.gateway.debug")))
        {
        	log.info("Starting hub mointor", null, null);
        	new HubMonitorThread().start();
        }


        String threads = System.getProperty("seycon.jetty.threads");
        int threadNumber = 250;
        try {
        	threadNumber = Integer.parseInt(threads);
        } catch (Exception e) {}
        QueuedThreadPool pool = new QueuedThreadPool();
        pool.setLowThreadsThreshold(2);
        pool.setMaxThreads(threadNumber);
        server = new Server(pool);

       	addConnector(null);

       	ContextHandlerCollection handlers = new ContextHandlerCollection();
        // basic authentication
        administracioContext = new ServletContextHandler(handlers, "/");
        administracioContext.addFilter(DiagFilter.class, "/*",  EnumSet.of(DispatcherType.REQUEST));
        administracioContext.addFilter(InvokerFilter.class, "/*",  EnumSet.of(DispatcherType.REQUEST));
        administracioContext.setErrorHandler(new ErrorServlet());
        
        bindAdministrationServlet("/gw-diag", null, GatewayDiagnosticServlet.class);

        // certificate authentication
        ctx = new ServletContextHandler(handlers, "/seycon");
        ctx.addFilter(DiagFilter.class, "/*",  EnumSet.of(DispatcherType.REQUEST));
        ctx.addFilter(InvokerFilter.class, "/*",  EnumSet.of(DispatcherType.REQUEST));
		ctx.addServlet( HubFromServerServlet.class, "/*");
		ctx.setErrorHandler(new ErrorServlet());
        
        sh = new ConstraintSecurityHandler();
        sh.setLoginService(new SeyconUserRealm());
        sh.setAuthMethod("CLIENT-CERT");
        SoffidSslContextFactory sslContextFactory = new SoffidSslContextFactory();
        sslContextFactory.setTrustStore(loadKeyStore());
        sslContextFactory.setKeyStore(loadKeyStore());
        sslContextFactory.setKeyStorePassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sslContextFactory.setKeyManagerPassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sh.setAuthenticator(new SslClientCertAuthenticator(sslContextFactory));

        ctx.setSecurityHandler(sh);

        server.setHandler(handlers);

        bindServlet("/hub",  Constraint.__CERT_AUTH, new String[] { "agent", "server", "remote"} ,  ctx, HubServlet.class);

        server.start();
        
    }

    private void addConnector(String ip) throws IOException, FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    	HttpConfiguration httpConfig = new HttpConfiguration();
    	httpConfig.setSendServerVersion(false);
    	HttpConnectionFactory http11 = new HttpConnectionFactory();

    	// Configure the SslContextFactory with the keyStore information.
    	SoffidSslContextFactory sslContextFactory = new SoffidSslContextFactory();
    	
    	KeyStore ks = loadKeyStore();
    	sslContextFactory.setKeyStore(ks);
    	sslContextFactory.setTrustStore(ks);
        sslContextFactory.setKeyManagerPassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sslContextFactory.setKeyStorePassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sslContextFactory.setTrustStorePassword(SeyconKeyStore.getKeyStorePassword().getPassword());
        sslContextFactory.setWantClientAuth(true);
    	
    	// The ConnectionFactory for TLS.
    	SslConnectionFactory tls = new SslConnectionFactory(sslContextFactory, http11.getProtocol());
    	ServerConnector connector;
    	if (enableProxyProtocol()) {
    		String trustedProxy = System.getenv("PROXY_PROTOCOL_ENABLED");
    		if ("true".equalsIgnoreCase(trustedProxy)) {
	    		log.warn("Accepting proxy requests from ANY server. It is a potential security vulnerability", null, null);
	    		ProxyConnectionFactory proxy = new ProxyConnectionFactory(tls.getProtocol());
	    		connector = new ServerConnector(server, proxy, tls, http11);
    		} else {
    			log.info("Accepting proxy requests from {}", trustedProxy, null);
	    		ProxyConnectionFactory proxy = new ProxyConnectionFactory(tls.getProtocol(), trustedProxy);
	    		connector = new ServerConnector(server, proxy, tls, http11);
    		}
    	} else {
    		connector = new ServerConnector(server, tls, http11);
    	}
    	connector.setPort(port);

        connector.setAcceptedReceiveBufferSize( 64 * 1024);
        connector.setAcceptedSendBufferSize( 64 * 1024);
        String s =  "server".equals(Config.getConfig().getRole()) ?
        		ConfigurationCache.getMasterProperty("soffid.syncserver.bufferSize") :
        		null ;
        if (s != null) {
        	connector.setAcceptedReceiveBufferSize( Integer.parseInt(s));
            connector.setAcceptedSendBufferSize( Integer.parseInt(s));
        }
        
        if (ip != null) 
        {
        	connector.setHost(ip);
        }
        log.info("Listening on https://{}:{}/", ip == null ? "0.0.0.0": ip, new Integer(port));
        connector.setAcceptQueueSize(10);
        connector.setShutdownIdleTimeout(100);

        server.addConnector(connector);
	}

	public KeyStore loadKeyStore() throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		KeyStore keyStore = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getKeyStoreFile());
        try 
        {
        	List<String> keys = new LinkedList<String>();
        	for (Enumeration<String> e = keyStore.aliases(); e.hasMoreElements();)
        	{
        		String alias = e.nextElement();
        		if ( keyStore.isKeyEntry(alias) && ! alias.equalsIgnoreCase(SeyconKeyStore.MY_KEY))
        			keys.add(alias);
        	}
        	for (String key: keys)
        		keyStore.deleteEntry(key);
        } catch (Exception e) {
        }
        return keyStore;
	}

    public void bindAdministrationServlet(String url, String[] rol, Class servletClass) {
        bindServlet (url, Constraint.__BASIC_AUTH, rol,
        		kubernetesContext != null ? kubernetesContext: administracioContext, servletClass);
    }

    public void bindEssoServlet(String url, String[] rol, Class servletClass) {
        bindServlet (url, Constraint.__BASIC_AUTH, rol, administracioContext, servletClass);
    }

    public void bindDownloadServlet(String url,  String [] rol, Class servletClass) {
        bindServlet (url, Constraint.__CERT_AUTH, rol, downloadContext, servletClass);
    }

    public void bindSeyconServlet(String url,  String [] rol, Class servletClass) {
        bindServlet (url, Constraint.__CERT_AUTH, rol, ctx, servletClass);
    }

    public synchronized void bindServlet(String url, String constraintName, String [] rol, ServletContextHandler context, Class servletClass) {
        log.debug("Binding servlet {} restricted to rol [{}]", url, rol);
        if (context.getAttribute(url) == null) {
            ServletHolder holder = context.addServlet(servletClass, url);
            holder.setInitParameter("target", url);
            if (rol != null && rol.length!=0) {
                Constraint constraint = new Constraint(constraintName, "Soffid authentication");

                constraint.setRoles(rol);
                constraint.setAuthenticate(true);
                
                ConstraintMapping constraintMapping = new ConstraintMapping();
                constraintMapping.setConstraint(constraint);
                constraintMapping.setPathSpec(url);

                ConstraintSecurityHandler csh = (ConstraintSecurityHandler) context.getSecurityHandler();
                csh.addConstraintMapping(constraintMapping);
            }
        }
    }


    public void bind(URL httpURL, Object target, String rol) {
        String s = httpURL.getFile();
        bind(s, target, rol);
    }
    
    public void bindSEU(URL httpURL, Object target, String rol) {
        String s = httpURL.getFile();
        bindSEU(s, target, rol);
    }

    public synchronized void bind(String url, Object target, String rol) {
        log.info("Binding {} restricted to rol [{}]", url, rol);
        if (url.startsWith("/seycon"))
            url = url.substring(7);
        else if (url.startsWith("seycon"))
            url = url.substring(6);
        
        Config config;
        try {
			config = Config.getConfig();
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
        
        if ("remote".equals(config.getRole()))
        {
        	remoteServices .put(url, target);
        }
        else
        {
        	if (ctx.getAttribute(url) == null) {
        		ctx.setAttribute(url, target);
        		ServletHolder holder = null;
        		if (config.isServer()) {
        			holder = ctx.addServlet( ServerInvokerServlet.class, url);
        		}
        		else {
        			holder = ctx.addServlet( InvokerServlet.class, url);
        		}
        		
        		
        		holder.setInitParameter("target", url);
        		if (rol != null) {
        			Constraint constraint = new Constraint();
        			constraint.setName(Constraint.__CERT_AUTH);
        			constraint.setRoles(new String[] { rol });
        			constraint.setAuthenticate(true);
        			
        			ConstraintMapping cm = new ConstraintMapping();
        			cm.setConstraint(constraint);
        			cm.setPathSpec(url);
        			
        			sh.addConstraintMapping(cm);
        		}
        	} else {
        		ctx.setAttribute(url, target);
        	}
        	
        }
    }
    
    /**
     * Fem un binding específic per al SEU emprant el context administracioContext
     * @param url
     * @param target
     * @param rol
     */
    public synchronized void bindSEU(String url, Object target, String rol) {
        log.info("Binding {} restricted to rol [{}]", url, rol);
        if (url.startsWith("/seycon"))
            url = url.substring(7);
        if (administracioContext.getAttribute(url) == null) {
        	administracioContext.setAttribute(url, target);

        	ServletHolder holder = null;
        	Config config;
			try {
				config = Config.getConfig();
			} catch (FileNotFoundException e) {
				throw new SecurityException("Configuration file not found");
			} catch (IOException e) {
				throw new SecurityException("IO error while reading configuration file");
			}
            if (config.isServer()) {
            	holder = administracioContext.addServlet( ServerInvokerServlet.class, url);
            }
            else {
            	holder = administracioContext.addServlet( InvokerServlet.class, url);
            }        	
            holder.setInitParameter("target", url);
        } else {
        	administracioContext.setAttribute(url, target);
        }
    }
    

    public void bindAdministrationWeb() throws FileNotFoundException, IOException {
        if (Config.getConfig().isServer())
        {
            bindAdministrationServlet("/viewLog", new String[]{"SC_DIAGNOSTIC", "SEU_ADMINISTRADOR"}, ViewLogServlet.class);
            bindAdministrationServlet("/reset", new String[]{"SEU_ADMINISTRADOR"}, ResetServlet.class);
            bindPublicWeb();
        }
        bindAdministrationServlet("/status", null, StatusServlet.class);
        bindServlet ("/trace-ip", Constraint.__BASIC_AUTH, null, administracioContext, TraceIPServlet.class);
    }

    private void bindPublicWeb() throws FileNotFoundException, IOException {
        if (Config.getConfig().isServer())
        {
            bindEssoServlet("/query/*", null, QueryServlet.class);
            bindEssoServlet("/propagatepass", null, PropagatePasswordServlet.class);
            bindEssoServlet("/changepass", null, ChangePasswordServlet.class);
            bindEssoServlet("/login", null, KerberosLoginServlet.class);
            bindEssoServlet("/logout", null, LogoutServlet.class);
            bindEssoServlet("/passwordLogin", null, PasswordLoginServlet.class);
            bindEssoServlet("/kerberosLogin", null, KerberosLoginServlet.class);
            bindEssoServlet("/certificateLogin", null, CertificateLoginServlet.class);
            bindEssoServlet("/keepAliveSession", null, KeepaliveSessionServlet.class);
            bindEssoServlet("/getSecrets", null, GetSecretsServlet.class);
            bindEssoServlet("/createsession", null, CreateSessionServlet.class);
            bindEssoServlet("/getmazingerconfig", null, MazingerServlet.class);
            bindEssoServlet("/getapplications", null, MazingerMenuServlet.class);
            bindEssoServlet("/getapplication", null, MazingerMenuEntryServlet.class);
            bindEssoServlet("/getapplicationicon", null, MazingerIconsServlet.class);
            bindEssoServlet("/gethostadmin", null, GetHostAdministrationServlet.class);
            bindEssoServlet("/updateHostAddress", null, UpdateHostAddress.class);
            bindEssoServlet("/setSecret", null, ChangeSecretServlet.class);
            bindEssoServlet("/generatePassword", null, GeneratePasswordServlet.class);
            bindEssoServlet("/auditPassword", null, AuditPasswordQueryServlet.class);
            bindEssoServlet("/sethostadmin", null, SetHostAdministrationServlet.class);
            bindEssoServlet("/websession", null, WebSessionServlet.class);
            bindEssoServlet("/pam-notify", null, PamSessionServlet.class);
            bindEssoServlet("/cert", null, PublicCertServlet.class);
            try {
            	Class cl = Class.forName("com.soffid.iam.doc.servlet.NASServlet");
                bindEssoServlet("/doc", null, cl);
            } catch (ClassNotFoundException e) 
            {
            }
        }
    }

    public void bindDiagnostics() {
        bindAdministrationServlet("/diag", null, DiagnosticServlet.class);
        bindAdministrationServlet("/log", null, PlainLogServlet.class);
    }


    public void publish(Object target, String path,
            String role) throws IOException {
    	Server s = kubernetesServer != null &&  "SEU_CONSOLE".equals(role) ? kubernetesServer: server;
        Handler[] handlers = s.getChildHandlers();
        ServletContextHandler handler = null;
        int length = 0;
        if (! path.startsWith ("/"))
        	path = "/" + path;
        for (int i = 0; i < handlers.length; i++) {
            if (handlers[i] instanceof ServletContextHandler) {
            	ServletContextHandler ctx = (ServletContextHandler) handlers[i];
                if ("/".equals (ctx.getContextPath()) && length == 0) {
                	handler = ctx;
                } else if (path.startsWith(ctx.getContextPath())) {
                    if (length < ctx.getContextPath().length()) {
                        length = ctx.getContextPath().length();
                        handler = ctx;
                    }
                }
            }
        }

        if (handler == null)
            throw new IOException("No context found");
        log.info("Binding {} restricted to rol [{}]", path, role);
        path = path.substring(length);
        if (handler.getAttribute(path) == null) {
            handler.setAttribute(path, target);

            ServletHolder holder = null;
            Config config;
            try {
                config = Config.getConfig();
            } catch (FileNotFoundException e) {
                throw new SecurityException("Configuration file not found");
            } catch (IOException e) {
                throw new SecurityException(
                        "IO error while reading configuration file");
            }
            if (config.isServer()) {
                holder = handler.addServlet(ServerInvokerServlet.class, path);
            } else {
                holder = handler.addServlet(InvokerServlet.class, path);
            }
            holder.setInitParameter("target", path);
            if (role != null && role.length() > 0) {
                Constraint constraint = new Constraint();
                constraint.setName("Resource "+path);
                constraint.setRoles(new String[] { role });
                constraint.setAuthenticate(true);

                ConstraintMapping cm = new ConstraintMapping();
                cm.setConstraint(constraint);
                cm.setConstraint(constraint);
                cm.setPathSpec(path);

                ConstraintSecurityHandler csh = (ConstraintSecurityHandler) handler.getSecurityHandler();
                csh.addConstraintMapping(cm);
            }
        } else {
            handler.setAttribute(path, target);
        }
    }

	public void unbind(String url) throws IOException {
        if (url.startsWith("/seycon"))
            url = url.substring(7);
        else if (url.startsWith("seycon"))
            url = url.substring(6);
        
        Config config;
        try {
			config = Config.getConfig();
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
        
        if ("remote".equals(config.getRole()))
        {
        	remoteServices .remove(url);
        }
        else
        {
        	ctx.removeAttribute(url);
        }
	}

	public Object getServiceHandler(String url) {
        if (url.startsWith("/seycon"))
            url = url.substring(7);
        else if (url.startsWith("seycon"))
            url = url.substring(6);
        Config config;
        try {
			config = Config.getConfig();
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
        
        if ("remote".equals(config.getRole()))
        	return remoteServices .get(url);
        else
        	return null;
	}

}

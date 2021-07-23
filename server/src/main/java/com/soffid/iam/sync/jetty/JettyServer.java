package com.soffid.iam.sync.jetty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.security.BasicAuthenticator;
import org.mortbay.jetty.security.ClientCertAuthenticator;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.PublisherInterface;
import com.soffid.iam.remote.RemoteServicePublisher;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.hub.server.HubFromServerServlet;
import com.soffid.iam.sync.hub.server.HubMonitorThread;
import com.soffid.iam.sync.hub.server.HubServlet;
import com.soffid.iam.sync.tools.FileVersionManager;
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
    private Context ctx;
    private SecurityHandler sh;

    private Context administracioContext;
    private SecurityHandler basicSecurityHandler;
    private Context downloadContext;
    private WebAppContext wsContext;
	private Map<String,Object> remoteServices = new Hashtable<String, Object>();

    public JettyServer(String host, int port) {
        super();
        this.host = host;
        this.port = port;
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    public void start() throws Exception {
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
        MyQueuedThreadPool pool = new MyQueuedThreadPool();
        pool.setLowThreads(2);
        pool.setMaxThreads(threadNumber);
        server.setThreadPool(pool);

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
        // Afegir el bind alternativo
        if (config.isServer()) {
            String altPort = config.getServerService().getConfig("seycon.https.alternate.port");
            if (altPort != null)
            {
                log.info("Listening on additional port {}", altPort, null);
                MySslSocketConnector connector2 = new MySslSocketConnector();
                connector2.setPort(Integer.decode(altPort));
                if (host != null)
                	connector2.setHost(host);
                connector2.setKeystore(SeyconKeyStore.getKeyStoreFile()
                        .getAbsolutePath());
                connector2.setPassword(SeyconKeyStore.getKeyStorePassword()
                         .getPassword());
                connector2.setKeyPassword(SeyconKeyStore.getKeyStorePassword()
                        .getPassword());
                connector2.setKeystoreType(SeyconKeyStore.getKeyStoreType());
                connector2.setWantClientAuth(false);
                connector2.setAcceptors(2);
                connector2.setAcceptQueueSize(10);
                connector2.setMaxIdleTime(5000);
                server.addConnector(connector2);
            }
        }
        

        // basic authentication
        administracioContext = new Context(server, "/");
        administracioContext.addFilter(DiagFilter.class, "/*", Handler.REQUEST);
        administracioContext.addFilter(InvokerFilter.class, "/*", Handler.REQUEST);

        if (config.isServer())
        {
            basicSecurityHandler = new SecurityHandler();
            basicSecurityHandler.setUserRealm(new SeyconBasicRealm());
            basicSecurityHandler.setAuthenticator(new BasicAuthenticator());
    
            basicSecurityHandler.setAuthMethod("BASIC");
    
            administracioContext.setSecurityHandler(basicSecurityHandler);
        }

        // certificate authentication
        ctx = new Context(server, "/seycon");
        ctx.addFilter(DiagFilter.class, "/*", Handler.REQUEST);
        ctx.addFilter(InvokerFilter.class, "/*", Handler.REQUEST);

        sh = new SecurityHandler();
        sh.setUserRealm(new SeyconUserRealm());
        sh.setAuthMethod("CLIENT-CERT");
        sh.setAuthenticator(new ClientCertAuthenticator());

        ctx.setSecurityHandler(sh);

        // certificate authentication para download
        downloadContext = new Context(server, "/downloadLibrary");
        downloadContext.addFilter(DiagFilter.class, "/*", Handler.REQUEST);
        downloadContext.addFilter(InvokerFilter.class, "/*", Handler.REQUEST);

        HandlerCollection handlers = new HandlerCollection();

        File ws = new FileVersionManager().getInstalledFile("seycon-webservice");
        if (ws != null) {
            wsContext = new WebAppContext(ws.toURI().toURL().toExternalForm(), "/ws");
            handlers.setHandlers(new Handler[] { ctx, downloadContext, wsContext, 
                    administracioContext,
                    new DefaultHandler() });
        } else {
            handlers.setHandlers(new Handler[] { ctx, downloadContext, 
                    administracioContext,
                    new DefaultHandler() });
        }
            

        

        server.setHandler(handlers);

        if (config.isServer()) 
        {
            new RemoteServicePublisher().publish (ServerServiceLocator.instance(), this);
        }
        server.start();
    }

    public void startGateway() throws Exception {
        if ("true".equals(System.getProperty("soffid.gateway.debug")))
        {
        	log.info("Starting hub mointor", null, null);
        	new HubMonitorThread().start();
        }

        server = new Server();

        String threads = System.getProperty("seycon.jetty.threads");
        int threadNumber = 250;
        try {
        	threadNumber = Integer.parseInt(threads);
        } catch (Exception e) {}
        MyQueuedThreadPool pool = new MyQueuedThreadPool();
        pool.setLowThreads(2);
        pool.setMaxThreads(threadNumber);
        server.setThreadPool(pool);

       	addConnector(null);

        // basic authentication
        administracioContext = new Context(server, "/");
        administracioContext.addFilter(DiagFilter.class, "/*", Handler.REQUEST);
        administracioContext.addFilter(InvokerFilter.class, "/*", Handler.REQUEST);
        bindAdministrationServlet("/gw-diag", null, GatewayDiagnosticServlet.class);

        // certificate authentication
        ctx = new Context(server, "/seycon");
        ctx.addFilter(DiagFilter.class, "/*", Handler.REQUEST);
        ctx.addFilter(InvokerFilter.class, "/*", Handler.REQUEST);
		ctx.addServlet( HubFromServerServlet.class, "/*");

        sh = new SecurityHandler();
        sh.setUserRealm(new SeyconUserRealm());
        sh.setAuthMethod("CLIENT-CERT");
        sh.setAuthenticator(new ClientCertAuthenticator());

        ctx.setSecurityHandler(sh);

        HandlerCollection handlers = new HandlerCollection();

        handlers.setHandlers(new Handler[] { ctx,  
                    administracioContext,
                    new DefaultHandler() });
            
        server.setHandler(handlers);

        bindServlet("/hub",  Constraint.__CERT_AUTH, new String[] { "agent", "server", "remote"} ,  ctx, HubServlet.class);

        server.start();
        
    }

    private void addConnector(String ip) throws IOException, FileNotFoundException {
		MySslSocketConnector connector = new MySslSocketConnector();
        connector.setPort(port);

        connector.setRequestBufferSize( 64 * 1024);
        connector.setHeaderBufferSize( 64 * 1024);
        String s =  "server".equals(Config.getConfig().getRole()) ?
        		ConfigurationCache.getMasterProperty("soffid.syncserver.bufferSize") :
        		null ;
        if (s != null) {
        	connector.setRequestBufferSize( Integer.parseInt(s));
            connector.setHeaderBufferSize( Integer.parseInt(s));
        }
        
        if (ip != null) 
        {
        	connector.setHost(ip);
        }
        log.info("Listening on https://{}:{}/", ip == null ? "0.0.0.0": ip, new Integer(port));
        connector.setKeystore(SeyconKeyStore.getKeyStoreFile()
                .getAbsolutePath());
        connector.setPassword(SeyconKeyStore.getKeyStorePassword()
                .getPassword());
        connector.setKeyPassword(SeyconKeyStore.getKeyStorePassword()
                .getPassword());
        connector.setKeystoreType(SeyconKeyStore.getKeyStoreType());
        connector.setWantClientAuth(true);
        connector.setAcceptors(2);
        connector.setAcceptQueueSize(10);
        connector.setMaxIdleTime(2000);
        connector.setLowResourceMaxIdleTime(500);
        connector.setSoLingerTime(100);

        server.addConnector(connector);
	}

    public void bindAdministrationServlet(String url, String[] rol, Class servletClass) {
        bindServlet (url, Constraint.__BASIC_AUTH, rol, administracioContext, servletClass);
    }

    public void bindDownloadServlet(String url,  String [] rol, Class servletClass) {
        bindServlet (url, Constraint.__CERT_AUTH, rol, downloadContext, servletClass);
    }

    public void bindSeyconServlet(String url,  String [] rol, Class servletClass) {
        bindServlet (url, Constraint.__CERT_AUTH, rol, ctx, servletClass);
    }

    public synchronized void bindServlet(String url, String constraintName, String [] rol,Context context, Class servletClass) {
        log.debug("Binding servlet {} restricted to rol [{}]", url, rol);
        if (context.getAttribute(url) == null) {
            ServletHolder holder = context.addServlet(servletClass,
                    url);
            holder.setInitParameter("target", url);
            if (rol != null && rol.length!=0) {
                Constraint constraint = new Constraint();
                constraint.setName(constraintName);
                constraint.setRoles( rol );
                constraint.setAuthenticate(true);

                ConstraintMapping cm = new ConstraintMapping();
                cm.setConstraint(constraint);
                cm.setPathSpec(url);

                ConstraintMapping array[] = context.getSecurityHandler().getConstraintMappings();

                ConstraintMapping array2[];
                if (array != null) {
                    array2 = new ConstraintMapping[array.length + 1];
                    System.arraycopy(array, 0, array2, 0, array.length);
                } else {
                    array2 = new ConstraintMapping[1];
                }
                array2[array2.length - 1] = cm;
                context.getSecurityHandler().setConstraintMappings(array2);
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
        			
        			ConstraintMapping array[] = sh.getConstraintMappings();
        			
        			ConstraintMapping array2[];
        			if (array != null) {
        				array2 = new ConstraintMapping[array.length + 1];
        				System.arraycopy(array, 0, array2, 0, array.length);
        			} else {
        				array2 = new ConstraintMapping[1];
        			}
        			array2[array2.length - 1] = cm;
        			sh.setConstraintMappings(array2);
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
        bindAdministrationServlet("/trace-ip", null, TraceIPServlet.class);
    }

    private void bindPublicWeb() throws FileNotFoundException, IOException {
        if (Config.getConfig().isServer())
        {
            bindAdministrationServlet("/query/*", null, QueryServlet.class);
            bindAdministrationServlet("/propagatepass", null, PropagatePasswordServlet.class);
            bindAdministrationServlet("/changepass", null, ChangePasswordServlet.class);
            bindAdministrationServlet("/login", null, KerberosLoginServlet.class);
            bindAdministrationServlet("/logout", null, LogoutServlet.class);
            bindAdministrationServlet("/passwordLogin", null, PasswordLoginServlet.class);
            bindAdministrationServlet("/kerberosLogin", null, KerberosLoginServlet.class);
            bindAdministrationServlet("/certificateLogin", null, CertificateLoginServlet.class);
            bindAdministrationServlet("/keepAliveSession", null, KeepaliveSessionServlet.class);
            bindAdministrationServlet("/getSecrets", null, GetSecretsServlet.class);
            bindAdministrationServlet("/createsession", null, CreateSessionServlet.class);
            bindAdministrationServlet("/getmazingerconfig", null, MazingerServlet.class);
            bindAdministrationServlet("/getapplications", null, MazingerMenuServlet.class);
            bindAdministrationServlet("/getapplication", null, MazingerMenuEntryServlet.class);
            bindAdministrationServlet("/getapplicationicon", null, MazingerIconsServlet.class);
            bindAdministrationServlet("/gethostadmin", null, GetHostAdministrationServlet.class);
            bindAdministrationServlet("/updateHostAddress", null, UpdateHostAddress.class);
            bindAdministrationServlet("/setSecret", null, ChangeSecretServlet.class);
            bindAdministrationServlet("/generatePassword", null, GeneratePasswordServlet.class);
            bindAdministrationServlet("/auditPassword", null, AuditPasswordQueryServlet.class);
            bindAdministrationServlet("/sethostadmin", null, SetHostAdministrationServlet.class);
            bindAdministrationServlet("/websession", null, WebSessionServlet.class);
            bindAdministrationServlet("/pam-notify", null, PamSessionServlet.class);
            bindAdministrationServlet("/cert", null, PublicCertServlet.class);
            try {
            	Class cl = Class.forName("com.soffid.iam.doc.servlet.NASServlet");
                bindAdministrationServlet("/doc", null, cl);
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
        Handler[] handlers = server.getChildHandlers();
        Context handler = null;
        int length = 0;
        if (! path.startsWith ("/"))
        	path = "/" + path;
        for (int i = 0; i < handlers.length; i++) {
            if (handlers[i] instanceof Context) {
                Context ctx = (Context) handlers[i];
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
                cm.setPathSpec(path);

                ConstraintMapping array[] = handler.getSecurityHandler().getConstraintMappings();

                ConstraintMapping array2[];
                if (array != null) {
                    array2 = new ConstraintMapping[array.length + 1];
                    System.arraycopy(array, 0, array2, 0, array.length);
                } else {
                    array2 = new ConstraintMapping[1];
                }
                array2[array2.length - 1] = cm;
                handler.getSecurityHandler().setConstraintMappings(array2);
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

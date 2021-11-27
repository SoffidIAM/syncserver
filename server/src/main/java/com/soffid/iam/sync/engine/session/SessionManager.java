package com.soffid.iam.sync.engine.session;

import es.caib.seycon.ng.comu.TipusSessio;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.LogonDeniedException;
import es.caib.seycon.ng.exception.TooManySessionsException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;

import java.rmi.server.*;
import java.rmi.*;
import java.net.*;
import java.io.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.config.Config;
import com.soffid.iam.remote.URLManager;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.ssl.AlwaysTrustConnectionFactory;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.ChangePasswordNotification;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.engine.socket.SSOServer;
import com.soffid.iam.sync.engine.socket.SSOThread;
import com.soffid.iam.utils.ConfigurationCache;

/**
 * Controla el LOGON y LOGOFF de las sesiones SSO. Los clientes acceden a ella a
 * través de {@link SSOThread} o {@link SSOServer}.
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.1 $
 */

public class SessionManager extends Thread {
    private static final int DEFAULT_NUMBER_OF_THREADS = 10;
    static Logger log = Log.getLogger("Daemon"); //$NON-NLS-1$
    /** nombre de host */
    protected String hostName;
    private NetworkService xarxaService;
    private SessionService sessioService;
    private UserService usuariService;
    static LinkedList<DuplicateSessionNotification> notifications = new LinkedList<DuplicateSessionNotification>();

    static ConnectionPool pool = ConnectionPool.getPool();

    static SessionManager theDaemon = null;

    /**
     * Constructor
     */
    public SessionManager() {
        theDaemon = this;
        setName("SSO Daemon"); //$NON-NLS-1$
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            hostName = "Unknown"; //$NON-NLS-1$
        }
        xarxaService = ServerServiceLocator.instance().getNetworkService();
        sessioService = ServerServiceLocator.instance().getSessionService();
        usuariService = ServerServiceLocator.instance().getUserService();
    }

    static public SessionManager getSessionManager() {
        return theDaemon;
    }

    /**
     * Método principal del Daemon. <LI>Crea en logonServer</LI> <LI>Cada minuto
     * inicia la verificación de sesiones vivas</LI> La verificación de sesiones
     * vivas se puede hacer de dos maneras en función de la propiedad
     * seycon.daemon.threads: <LI><B>seycon.daemon.threads=0</B> Secuencialmente
     * se verifica cada una de las conexiones, dando de baja aquellas que no
     * respondan.</li> <LI><B>seycon.daemon.threads>0</B> Se crea una lista en
     * memoria con las sesiones a verificar. A continuacion se crean threads en
     * función del número de sesiones abiertas y como máximo
     * seycon.daemon.threads. Todos los threads de forma paralela irán
     * verificando las conexiones y dando de baja las que no respondan</LI>
     * 
     */
    public void run() {
        // Iniciar thread de notificacions
        DuplicateSessionNotificationThread dsnt = new DuplicateSessionNotificationThread(this,
                notifications);
        new Thread(dsnt).start();
        // ** Gestión de chequeos concurrentes de conexión ***
        int daemonThreads = DEFAULT_NUMBER_OF_THREADS;

        /** Planificar tareas */
        while (true) {
            int sessions = 0;
            int loggedoff = 0;
            try {
                Collection<Session> sessionList = sessioService.getActiveSessions();
                int realThreads = sessionList.size() / 20;
                if (realThreads > daemonThreads)
                    realThreads = daemonThreads;
                if (realThreads <= 0)
                    realThreads = 1;
                Thread threads[] = new Thread[realThreads];
                int i;
                for (i = 0; i < realThreads; i++) {
                    threads[i] = new Thread(new LogoffThread(sessionList, this));
                    threads[i].setName("LogoffThread " + (i + 1)); //$NON-NLS-1$
                    threads[i].start();
                }
                // Ejecutar los threads y esperar a que finalicen
                for (i = 0; i < realThreads; i++) {
                    try {
                        threads[i].join();
                    } catch (java.lang.InterruptedException e) {
                    }
                    threads[i] = null;
                }
            } catch (Exception e) {
                log.warn("Error interno", e); //$NON-NLS-1$
            } finally {
                pool.releaseConnection();
            } // end try-catch
            try {
                sleep(60000);
            } catch (java.lang.InterruptedException e) {
            }
        } // end while-true
    }

    /**
     * Finalización del daemon
     */
    public void shutDown() {
    }

    /**
     * Comprobar si una determinada sesión está viva o no
     * 
     * @param host
     *            Máquina desde la que se inicia la sesión
     * @param port
     *            Número de puerto donde se encuentra el gestor de la sesión
     * @param user
     *            Código del usuario registrado
     * @param id
     *            Identificador de la sesión
     * @return true si la sesión debe seguir viva
     * @throws InternalErrorException 
     */
    public boolean check(Session sessio) throws InternalErrorException {
    	if (sessio.getType() == TipusSessio.PAM ||
    			sessio.getType() == TipusSessio.PAMRDP ||
    			sessio.getType() == TipusSessio.PAMSSH)
    		return checkPamSession(sessio);
    	else if (sessio.getUrl() != null)
    		return checkUrlSession(sessio);
    	else
    		return checkSocketSession(sessio);
    }
    
    public boolean checkUrlSession(Session sessio) throws InternalErrorException {
    	try {
        	URL url = new URL (sessio.getUrl());
//        	if (System.currentTimeMillis() - sessio.getDataKeepAlive().getTimeInMillis() < 7200000) // 2 hour = 2 * 60 * 60 * 1000
//        		return true;
        	HttpURLConnection connection = (HttpURLConnection) AlwaysTrustConnectionFactory.getConnection(url);
        	connection.setRequestMethod("POST"); //$NON-NLS-1$
        	connection.setDoOutput(true);
        	connection.setDoInput(true);
        	
        	StringBuffer b = new StringBuffer ();
        	b.append("id=").append(sessio.getId()); //$NON-NLS-1$
        	connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded"); //$NON-NLS-1$ //$NON-NLS-2$
        	connection.addRequestProperty("Content-Length", Integer.toString(b.length())); //$NON-NLS-1$
        	
        	
        	OutputStream out = connection.getOutputStream();
        	out.write(b.toString().getBytes("UTF-8")); //$NON-NLS-1$
        	out.flush();
        
        	
        	InputStreamReader reader = new InputStreamReader(connection.getInputStream(), "UTF-8"); //$NON-NLS-1$
        	StringBuffer buffer = new StringBuffer();
        	do
        	{
        		int i = reader.read();
        		if (i < 0)
        			break;
        		else
        			buffer.append((char) i);
        	} while (true);
        	reader.close();
//            log.info("Session {} is alive: result = {}", sessio.getUrl()+" ["+sessio.getId()+"]", buffer.toString()); //$NON-NLS-1$
        	return buffer.toString().startsWith("OK"); //$NON-NLS-1$
    	} 
    	catch (IOException e)
    	{
            String identity = String.format("%s on %s ID=%s", sessio.getUserName(), //$NON-NLS-1$
                            sessio.getUrl(), sessio.getId());
            log.info("LOGOFF Session {}: unexpected exception {}", identity, e.toString()); //$NON-NLS-1$
            // Simply assume web server is restarting and cannot answer your request
            return true;
    	}
    }
    
    public boolean checkPamSession(Session sessio) throws InternalErrorException {
    	try {
    		return ServiceLocator.instance().getPamSessionService().checkJumpServerSession(sessio);
    	} 
    	catch (Exception e)
    	{
            String identity = String.format("%s on %s ID=%s", sessio.getUserName(), //$NON-NLS-1$
                            sessio.getUrl(), sessio.getId());
            log.info("LOGOFF Session {}: unexpected exception {}", identity, e.toString()); //$NON-NLS-1$
            // Simply assume web server is restarting and cannot answer your request
            return true;
    	}
    }

    public boolean checkSocketSession(Session sessio) throws InternalErrorException {
        String idText = sessio.getId().toString();
        String identity = String.format("%s on %s:%d ID=%s", sessio.getUserName(), //$NON-NLS-1$
                sessio.getServerHostName(), sessio.getPort(), sessio.getId());

        if (sessio.getPort() == null)
            return true;

        Socket s = null;
        try {
            Host maq = xarxaService.findHostByName(sessio.getServerHostName());
            if (maq == null)
            {
            	log.info("Cannot find host {}", sessio.getServerHostName(), null);
            	return true;
            }
            if (maq.getIp() == null) {
            	log.info("Host {} {} does not have a IP address", maq.getId(), maq.getName());
            	return false;
            }
            
            
        	String defaultNetwork = ConfigurationCache.getProperty("soffid.network.internet"); //$NON-NLS-1$
            if ( defaultNetwork != null && defaultNetwork.equals( maq.getNetworkCode()))
            	return false; // Internet connection

        	log.info("Connecting to {} {}",maq.getIp(), sessio.getPort().intValue());
        	try {
        		s = new Socket(maq.getIp(), sessio.getPort().intValue());
        	} catch (IOException e) {
        		return false;
        	}
            try {
                s.setSoTimeout(30000); // Máximo treinta segundos para
                                       // responder
                InputStreamReader inReader = new InputStreamReader(s.getInputStream());
                BufferedReader reader = new BufferedReader(inReader);
                OutputStreamWriter outWriter = new OutputStreamWriter(s.getOutputStream(), "UTF-8"); //$NON-NLS-1$
                BufferedWriter writer = new BufferedWriter(outWriter);
                try {
                    writer.write("WHO"); //$NON-NLS-1$
                    writer.newLine();
                    writer.flush();
                    String line = reader.readLine();
                    if (line != null && line.equalsIgnoreCase(idText))
                        return true;
                    else {
                        log.info("Detected closed Session {}: bad answer {}", identity, line); //$NON-NLS-1$
                        return false;
                    }
                } finally {
                    reader.close();
                    writer.close();
                }
            } finally {
                try {
                    s.close();
                } catch (Exception e) {
                }
            }
        } catch (java.io.IOException e) {
            log.info("LOGOFF Session {}: unexpected exception {}", identity, e.toString()); //$NON-NLS-1$
            return false;
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (java.io.IOException e) {
                }
        } // end try-catch-finally
    }

    // Notificar de un sesión duplicada
    /**
     * Envia un mensaje a una determinada sesión
     * 
     * @param host
     *            Máquina a la cual notificar
     * @param port
     *            Número de puerto donde se encuentra el agente
     * @param message
     *            Mensaje a enviar
     * @throws InternalErrorException 
     */
    public void notify(Session sessio, String message) throws InternalErrorException {
        Socket s = null;
        try {
            Host maq = xarxaService.findHostByName(sessio.getServerHostName());
            if (maq == null || sessio.getPort() == null)
                return;
            s = new Socket(maq.getIp(), sessio.getPort().intValue());
            InputStreamReader inReader = new InputStreamReader(s.getInputStream());
            BufferedReader reader = new BufferedReader(inReader);
            OutputStreamWriter outWriter = new OutputStreamWriter(s.getOutputStream(), "UTF-8"); //$NON-NLS-1$
            BufferedWriter writer = new BufferedWriter(outWriter);
            writer.write("ALERT " + message); //$NON-NLS-1$
            writer.newLine();
            writer.flush();
            reader.readLine();
            s.close();
            s = null;
        } catch (java.io.IOException e) {
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (java.io.IOException e) {
                }
        } // end try-catch-finally
    }

    // Dar de alta un sujeto
    /**
     * Registrar una nueva sesión. El sistema verificará si el usuario dispone o
     * no de alguna sesión abierta. Si es así y no dispone de permiso de
     * multi-sesión, se denegará el inicio de sesion. Si es correcto, registrará
     * la sesión en las tablas SC_SESSIO y SC_REGACC
     * 
     * @param addr
     *            Host desde donde se registra
     * @param port
     *            Puerto donde está escuchando el agente
     * @param user
     *            Código de usuario
     * @param pass
     *            Contraseña utilizada
     * @param client
     *            Host donde se encuentra el usuario. Es importante en el caso
     *            de servidores web o servidores de terminales
     * @param silent
     * @return identificador de la nueva sesión
     * @throws LogonDeniedException
     *             El usuario no puede iniciar sesion
     * @throws UnknownHostException
     *             Máquina desconocida
     * @throws UnknownUserException
     *             Usuario desconocido
     * @throws InternalErrorException
     *             Cualquier otra causa
     */
    public Session addSession(Host servidor, int port, String user,
		Password pass, Host client, String secretKey,
		boolean closeOldSessions, boolean silent, 
		String authenticationMethod)
    	throws LogonDeniedException,
    		es.caib.seycon.ng.exception.UnknownHostException,
    		UnknownUserException, InternalErrorException
	{
    	User usuari = usuariService.findUserByUserName(user);
    	String clientName;

    	if (!usuari.getMultiSession().booleanValue())
    	{
    		StringBuffer hosts = new StringBuffer();
    		clientName = (client == null ? "" : ":" + client); //$NON-NLS-1$ //$NON-NLS-2$
    		if (warnMultipleSession(user, servidor.getName(),
					(client == null ? null : client.getName()),
					servidor.getName() + "(" + servidor.getIp() + ")" + //$NON-NLS-1$ //$NON-NLS-2$
						clientName, hosts, closeOldSessions, silent))
    			throw new TooManySessionsException(hosts.toString());
    	}
    	
    	return sessioService.registerSession(user, servidor.getName(),
			(client == null ? null : client.getName()), port, secretKey, authenticationMethod);
    } // end add-user

    public void shutdownSession(Session sessio, String message) {
        Socket s = null;
        try {
            Host maq = xarxaService.findHostByName(sessio.getServerHostName());
            if (maq == null || sessio.getPort() == null)
                return;
            s = new Socket(maq.getIp(), sessio.getPort().intValue());
            InputStreamReader inReader = new InputStreamReader(s.getInputStream());
            BufferedReader reader = new BufferedReader(inReader);
            OutputStreamWriter outWriter = new OutputStreamWriter(s.getOutputStream(), "UTF-8"); //$NON-NLS-1$
            BufferedWriter writer = new BufferedWriter(outWriter);
            writer.write("LOGOUT " + sessio.getKey()); //$NON-NLS-1$
            writer.newLine();
            writer.flush();
            s.close();
            s = null;
        } catch (java.io.IOException e) {
        } catch (InternalErrorException e) {
            log.warn("Error closing session", e); //$NON-NLS-1$
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (java.io.IOException e) {
                }
        } // end try-catch-finally
    }

    /**
     * Dar de baja una sesión. Borra los datos de la tabla SC_SESSIO
     * 
     * @param id
     *            Identificador de la sesión a dar de baja
     * @throws InternalErrorException 
     */
    public void deleteSession(Session s) throws InternalErrorException {
        sessioService.destroySession(s);
        log.info("LOGOFF: Session {} {}", s.getId(), s.getUserName()); //$NON-NLS-1$
    } // end delete-session

    /*
     * Verifica si un usuario tiene abierta alguna sesión. No se considera una
     * sesión duplicada abrir una sesión en un máquina destino si existe una
     * sesión en la máquina origen. Es decir, una sesión en un terminal server
     * no se considera sesión duplicada. Sólamente es invocada si el usuario no
     * tiene permiso de multi-sesión @param user Código del usuario @param
     * targetHost identificador interno del host donde inicia sesión @param
     * clientHost identificador interno del host desde donde se inicia @param
     * host nombre del host donde se inicia sesion @param hosts cadena que
     * recibirá los hosts donde ya hay sesión iniciada @return true si hay
     * sesiones duplicadas
     */
    public boolean warnMultipleSession(String user, String targetHost,
		String clientHost, String host, StringBuffer hosts,
		boolean closeOldSessions, boolean silent)
		throws InternalErrorException
	{
    	LinkedList<DuplicateSessionNotification> v =
			new LinkedList<DuplicateSessionNotification>();
    	// //////////////////////////////////////////////////////////////////////////
    	// Obtener las sesione que pueda tener abiertas el usuario
    	User usuari = usuariService.findUserByUserName(user);

    	boolean chainedSession = false;
    	for (Iterator<Session> it = sessioService.getActiveSessions(usuari.getId()).iterator();
				it.hasNext();)
    	{
    		Session s = it.next();
    		if (s.getServerHostName().equals(clientHost))
    		{
    			// Parent session
    			chainedSession = true;
    			break;
    		}
    		else if ((clientHost != null) &&
				(clientHost.equals(s.getClientHostName())))
    		{
    			// Sibling session
    			chainedSession = true;
    		}
    		else if (s.getPort() != null)
    		{
    			DuplicateSessionNotification n = new DuplicateSessionNotification(s);

    			n.setMessage(String.format(Messages.getString("SessionManager.DuplicateSessionMsg"), //$NON-NLS-1$
					host));
    			n.setCloseSession(closeOldSessions);
    			v.add(n);
    			hosts.append(s.getServerHostName());
    			hosts.append(" "); //$NON-NLS-1$
    		}
    	}
    	if (chainedSession)
    	{
    		hosts.setLength(0);
    		return false;
    	}
    	else if (!v.isEmpty())
    	{
    		synchronized (notifications)
    		{
    			notifications.addAll(v);
    			notifications.notifyAll();
    		}
    		return true;
    	}
    	else
    		return false;
    } // end add-user
    
    void keepAliveSession (Session session) throws InternalErrorException
    {
    	sessioService.sessionKeepAlive(session);
    }

}

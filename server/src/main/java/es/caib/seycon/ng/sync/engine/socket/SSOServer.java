// Copyright (c) 2000 Govern  de les Illes Balears
package es.caib.seycon.ng.sync.engine.socket;

import java.net.*;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * A Class class.
 * <P>
 * 
 * @author DGTIC
 */
public class SSOServer extends Thread {
    boolean shutDownPending = false;
    /**
     * Constructor
     */
    public SSOServer() {
        setName("SSO Server");
    }

    /**
     * If this thread was constructed using a separate <code>Runnable</code>
     * run object, then that <code>Runnable</code> object's <code>run</code>
     * method is called; otherwise, this method does nothing and returns.
     * <p>
     * Subclasses of <code>Thread</code> should override this method.
     * 
     * @see java.lang.Thread#start()
     * @see java.lang.Thread#stop()
     * @see java.lang.Thread#Thread(java.lang.ThreadGroup, java.lang.Runnable,
     *      java.lang.String)
     * @see java.lang.Runnable#run()
     * @since JDK1.0
     */
    ServerSocket s = null;
    private Logger log;

    public void run() {
        log = Log.getLogger("SSO Server");
        try {
            ServerService server = ServerServiceLocator.instance().getServerService();
            String name = server.getConfig("seycon.sso.port");
            int port;
            if (name == null)
                port = 559;
            else
                port = Integer.decode(name).intValue();
            log.info("Starting in port {}", new Integer(port), null);
            s = new ServerSocket(port);
            while (! shutDownPending) {
                try {
                    Socket clientSocket = s.accept();
                    SSOThread thread = new SSOThread(clientSocket);
                    thread.start();
                } catch (java.io.IOException e) {
                } // end catch
            } // end while
        } catch (java.io.IOException e) {
            log.warn ("Error intern {}", e);
        } // end try
        catch (InternalErrorException e) {
            log.warn ("Error intern {}", e, null);
        } finally {
            log.info("Stopped", null, null);
            if (s != null)
                try {
                    s.close();
                } catch (Exception e) {
                }
        }
    } // end run

    public void shutDown() {
        shutDownPending = true;
        try {
            s.close();
        } catch (java.io.IOException e) {
            log.warn ("Error when shutting down {}", e);
        }
        this.interrupt();
        try {
            this.join();
        } catch (InterruptedException e) {
        }
    }
} // end SSOServer


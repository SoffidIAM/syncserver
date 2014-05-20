package es.caib.seycon.ng.sync.engine.session;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.sync.ServerServiceLocator;


public class DuplicateSessionNotificationThread implements Runnable {
    LinkedList<DuplicateSessionNotification> notifications;

    Logger log = Log.getLogger("DuplicateSession"); //$NON-NLS-1$

    private SessionManager sessionManager;

    public DuplicateSessionNotificationThread(
            SessionManager sessionManager, LinkedList<DuplicateSessionNotification> notifications) {
        super();
        this.notifications = notifications;
        this.sessionManager = sessionManager;
    }

    public void run() {
        try { 
            while (true) {
                SessioService sessioService = ServerServiceLocator.instance().getSessioService();
                DuplicateSessionNotification notification = null;
                synchronized (notifications) {
                    while (notification == null) {
                        try {
                            notification = notifications.remove();
                        } catch (NoSuchElementException e) {
                            notifications.wait();
                        }
                    }
                }
                try {
                    log.info("Testing session {} of {}", notification.getId(), //$NON-NLS-1$
                            notification.getCodiUsuari());
                    Sessio sessio = sessioService.getSession(notification.getId(), notification.getClau()); 
                    if (sessio != null) {
                        if (sessionManager.check(sessio)) {
                            if (notification.isCloseSession()) {
                                sessionManager.shutdownSession(sessio, notification.getMessage());
                            } else {
                                sessionManager.notify(sessio, notification.getMessage());
                            }
                        } else {
                            sessionManager.deleteSession(sessio);
                        }
                    }
                } catch (Exception e) {

                }

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}

// Copyright (c) 2001 DGTIC
package com.soffid.iam.sync.engine.session;

import java.lang.Runnable;
import java.util.*;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Session;
import com.soffid.iam.utils.ConfigurationCache;

import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.TipusSessio;

/**
 * Thread lanzado por Daemon para verificar las sesiones vivas. Todos los
 * threads lanzados de forma simulténea se sincronizan mediante el vector de
 * sesiones a verificar. De forma sincronizada cada thread coge el objeto
 * {@link es.caib.sso.SessionInfo} de la cabeza del vector, lo elimina , lo
 * procesa y pasa al siguiente
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.1 $
 * @see SessionManager
 */

// $Log: LogoffThread.java,v $
// Revision 1.1  2012-11-07 07:50:12  u07286
// Refactoring hibernate
//
// Revision 1.1 2012-05-16 10:57:57 u07286
// Reestructuració de paquets seycon antics
//
// Revision 1.2 2012-03-19 13:52:46 u07286
// Corregir integer overflow
//
// Revision 1.1 2010-12-07 13:38:31 u07286
// Renombrar LogoffDaemon a SessionManager
//
// Revision 1.4 2010-03-15 10:23:32 u07286
// Movido a tag HEAD
//
// Revision 1.2.2.1 2009-06-16 11:23:02 u07286
// Merge a seycon-3.0.15
//
// Revision 1.3 2009-05-29 08:20:52 u07286
// Debug en logoff
//
// Revision 1.2 2008-10-16 11:43:42 u07286
// Migrado de RMI a HTTP
//
// Revision 1.1 2007-09-06 12:51:17 u89559
// [T252]
//
// Revision 1.2 2004-05-18 06:13:40 u07286
// Agregado javadoc
//

public class LogoffThread extends Object implements Runnable {
    Collection<Session> sessionInfoVector;
    private SessionManager sessionManager;

    /**
     * Constructor
     */
    public LogoffThread(Collection<Session> sessionList, SessionManager sm) {
        sessionInfoVector = sessionList;
        sessionManager = sm;

    }

    public void run() {
        Logger log = Log.getLogger(Thread.currentThread().getName());
        boolean salir = false;
        Session s = null;
        long sessionTimeout = 1200000; // 20 minutes
        long pamSessionTimeout = 60000; // 1 minute
        try {
        	sessionTimeout = Long.decode(ConfigurationCache.getMasterProperty("soffid.esso.session.timeout")) * 1000;
        } catch (Exception e) {}
        try {
        	pamSessionTimeout = Long.decode(ConfigurationCache.getMasterProperty("soffid.esso.pamsession.timeout")) * 1000;
        } catch (Exception e) {}
        // es.caib.seycon.ServerApplication.out.println
        // (Thread.currentThread().getName () + ": started");
        while (!salir) {
            synchronized (sessionInfoVector) {
                Iterator<Session> it = sessionInfoVector.iterator();
                if (! it.hasNext())
                    salir = true;
                else {
                    s = it.next();
                    it.remove();
                }
            }
            if (!salir) {
                try {
                	Long lastPing = s.getKeepAliveDate() == null ? 
                						s.getStartDate().getTimeInMillis():
                						s.getKeepAliveDate().getTimeInMillis();
                	long t = s.getType() == TipusSessio.PAM ? pamSessionTimeout: sessionTimeout;
                	if ( System.currentTimeMillis() - lastPing > t)
                	{
                        if (!sessionManager.check(s)) {
                            sessionManager.deleteSession(s);
                        } else {
                        	sessionManager.keepAliveSession(s);
                        }
                	}
                } catch (Exception e) {
                    log.warn("Error checking session", e); //$NON-NLS-1$
                }
            }
        }
    }
}

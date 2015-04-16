package es.caib.seycon.ng.sync.servei;

import com.soffid.iam.model.SessionEntity;
import es.caib.seycon.ng.sync.engine.ChangePasswordNotification;
import es.caib.seycon.ng.sync.engine.challenge.ChallengeStore;
import es.caib.seycon.ng.sync.engine.cpn.ChangePasswordNotificationThread;
import es.caib.seycon.ng.sync.servei.ChangePasswordNotificationQueueBase;
import es.caib.seycon.ssl.AlwaysTrustConnectionFactory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

public class ChangePasswordNotificationQueueImpl extends
        ChangePasswordNotificationQueueBase {
    LinkedList<ChangePasswordNotification> sessionsToNotify = new LinkedList<ChangePasswordNotification>();
    Object semaphore = new Object ();
    int maxQueueSize = 1;
    Logger log = Log.getLogger("ChangePasswordNotificationQueue");
    List<ChangePasswordNotificationThread> threads = new LinkedList<ChangePasswordNotificationThread>();
    
    @Override
    protected void handleAddNotification(String user) throws Exception {
        List<SessionEntity> sessions = getSessionEntityDao().findSessionByUserName(user);
        for (Iterator<SessionEntity> it = sessions.iterator(); it.hasNext(); ) {
            SessionEntity sessio = it.next();
            ChangePasswordNotification n = new ChangePasswordNotification();
            n.setUser(user);
            n.setSessionId(sessio.getId());
            if (sessio.getHost() == null || sessio.getHost().getHostIP() == null) n.setHost(sessio.getHostAddress()); else n.setHost(sessio.getHost().getHostIP());
            if (sessio.getPort() != null) n.setPortNumber(sessio.getPort().intValue());
            if (sessio.getWebHandler() != null) n.setUrl(sessio.getWebHandler());
            synchronized (semaphore) {
                sessionsToNotify.addLast(n);
                startNotificationThreads();
                semaphore.notify();
            }
        }
    }
      
    void startNotificationThreads ()
    {
        if (threads.isEmpty())
        {
            maxQueueSize = 1;
            ChangePasswordNotificationThread th = new ChangePasswordNotificationThread();
            threads.add(th);
            th.start();
        } else {
            int i = sessionsToNotify.size();
            if ( i >= maxQueueSize ||  threads.isEmpty())
            {
                maxQueueSize *= 2;
                ChangePasswordNotificationThread th = new ChangePasswordNotificationThread();
                threads.add(th);
                th.start();
            }
        }
    }
    
    protected ChangePasswordNotification handlePeekNotification ()
    {
        synchronized (semaphore) {
            if (sessionsToNotify.isEmpty()) {
                try {
                    semaphore.wait(30000);// Wait for five minutes
                } catch (InterruptedException e) {
                } 
                if (sessionsToNotify.isEmpty())
                    return null;
            }
            ChangePasswordNotification n = sessionsToNotify.getFirst();
            sessionsToNotify.removeFirst();
            return n;
        }
    }
    

    private void sendKeySocketMessage(ChangePasswordNotification n, String dif) throws IOException {
        Socket s = null;
        try {
            s = new Socket(n.getHost(), n.getPortNumber());
            try {
                s.setSoTimeout(30000); // Máximo treinta segundos para
                                        // responder
                OutputStreamWriter outWriter = new OutputStreamWriter(s
                        .getOutputStream());
                BufferedWriter writer = new BufferedWriter(outWriter);
                try {
                    writer.write("KEY");
                    writer.write(dif);
                    writer.newLine();
                    writer.flush();
                } finally {
                    writer.close();
                }
            } finally {
                try {
                    s.close();
                } catch (Exception e) {
                }
            }
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (java.io.IOException e) {
                }
        } // end try-catch-finally
    }

    private void sendKeyPostMessage(ChangePasswordNotification n, String dif) throws IOException {
        try {
        	URL url = new URL (n.getUrl());
        	HttpURLConnection connection = (HttpURLConnection) AlwaysTrustConnectionFactory.getConnection(url);
        	connection.setRequestMethod("POST");
        	connection.setDoOutput(true);
        	connection.setDoInput(true);
        	
        	StringBuffer b = new StringBuffer ();
        	b.append("id=").append(n.getSessionId()).append("&key=").append(dif).append("\r\n");
        	connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        	connection.addRequestProperty("Content-Length", Integer.toString(b.length()));
        	
        	
        	OutputStream out = connection.getOutputStream();
        	out.write(b.toString().getBytes("UTF-8"));
        	out.flush();
       	
        	InputStream in = connection.getInputStream();
        	
        	while ( in.read() >= 0)
        	{
        	}
        	in.close();
        	connection.disconnect();
        } finally {
        } // end try-catch-finally
    }

    private String computeDiferences(String key, String newKey) {
        StringBuffer b = new StringBuffer();
        ChallengeStore s = ChallengeStore.getInstance();
        for (int i=0; i <key.length();i++)
        {
            char ch = key.charAt(i);
            char ch2 = newKey.charAt(i);
            int dif = s.charToInt(ch2) - s.charToInt(ch);
            b.append (s.intToChar(dif));
        }
        return b.toString();
    }

    
    @Override
    protected void handleSendNotification(ChangePasswordNotification n)
            throws Exception {
        try {
            SessionEntity sessio = getSessionEntityDao().load(n.getSessionId());
            if (sessio == null || sessio.getKey() == null)
                return;
            // Generar la nova clau
            String newKey = ChallengeStore.getInstance().generateSessionKey();
            String dif = computeDiferences(sessio.getKey(), newKey);
            sessio.setNewKey(newKey);
            getSessionEntityDao().update(sessio);
            if (n.getUrl() == null)
            	sendKeySocketMessage(n, dif);
            else
            	sendKeyPostMessage(n, dif);
            log.info("User {} notified to change passwords", n.getUser(), null);
        } catch (IOException e) {
            log.warn("Error sending new key for user {}", n.getUser(), null);
            log.warn("Exception: ", e);
        }

    }

    @Override
    protected void handleEndNotificationThread() throws Exception {
        ChangePasswordNotificationThread thread = (ChangePasswordNotificationThread) Thread.currentThread();
        maxQueueSize /= 2;
        threads.remove(thread);
    }


}

// Copyright (c) 2000 Govern  de les Illes Balears
package com.soffid.iam.sync.engine.socket;

import java.net.*;
import java.io.*;

import es.caib.seycon.ng.exception.InternalErrorException;

import java.rmi.RemoteException;
import java.nio.charset.Charset;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Challenge;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.Session;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.jetty.Invoker;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.service.QueryService;

import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.exception.LogonDeniedException;

/**
 * A Class class.
 * <P>
 * 
 * @author DGTIC
 */
public class SSOThread extends Thread {
    Socket socket;
    Challenge challenge;
    int clientVersion;
    String hostIp;
    Logger log = Log.getLogger("SSO");
    
    LogonService logon;

    /**
     * Constructor
     */
    public SSOThread(Socket s) {
        socket = s;
        challenge = null;
        clientVersion = 1;
        hostIp = s.getInetAddress().getHostAddress();
        logon = ServerServiceLocator.instance().getLogonService();
    }

    public java.util.Vector parseString(String s) {
        java.util.Vector v = new java.util.Vector();
        String token;
        while (s.length() > 0) {
            int x = s.indexOf("|");
            if (x < 0) {
                token = s;
                s = "";
            }
            // else if (x == 0) {token = ""; s = s.substring (x+1); }
            else {
                token = s.substring(0, x);
                s = s.substring(x + 1);
            }
            v.addElement(token);
        }
        return v;
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
    public void run() {
        try {
            Invoker.setInvoker(new Invoker(socket.getInetAddress()));
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    input, Charset.forName("ISO-8859-1")));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output, Charset.forName("ISO-8859-1")));
            // Identificar el host llamante
            String addr = socket.getInetAddress().getHostAddress();
            // Leer linea con usuario
            String line = reader.readLine();
            while (line != null) {
                java.util.Vector v = parseString(line);
                try {
                    if (v.size() == 0)
                        throw new es.caib.seycon.ng.exception.InternalErrorException(
                                "Falta comando");
                    String method = (String) v.elementAt(0);
                    String result = "";
                    if (method.equals("clientVersion"))
                        result = clientVersion(v);
                    else if (method.equals("requestChallenge"))
                        result = requestChallenge(v);
                    else if (method.equals("responseChallenge"))
                        result = responseChallenge(v);
                    else if (method.equals("propagatePassword"))
                        result = propagatePassword(v);
                    else if (method.equals("validatePassword"))
                        result = validatePassword(v);
                    else if (method.equals("changePassword"))
                        result = changePassword(v);
                    else if (method.equals("mustChangePassword"))
                        result = mustChangePassword(v);
                    else if (method.equals("getData")) {
                        result = getData(v, writer);
                    } else
                        throw new InternalErrorException("Commando incorrecto "
                                + method);
                    if (result != null)
                        writer.write(result);
                } catch (Exception e) {
                    log.warn("Unexpected exception at {} {}:" + e.toString(),
                            addr, line);
                    writer.write(e.getClass().getName() + "|" + e.getMessage());
                }
                writer.newLine();
                writer.flush();
                line = reader.readLine();
            }
            reader.close();
            writer.close();
            socket.close();
        } catch (IOException e) {
        } finally {
            Invoker.setInvoker (null);
        }
    } // end-run

    // Pedir desafío
    String requestChallenge(java.util.Vector v) throws InternalErrorException,
            RemoteException, LogonDeniedException, UnknownUserException {
        if (v.size() != 4 && v.size() != 5)
            throw new InternalErrorException("Número erróneo de argumentos");
        String user = (String) v.elementAt(1);
        String client = (String) v.elementAt(2);
        int cardSupport = Integer.decode((String) v.elementAt(3)).intValue();
        String db = null;
        if (v.size() == 5)
            db = (String) v.elementAt(4);
        // Solicitar desafío
        challenge = logon.requestChallenge(Challenge.TYPE_RMI, user, null, hostIp, client, cardSupport);
        challenge.setClientVersion(clientVersion);
        return "OK|" + challenge.getCardNumber() + "|" + challenge.getCell();
    }

    // Responder al desafio
    String responseChallenge(java.util.Vector v) throws InternalErrorException,
            RemoteException, LogonDeniedException,
            es.caib.seycon.ng.exception.UnknownUserException {
        if (v.size() != 3 && v.size() != 4) {
            throw new InternalErrorException("Número erróneo de argumentos");
        }
        challenge.setPassword(Password.decode((String) v.elementAt(1)));
        String port = (String) v.elementAt(2);
        try {
            challenge.setCentinelPort(Integer.decode(port).intValue());
        } catch (Exception e) {
        }
        if (v.size() == 4)
            challenge.setValue((String) v.elementAt(3));

        Session sessio = logon.responseChallenge(challenge);
        String info;
        if (clientVersion < 2)
            info = "OK|" + sessio.getId().toString() + "|" + sessio.getUserName()
                    + ";" ;
        else
            info = "OK|" + sessio.getId() + "|" ;

        return info;
    }

    String propagatePassword(java.util.Vector v) throws InternalErrorException,
            RemoteException, UnknownUserException, LogonDeniedException {
        if (v.size() != 3)
            throw new InternalErrorException("Número erróneo de argumentos");
        String user = (String) v.elementAt(1);
        Password pass = Password.decode((String) v.elementAt(2));
        logon.propagatePassword(user, null, pass.getPassword());
        return "OK";
    }

    String validatePassword(java.util.Vector v) throws InternalErrorException,
            RemoteException, UnknownUserException, LogonDeniedException {
        if (v.size() != 3)
            throw new InternalErrorException("Número erróneo de argumentos");
        String user = (String) v.elementAt(1);
        Password pass = Password.decode((String) v.elementAt(2));
        boolean ok = logon.validatePassword(user, null, pass.getPassword()) == PasswordValidation.PASSWORD_GOOD;
        if (ok)
            return "OK|1";
        else
            return "OK|0";
    }

    String changePassword(java.util.Vector v) throws InternalErrorException,
            RemoteException, UnknownUserException, LogonDeniedException,
            es.caib.seycon.ng.exception.InvalidPasswordException,
            es.caib.seycon.ng.exception.BadPasswordException {
        if (v.size() != 4)
            throw new InternalErrorException("Número erróneo de argumentos");
        String user = (String) v.elementAt(1);
        Password pass = Password.decode((String) v.elementAt(2));
        Password newpass = Password.decode((String) v.elementAt(3));
        logon.changePassword(user, null, pass.getPassword(), newpass.getPassword());
        return "OK|1";
    }

    String mustChangePassword(java.util.Vector v)
            throws InternalErrorException, RemoteException,
            UnknownUserException, LogonDeniedException,
            es.caib.seycon.ng.exception.InvalidPasswordException,
            es.caib.seycon.ng.exception.BadPasswordException {
        if (v.size() != 2)
            throw new InternalErrorException("Número erróneo de argumentos");
        String user = (String) v.elementAt(1);
        if (logon.mustChangePassword(user, null))
            return "OK|1";
        else
            return "OK|0";
    }

    String getData(java.util.Vector v, BufferedWriter writer)
            throws java.sql.SQLException, InternalErrorException, IOException {
        if (v.size() != 2)
            throw new InternalErrorException("Número erróneo de argumentos");
        String url = (String) v.elementAt(1);
        QueryService qs = ServerServiceLocator.instance().getQueryService();
        if (writer == null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Writer w = new OutputStreamWriter(out);
            w.write("OK|");
            qs.query(url, "text/plain", writer);
            w.close();
            return out.toString();
        }
        else
        {
            qs.query(url, "text/plain", writer);
            return null;
        }

    }

    //
    //
    String clientVersion(java.util.Vector v) throws InternalErrorException {
        if (v.size() != 2) {
            throw new InternalErrorException("Número erróneo de argumentos");
        }
        String version = (String) v.elementAt(1);
        try {
            clientVersion = Integer.decode(version).intValue();
        } catch (Exception e) {
        }
        return "OK";
    }
} // end-class SSOThread


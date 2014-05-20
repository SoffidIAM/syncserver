package bubu.test.seycon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;

import javax.net.ssl.HttpsURLConnection;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import es.caib.seycon.ssl.ConnectionFactory;
import es.caib.seycon.util.Base64;

public class KerberosTest {

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            ClientLoginConfiguration config = new ClientLoginConfiguration ();
            Configuration.setConfiguration(config);
            Subject s = doLogin ();
            
            Subject.doAs(s, new PrivilegedAction<Object>() {
                public Object run() {
                    try {
                        doConnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void doConnect() throws GSSException, IOException,
            MalformedURLException, ProtocolException,
            UnsupportedEncodingException {
        GSSManager manager = GSSManager.getInstance();
        System.out.println ("PRUEBA 2");
        GSSName clientName = 
            manager.createName("u00003@TEST-AD.LAB", GSSName.NT_USER_NAME);
        GSSCredential clientCreds =
            manager.createCredential(null,
                                      8*3600,
                                      new Oid("1.2.840.113554.1.2.2"),
                                      GSSCredential.INITIATE_ONLY);
        GSSName peerName =
            manager.createName("SEYCON/epreinf14.test.lab@TEST-AD.LAB",
                                null);
        GSSContext secContext =
            manager.createContext(peerName,
                                  new Oid("1.2.840.113554.1.2.2"),
                                  clientCreds,
                                  GSSContext.DEFAULT_LIFETIME);
        secContext.requestMutualAuth(true);
 
        // The first input token is ignored
        byte[] inToken = new byte[0];

        byte[] outToken = null;

        boolean established = false;

         // Loop while the context is still not established
         while (!established) {
           outToken = 
               secContext.initSecContext(inToken, 0, inToken.length);

           // Send a token to the peer if one was generated
           if (outToken != null)
           {
              inToken = sendToken(outToken);
           }
           established = true;

        }
    }

    private static Subject doLogin() throws LoginException {
//        LoginContext lc = new LoginContext("SampleClient", new MyCallbackHandler("Administrator@TEST-AD.LAB", "test6969"));
        LoginContext lc = new LoginContext("SampleClient", new MyCallbackHandler(null, null));
        lc.login();
        System.out.println ("Logged in ");
        Subject s = lc.getSubject();
        for (Iterator<Principal> it = s.getPrincipals().iterator(); it.hasNext();)
        {
            Principal p = it.next();
            System.out.println ("Principal = "+p.getName());
        }
        return s;
        
    }

    private static byte[] sendToken( byte[] outToken) throws IOException {
        String b64 = es.caib.seycon.util.Base64.encodeBytes(outToken, Base64.DONT_BREAK_LINES);
        b64=URLEncoder.encode(b64, "UTF-8");
        // Cambiar la factoria SSL
        String url = "https://epreinf14.test.lab:750/kerberosLogin?action=start&principal=u00003@TEST-AD.LAB&clientIP=&cardSupport=2&krbToken="+
            b64;
        System.out.println(url);
        HttpsURLConnection conn = ConnectionFactory.getConnection(new URL(
                url));
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestMethod("GET");
        conn.connect();
        InputStream in = conn.getInputStream();
        int read = in.read();
        while (read >= 0)
        {
            System.out.write(read);
            read = in.read();
        }
        in.close ();
        
        return null;
    }

    private static byte[] readToken(LineNumberReader reader) throws IOException {
        String line = reader.readLine();
        return Base64.decode(line);
    }


}
/**
 * @author u07286
 *
 * Window - Preferences - Java - Code Style - Code Templates
 */
class ClientLoginConfiguration extends Configuration {
  public AppConfigurationEntry[] getAppConfigurationEntry(String p0)
  {
    AppConfigurationEntry app[] = new AppConfigurationEntry [ 1 ];
    HashMap<String, String> m = new HashMap<String, String>();
    m.put("useTicketCache", "true");
    m.put("storeKey", "true");
    app [ 0 ] = new AppConfigurationEntry (
        "com.sun.security.auth.module.Krb5LoginModule",
        AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT,
        m
       );
    HashMap<String, String> m2 = new HashMap<String, String>();
    m2.put("useTicketCache", "true");
//    m2.put("storeKey", "true");
    app [ 0 ] = new AppConfigurationEntry (
        "com.sun.security.auth.module.Krb5LoginModule",
        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
        m2
       );
    return app;
  }

  public void refresh()
  {
  }
  
}

/**
 * Callback para insertar usuario y contraseña
 */
class MyCallbackHandler extends Object implements CallbackHandler
{
  String user;
  String password;

  MyCallbackHandler (String user , String password) 
  {
    this.user = user;
    this.password = password;
  }
  
  public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
  {
     for (int i = 0; i < callbacks.length; i++) {
        if (callbacks[i] instanceof TextOutputCallback) {
          // display the message according to the specified type
          TextOutputCallback toc = (TextOutputCallback)callbacks[i];
          switch (toc.getMessageType()) {
          case TextOutputCallback.INFORMATION:
              System.out.println(toc.getMessage());
              break;
          case TextOutputCallback.ERROR:
              System.out.println("ERROR: " + toc.getMessage());
              break;
          case TextOutputCallback.WARNING:
              System.out.println("WARNING: " + toc.getMessage());
              break;
          default:
              throw new IOException("Unsupported message type: " +
                toc.getMessageType());
          }

        } else if (callbacks[i] instanceof NameCallback) {
          // prompt the user for a username
          NameCallback nc = (NameCallback)callbacks[i];
          if (user == null)
          {
              System.out.print(nc.getPrompt());
              System.out.flush();
              LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in)); 
              user = reader.readLine();
          }
          System.out.println ("Set user = "+user);
          nc.setName(user);
        } else if (callbacks[i] instanceof PasswordCallback) {
          PasswordCallback pc = (PasswordCallback)callbacks[i];
          if (password == null)
          {
              System.out.print(pc.getPrompt());
              System.out.flush();
              LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in)); 
              password = reader.readLine();
          }
          System.out.println ("Set password = "+password);
          pc.setPassword(password.toCharArray());
        } else {
          throw new UnsupportedCallbackException
            (callbacks[i], "Unrecognized Callback");
        }
     }
   }

  
}

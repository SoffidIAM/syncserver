package com.soffid.iam.sync.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.log.Log;
import org.mortbay.resource.Resource;

public class MySslSocketConnector extends SslSocketConnector {
    private String _password;
	private Object pass;
	private String _keyPassword;
	private String _trustPassword;


	/* ------------------------------------------------------------ */
    public void accept(int acceptorID)
        throws IOException, InterruptedException
    {   
        try
        {
            Socket socket = _serverSocket.accept();
            configure(socket);

            Connection connection=new MySslConnection(socket);
            connection.dispatch();
        }
        catch(SSLException e)
        {
            Log.warn(e);
            try
            {
                stop();
            }
            catch(Exception e2)
            {
                throw new IllegalStateException(e2);
            }
        }
    }

    protected SSLServerSocketFactory createFactory() 
        throws Exception
    {
        if (getTruststore()==null)
        {
            setTruststore(getKeystore());
            setTruststoreType(getKeystoreType());
        }

        KeyManager[] keyManagers = null;
        InputStream keystoreInputStream = null;
        if (getKeystore() != null)
        	keystoreInputStream = Resource.newResource(getKeystore()).getInputStream();
        KeyStore keyStore = KeyStore.getInstance(getKeystoreType());
        keyStore.load(keystoreInputStream, _password==null?null:_password.toString().toCharArray());
        try 
        {
        	keyStore.deleteEntry("secretskey");
        } catch (Exception e) {
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getSslKeyManagerFactoryAlgorithm());        
        keyManagerFactory.init(keyStore,_keyPassword==null?null:_keyPassword.toString().toCharArray());
        keyManagers = keyManagerFactory.getKeyManagers();

        TrustManager[] trustManagers = null;
        InputStream truststoreInputStream = null;
        if (getTruststore() != null)
        	truststoreInputStream = Resource.newResource(getTruststore()).getInputStream();
        KeyStore trustStore = KeyStore.getInstance(getTruststoreType());
        trustStore.load(truststoreInputStream, _trustPassword==null?null:_trustPassword.toString().toCharArray());
        
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(getSslTrustManagerFactoryAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();
        

        SecureRandom secureRandom = getSecureRandomAlgorithm()==null?null:SecureRandom.getInstance(getSecureRandomAlgorithm());

        SSLContext context = getProvider()==null?SSLContext.getInstance(getProtocol()):SSLContext.getInstance(getProtocol(), getProvider());

        
     // Get the default Key Manager
        final X509KeyManager origKm = (X509KeyManager) keyManagers[0];
        X509KeyManager km = new MyX509KeyManager(origKm);

        context.init(new KeyManager[] { km }, trustManagers, secureRandom);
//        context.init(kmf.getKeyManagers(), trustManagers, secureRandom);

        return context.getServerSocketFactory();
    }
    

    @Override
	public void setTrustPassword(String password) {
		super.setTrustPassword(password);
		_trustPassword = password;
	}

	@Override
	public void setKeyPassword(String password) {
		super.setKeyPassword(password);
		_keyPassword = password;
	}

	@Override
	public void setPassword(String password) {
		super.setPassword(password);
		_password  = password;
	}


	protected class MySslConnection extends SslSocketConnector.SslConnection
    {
        public MySslConnection(Socket socket) throws IOException {
            super(socket);
        }

        public String toString () {
            return "SSL Connection from "+_socket.getRemoteSocketAddress().toString()+" to "+
                    _socket.getLocalSocketAddress().toString();
        }
        
    }

}

class MyX509KeyManager implements X509KeyManager {
	org.apache.commons.logging.Log log = LogFactory.getLog(getClass());
	
	private X509KeyManager parent;

	public MyX509KeyManager(X509KeyManager parent) {
		this.parent = parent;
	}
	
	public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
		String s = parent.chooseClientAlias(keyType, issuers, socket);
		return s;
	}

	public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
		String s = parent.chooseServerAlias(keyType, issuers, socket);
		return s;
	}

	public X509Certificate[] getCertificateChain(String alias) {
		return parent.getCertificateChain(alias);
	}

	public String[] getClientAliases(String keyType, Principal[] issuers) {
		return parent.getClientAliases(keyType, issuers);
	}

	public PrivateKey getPrivateKey(String alias) {
		log.info("Buscando clave "+alias);
		return parent.getPrivateKey(alias);
	}

	public String[] getServerAliases(String keyType, Principal[] issuers) {
		return parent.getServerAliases(keyType, issuers);
	}
}

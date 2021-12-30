package com.soffid.iam.sync.update;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.soffid.iam.api.Password;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.ssl.SeyconTrustManager;

public class HttpConnectionFactory {
    private static SSLSocketFactory sslFactory = null;

    private static KeyManager[] getKeyManagers(KeyStore ks)
            throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException, FileNotFoundException, IOException {
        Password password = SeyconKeyStore.getKeyStorePassword();

        X509Certificate myKey = (X509Certificate) ks
                .getCertificate(SeyconKeyStore.MY_KEY);
        if (myKey == null) {
            return new KeyManager[0];
        } else {

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); //$NON-NLS-1$
            kmf.init(ks, password.getPassword().toCharArray());
            return kmf.getKeyManagers();
        }

    }

    private static void init() throws KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
        File file = SeyconKeyStore.getKeyStoreFile();
        KeyStore ks = SeyconKeyStore.loadKeyStore(file);

        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
        	String key = e.nextElement();
        	if ( !key.equalsIgnoreCase(SeyconKeyStore.MY_KEY) && ks.isKeyEntry(key) )
        		ks.deleteEntry(key);
        }

        SSLContext ctx;
        ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$

        ctx.init(getKeyManagers(ks), getTrustManagers(ks), null);

        sslFactory = ctx.getSocketFactory();

    }

    public static void reloadKeys () throws KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException
    {
    	init ();
    }
    
    private static TrustManager[] getTrustManagers(KeyStore ks)
            throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, FileNotFoundException, IOException {
        return new TrustManager[] { new SeyconTrustManager(ks) };
    }
    
    public static SSLSocketFactory getSocketFactory() throws KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
        if (sslFactory == null) {
            init ();
        }
        return sslFactory;
            
    }

    public static HttpsURLConnection getConnection(URL url)
            throws RemoteException {
        try {
            if (sslFactory == null) {
                init();
            }

            HttpsURLConnection sslC = (HttpsURLConnection) url.openConnection();

            sslC.setSSLSocketFactory(sslFactory);

            return sslC;
        } catch (Exception e) {
            throw new RemoteException("ConnectionFactory.NotSSLInitialized", e); //$NON-NLS-1$
        }

    }
}

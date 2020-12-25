package com.soffid.iam.sync.bootstrap.impl;

import java.io.ByteArrayInputStream;
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
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class BaseHttpConnectionFactory {
    private SSLSocketFactory sslFactory = null;

    private KeyManager[] getKeyManagers(KeyStore ks)
            throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException, FileNotFoundException, IOException {
         return new KeyManager[0];
    }

    public BaseHttpConnectionFactory(String certdata) throws KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
        
        byte[] binary = Base64.getDecoder().decode(certdata);
        X509Certificate cert =
        	(X509Certificate) CertificateFactory.getInstance("X.509")
									.generateCertificate( new ByteArrayInputStream(binary));
        

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.setCertificateEntry("trusted", cert);

        SSLContext ctx;
        ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$

        ctx.init(getKeyManagers(ks), getTrustManagers(ks), null);

        sslFactory = ctx.getSocketFactory();

    }

    private TrustManager[] getTrustManagers(KeyStore ks)
            throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, FileNotFoundException, IOException {
        return new TrustManager[] { new SeyconTrustManager(ks) };
    }
    
    public SSLSocketFactory getSocketFactory() throws KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
        if (sslFactory == null) {
            throw new IOException("Not initialized");
        }
        return sslFactory;
            
    }

    public HttpsURLConnection getConnection(URL url)
            throws RemoteException {
        try {
            if (sslFactory == null) {
                throw new IOException("Not initialized");
            }

            HttpsURLConnection sslC = (HttpsURLConnection) url.openConnection();

            sslC.setSSLSocketFactory(sslFactory);

            return sslC;
        } catch (Exception e) {
            throw new RemoteException("ConnectionFactory.NotSSLInitialized", e); //$NON-NLS-1$
        }

    }
}

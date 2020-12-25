/*
 * Created on 24-ago-2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.soffid.iam.sync.bootstrap.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * @author u07286
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */

public class SeyconTrustManager implements X509TrustManager {
    KeyStore ks;
    private X509Certificate trustedCert;

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    public X509Certificate[] getAcceptedIssuers() {
        if (trustedCert != null)
            return new X509Certificate[] { trustedCert };
        else
            return new X509Certificate[0];

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[],
     *      java.lang.String)
     */
    public void checkServerTrusted(X509Certificate[] arg0, String arg1)
            throws CertificateException {
        X509Certificate cert = arg0[0];
        try {
            if (trustedCert != null)
                cert.verify(trustedCert.getPublicKey(), "BC"); //$NON-NLS-1$
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    public void checkClientTrusted(X509Certificate[] arg0, String arg1)
            throws CertificateException {
        X509Certificate cert = arg0[0];
        try {
            if (trustedCert != null)
                cert.verify(trustedCert.getPublicKey(), "BC"); //$NON-NLS-1$
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    public SeyconTrustManager(KeyStore ks) throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException,
            FileNotFoundException, IOException {
        this.ks = ks;
        trustedCert = (X509Certificate) ks
                .getCertificate("rootKey"); //$NON-NLS-1$
    }


}

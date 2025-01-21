/*
 * Created on 24-ago-2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.soffid.iam.sync.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * @author u07286
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */

public class KubernetesTrustManager extends X509ExtendedTrustManager {
    KeyStore ks;
    private X509Certificate trustedCert;
    static boolean trustAnything = false;
    
    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    public X509Certificate[] getAcceptedIssuers() {
    	try {
			List<X509Certificate> certs = new LinkedList<>();
			for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
				String alias = e.nextElement();
				X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
				if (cert != null)
					certs.add(cert);
			}
			return certs.toArray(new X509Certificate[certs.size()]);
		} catch (KeyStoreException e) {
			return new X509Certificate[0];
		}
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[],
     *      java.lang.String)
     */
    public void checkServerTrusted(X509Certificate[] certs, String arg1)
            throws CertificateException {
        try {
    		for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
    			String alias = e.nextElement();
    			X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
    			if (cert != null && cert.equals(certs[0]))
    				return; // Knwon certificate
    		}
            if (trustedCert != null)
                certs[0].verify(trustedCert.getPublicKey(), "BC"); //$NON-NLS-1$
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    public void checkClientTrusted(X509Certificate[] certs, String arg1)
            throws CertificateException {
        try {
    		for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
    			String alias = e.nextElement();
    			X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
    			if (cert != null && cert.equals(certs[0]))
    				return; // Knwon certificate
    		}
            if (trustedCert != null)
                certs[0].verify(trustedCert.getPublicKey(), "BC"); //$NON-NLS-1$
            else
            	throw new CertificateException("Untrusted certificate "+certs[0].getSubjectDN());
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    public KubernetesTrustManager(KeyStore ks) throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException,
            FileNotFoundException, IOException {
        this.ks = ks;
        trustedCert = (X509Certificate) ks
                .getCertificate("rootKey"); //$NON-NLS-1$
    }

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
			throws CertificateException {
		checkClientTrusted(chain, authType);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
			throws CertificateException {
		checkServerTrusted(chain, authType);
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
			throws CertificateException {
		checkClientTrusted(chain, authType);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
			throws CertificateException {
		checkServerTrusted(chain, authType);
	}

}

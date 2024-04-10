package com.soffid.iam.sync.jetty;

import java.security.KeyStore;
import java.security.cert.CRL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.TrustManager;

import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.engine.cert.CertificateServer;

/**
* <p>SslContextFactory is used to configure SSL parameters
* to be used by server and client connectors.</p>
* <p>Use {@link Server} to configure server-side connectors,
* and {@link Client} to configure HTTP or WebSocket clients.</p>
*/
@ManagedObject
public class SoffidSslContextFactory extends org.eclipse.jetty.util.ssl.SslContextFactory.Server
{
    private static final Logger LOG = LoggerFactory.getLogger(SoffidSslContextFactory.class);
    HashMap<String, X509Certificate> certs = new HashMap<>();

    public SoffidSslContextFactory() {
		super();
	}
	
	KeyStore trustStore;
	private List<X509Certificate> trustedCerts;
	X509Certificate rootCert;
	
	@Override
	protected KeyStore loadTrustStore(Resource resource) throws Exception {
		return trustStore;
	}
	
	public KeyStore getTrustStore() {
		return trustStore;
	}
	public void setTrustStore(KeyStore trustStore) {
		this.trustStore = trustStore;

		trustedCerts = new LinkedList<X509Certificate>();
		try {
			rootCert = (X509Certificate) trustStore.getCertificate(SeyconKeyStore.ROOT_CERT);
			for (Enumeration<String> e = trustStore.aliases(); e.hasMoreElements();) {
				String alias = e.nextElement();
				X509Certificate cert = (X509Certificate) trustStore.getCertificate(alias);
	
				if (cert != null)
					trustedCerts.add(cert);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

    protected TrustManager[] getTrustManagers(KeyStore trustStore, Collection<? extends CRL> crls) throws Exception {
    	return TRUST_ALL_CERTS;
    }

    public void validateCerts(X509Certificate[] certs) throws Exception
    {
        if (certs.length == 0)
        {
            throw new IllegalStateException("Invalid certificate chain");
        }

        if (rootCert != null && certs.length == 2 && rootCert.equals(certs[1])) {
        	certs[0].verify(rootCert.getPublicKey());
        	certs[0].checkValidity();
        	return;
        }
        if (trustedCerts.contains(certs[0]))
        	return;
        LOG.warn("Unable to validate certificate chain "+certs[0].getSubjectX500Principal().getName());
        trustedCerts = new CertificateServer().loadTrustedCertificates();
        // Try again
        if (trustedCerts.contains(certs[0]))
        	return;
        throw new CertificateException("Unable to validate certificate: " + certs[0].getSubjectX500Principal().getName());
    }
}

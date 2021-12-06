package com.soffid.iam.sync.jetty;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CRL;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.CertificateValidator;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public SoffidSslContextFactory() {
		super();
	}
	
	KeyStore trustStore;
	@Override
	protected KeyStore loadTrustStore(Resource resource) throws Exception {
		return trustStore;
	}
	
	public KeyStore getTrustStore() {
		return trustStore;
	}
	public void setTrustStore(KeyStore trustStore) {
		this.trustStore = trustStore;
	}

    public void validateCerts(X509Certificate[] certs) throws Exception
    {
        try
        {
            if (certs.length == 0)
            {
                throw new IllegalStateException("Invalid certificate chain");
            }

            X509CertSelector certSelect = new X509CertSelector();
            certSelect.setCertificate(certs[0]);

            // Configure certification path builder parameters
            PKIXBuilderParameters pbParams = new PKIXBuilderParameters(trustStore, certSelect);
            pbParams.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(Arrays.asList(certs))));

            // Set maximum certification path length
            pbParams.setMaxPathLength(-1);

            // Disable revocation checking
            pbParams.setRevocationEnabled(false);

            // Build certification path
            CertPathBuilderResult buildResult = CertPathBuilder.getInstance("PKIX").build(pbParams);

            // Validate certification path
            CertPathValidator.getInstance("PKIX").validate(buildResult.getCertPath(), pbParams);
        }
        catch (GeneralSecurityException gse)
        {
            LOG.debug("Unable to validate certificate chain", gse);
            throw new CertificateException("Unable to validate certificate: " + gse.getMessage(), gse);
        }
    }

}

package com.soffid.iam.sync.engine.cert;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import com.soffid.iam.ssl.TrustedCertificateLoader;

import es.caib.seycon.ng.exception.InternalErrorException;

public class SyncServerTrustedCertificateLoader implements TrustedCertificateLoader {
	static boolean recursive = false;
	@Override
	public List<X509Certificate> loadCerts(KeyStore ks) throws IOException {
		if (!recursive ) {
			try {
					recursive = true;
					return new CertificateServer().loadTrustedCertificates();
			} catch (Exception e) {
				return null;
			} finally {
				recursive = false;
			}
		}
		else
			return null;
		
	}

}

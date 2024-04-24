package com.soffid.iam.sync.engine.cert;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;

import com.soffid.iam.api.Password;
import com.soffid.iam.config.Config;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.tools.KubernetesConfig;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.remote.RemoteServiceLocator;

public class UpdateCertsTask implements Runnable {
	Log log = LogFactory.getLog(getClass());
	
	static Long restartOn = null;
	@Override
	public void run() {
		do {
	        try {
	        	try {
	        		Thread.sleep(new Random().nextInt(15 * 60 * 1000));
	        	} catch (InterruptedException e) {}
	        	Config config = Config.getConfig();
	        	new KubernetesConfig().load();
				KeyStore ks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getKeyStoreFile());
				Password password = SeyconKeyStore.getKeyStorePassword();
				X509Certificate cert = (X509Certificate) ks.getCertificate(SeyconKeyStore.MY_KEY);
				long now = System.currentTimeMillis();
				long oneDay = 24 * 60 * 60 * 1000;
				if (cert.getNotAfter().getTime() < now + oneDay * 45) {
					X509Certificate newCert = (X509Certificate) ks.getCertificate(SeyconKeyStore.NEXT_CERT);
					if (newCert == null || newCert.getNotAfter().before(new Date()) ) {
						log.info("Current date:             "+new Date().toGMTString());
						log.info("Current certificate date: "+cert.getNotAfter().toGMTString());
						log.info("Generating new certificate");
						CertificateServer cs = new CertificateServer();
			            KeyPair pair = cs.generateNewKey();
			            PublicKey publicKey = pair.getPublic();
			            PrivateKey privateKey = pair.getPrivate();
			            newCert = cs.createSelfSignedCertificate(getCertificateOU(cert), config.getHostName(),
			            		publicKey, privateKey);
			            new RemoteServiceLocator().getServerService().addCertificate(newCert);
			            ks.setKeyEntry(SeyconKeyStore.NEXT_CERT, privateKey, password.getPassword().toCharArray(), new X509Certificate[] {newCert});
			            SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
			        	new KubernetesConfig().save();
			        }
				}
				if (cert.getNotAfter().getTime() < now + oneDay * 15) {
					X509Certificate newCert = (X509Certificate) ks.getCertificate(SeyconKeyStore.NEXT_CERT);
					log.info("Current date:             "+new Date().toGMTString());
					log.info("Current certificate date: "+cert.getNotAfter().toGMTString());
					log.info("Committed new certificate");
					log.info("Activate new cert after:  "+new Date(cert.getNotAfter().getTime() - oneDay * 15).toGMTString());
					PrivateKey privateKey = (PrivateKey) ks.getKey(SeyconKeyStore.NEXT_CERT, password.getPassword().toCharArray());
					ks.deleteEntry(SeyconKeyStore.NEXT_CERT);
		            ks.setKeyEntry(SeyconKeyStore.MY_KEY, privateKey, password.getPassword().toCharArray(), new X509Certificate[] {newCert});
		            SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
		            restartOn = Long.valueOf(cert.getNotAfter().getTime() - oneDay - 5 * 60 * 1000);
					new KubernetesConfig().save();
				}
				if (restartOn != null && now > restartOn) {
					new KubernetesConfig().save();
					log.info("Restarting to apply new certificate");
					System.exit(1);
				}
				if (Security.isSyncServer() &&  ! Security.isSyncProxy())
					return;
				Thread.sleep(oneDay);
			} catch (Exception e) {
				log.warn("Error running update certs task", e);
				try {
					Thread.sleep(60 * 60 * 1000);
				} catch (InterruptedException e1) {
				}
			}
		} while (true);
	}

	private String getCertificateOU (X509Certificate cert) {
		X500Name name = new X500Name (cert.getSubjectX500Principal().getName());
		String domain = Security.getMasterTenantName();
		for ( RDN rdn: name.getRDNs())
		{
			if (rdn.getFirst() != null &&
					rdn.getFirst().getType().equals( RFC4519Style.ou))
				domain = rdn.getFirst().getValue().toString();
		}
		return domain;
	}
}

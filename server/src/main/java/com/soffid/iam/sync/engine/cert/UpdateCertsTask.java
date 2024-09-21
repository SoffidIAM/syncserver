package com.soffid.iam.sync.engine.cert;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.eclipse.jetty.security.UserPrincipal;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.config.Config;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.tools.KubernetesConfig;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.remote.RemoteServiceLocator;

public class UpdateCertsTask implements Runnable {
	Log log = LogFactory.getLog(getClass());
	
	static Long restartOn = null;
	@Override
	public void run() {
        try {
        	doInitialUpload();
		} catch (Exception e) {
			log.warn("Error uploading current certificate", e);
		}
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

	private void doInitialUpload() throws KeyManagementException, UnrecoverableKeyException, FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InternalErrorException {
    	new KubernetesConfig().load();
    	Config cfg = Config.getConfig();
    	if (cfg.isServer() && ! "true".equals(cfg.getCustomProperty("migrated-certs"))) {
    		DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
    		Collection<Server> servers = dispatcherService.findAllServers();
			KeyStore ks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getKeyStoreFile());
			for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
				String alias = e.nextElement();
				if (alias.startsWith("trusted") || alias.equals(SeyconKeyStore.MY_KEY)) {
					X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
					Server server = findServer(servers, cert);
					if (server != null)
						dispatcherService.addCertificate(server, cert);
				}
			}
			cfg.setCustomProperty("migrated-certs", "true");
			new KubernetesConfig().save();
    	}
	}

	private Server findServer(Collection<Server> servers, X509Certificate cert) throws FileNotFoundException, IOException {
		X500Name name = new X500Name (cert.getSubjectDN().getName());
		String n = null;
		for ( RDN rdn: name.getRDNs())
		{
			if (rdn.getFirst() != null &&
					rdn.getFirst().getType().equals( RFC4519Style.cn))
				n = rdn.getFirst().getValue().toString();
		}
		for (Server server: servers ) {
			if (server.getName().equals(n))
				return server;
		}
		for (Server server: servers ) {
			if (server.getName().equals(Config.getConfig().getHostName()))
				return server;
		}
		return servers.iterator().next();
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

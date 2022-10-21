package com.soffid.iam.sync.engine.cert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.opensaml.xmlsec.signature.impl.X509CertificateBuilder;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteInvokerFactory;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.agent.AgentManager;
import com.soffid.iam.sync.service.CertificateEnrollService;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.utils.Security;

@SuppressWarnings("deprecation")
public class CertificateServer {
    private static final String MASTER_CA_NAME = "CN=RootCA,OU=master,O=Soffid";
	KeyStore ks;
    KeyStore rootks;
    File file;
    boolean acceptNewCertificates;
    boolean noCheck = false;
    private Password password;
    Config config = Config.getConfig();
    org.apache.commons.logging.Log log  = LogFactory.getLog(getClass());

    public CertificateServer() throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, FileNotFoundException, IOException, InternalErrorException {
        password = SeyconKeyStore.getKeyStorePassword();
        ks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getKeyStoreFile());
        rootks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getRootKeyStoreFile());
    }

    public X509Certificate getRoot() throws KeyStoreException {
        X509Certificate cert = (X509Certificate) ks.getCertificate(SeyconKeyStore.ROOT_CERT);
        return cert;
    }

    public void createRoot() throws KeyStoreException, NoSuchAlgorithmException,
            NoSuchProviderException, CertificateException, FileNotFoundException, IOException,
            InvalidKeyException, IllegalStateException, SignatureException,
            UnrecoverableKeyException, InternalErrorException, KeyManagementException {
        log.info("Generating RSA keys ");
        

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        SecureRandom random = new SecureRandom();
        
        keyGen.initialize(2048, random);
        X509Certificate cert = (X509Certificate) rootks.getCertificate(SeyconKeyStore.ROOT_CERT);
        
        if (! rootks.containsAlias(SeyconKeyStore.ROOT_CERT))
        {
        	// Generar clave raiz
        	KeyPair pair = keyGen.generateKeyPair();
        	cert = createCertificate("master", "RootCA", pair.getPublic(), pair.getPrivate(), null, true);
        	rootks.setKeyEntry(SeyconKeyStore.ROOT_KEY, pair.getPrivate(), password.getPassword()
        			.toCharArray(), new X509Certificate[] { cert });
        	
        	// Guardar trusted cert
        	ks.setCertificateEntry(SeyconKeyStore.ROOT_CERT, cert);
        	
        }


        // Generar clave servidor
        Certificate oldCert = (Certificate) ks.getCertificate(SeyconKeyStore.MY_KEY);
        PrivateKey secretKey = (PrivateKey) ks.getKey(SeyconKeyStore.MY_KEY, password.getPassword().toCharArray());
        PublicKey publicKey;
        if (secretKey == null || oldCert == null)
        {
        	KeyPair pair2 = keyGen.generateKeyPair();
        	secretKey = pair2.getPrivate();
        	publicKey = pair2.getPublic();
        }
        else
        {
        	publicKey = oldCert.getPublicKey();
        }
        File ksFile = new File (config.getHomeDir(), "conf/root.jks");
        log.info("Storing keystore "+ ksFile);
        SeyconKeyStore.saveKeyStore(rootks, ksFile);
        	
        X509Certificate servercert = createCertificate("master", config.getHostName(), publicKey, false);
		ks.setKeyEntry(SeyconKeyStore.MY_KEY, secretKey, password.getPassword()
                .toCharArray(), new X509Certificate[] { servercert, cert });

        // Guardar certificado
        log.info("Storing keystore "+SeyconKeyStore.getKeyStoreFile());
        SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
    }

    public X509Certificate createCertificate(String tenant, String hostName, PublicKey certificateKey)
            throws CertificateEncodingException, InvalidKeyException, IllegalStateException,
            NoSuchProviderException, NoSuchAlgorithmException, SignatureException,
            UnrecoverableKeyException, KeyStoreException {
    	return createCertificate(tenant, hostName, certificateKey, false);
    }

    
    public X509Certificate createCertificate(String tenant, String hostName, PublicKey certificateKey, boolean root)
            throws CertificateEncodingException, InvalidKeyException, IllegalStateException,
            NoSuchProviderException, NoSuchAlgorithmException, SignatureException,
            UnrecoverableKeyException, KeyStoreException {

        try {
			rootks = SeyconKeyStore.loadKeyStore(SeyconKeyStore.getRootKeyStoreFile());
		} catch (Exception e) {
			throw new KeyStoreException("Error loading root key ",e);
		}
        Key key = rootks.getKey(SeyconKeyStore.ROOT_KEY, password.getPassword().toCharArray());
        for ( Enumeration<String> enumeration = rootks.aliases(); enumeration.hasMoreElements();)
        {
        	String s = enumeration.nextElement();
        	log.info(">> "+s);
        }
        X509Certificate rootCert = (X509Certificate) rootks.getCertificate(SeyconKeyStore.ROOT_KEY);
        return createCertificate(tenant, hostName, certificateKey, (PrivateKey) key, rootCert, root);
    }

    public X509Certificate createCertificate(String tenant, String hostName, PublicKey certificateKey,
            PrivateKey signerKey, X509Certificate rootCert, boolean root) throws CertificateEncodingException, InvalidKeyException,
            IllegalStateException, NoSuchProviderException, NoSuchAlgorithmException,
            SignatureException {
        X509V3CertificateGenerator generator = getX509Generator(rootCert);
        Vector<ASN1ObjectIdentifier> tags = new Vector<ASN1ObjectIdentifier>();
        Vector<String> values = new Vector<String>();
        tags.add (RFC4519Style.cn); values.add (hostName);
        tags.add (RFC4519Style.ou); values.add (tenant);
        tags.add (RFC4519Style.o); values.add ("Soffid");
        X509Name name = new X509Name(tags, values);
        generator.setSubjectDN(name);
        generator.setPublicKey(certificateKey);
        Calendar c2 = Calendar.getInstance();
        c2.add(Calendar.DAY_OF_MONTH, -5);
        generator.setNotBefore(c2.getTime());
        String algorithm = "SHA256WithRSA";
        generator.setSignatureAlgorithm(algorithm);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 10);
        generator.setNotAfter(c.getTime());
        if (root)
        {
        	log.info("Creating root certificate authority");
        	generator.addExtension(
        		    X509Extensions.BasicConstraints, true, new BasicConstraints(true));
        }
        else 
        {
	        GeneralNames subjectAltName = new GeneralNames(
	        		new GeneralName[] {
	            			new GeneralName(GeneralName.dNSName, hostName),
	            			new GeneralName(GeneralName.dNSName, "*." + hostName)
	        		});
	        generator.addExtension(X509Extensions.SubjectAlternativeName, false, subjectAltName);
        }

        log.debug("Creating cert for "+certificateKey+" publickey");
        X509Certificate cert = generator.generate(signerKey, "BC");
        log.debug("Generated cert "+cert);

        return cert;
    }

    private static final String PRIVATE_KEY = SeyconKeyStore.MY_KEY + ".private";

    public void obtainCertificate(String serverUrl, String adminTenant, String adminUser, Password adminPassword,
            String domain, boolean remote) throws NoSuchAlgorithmException, NoSuchProviderException,
            KeyStoreException, FileNotFoundException, CertificateException, IOException,
            InvalidKeyException, UnrecoverableKeyException, IllegalStateException,
            SignatureException, InternalErrorException,
            CertificateEnrollWaitingForAproval, CertificateEnrollDenied, KeyManagementException {
        System.out.println("Obtain certificate");
        PrivateKey privateKey = (PrivateKey) ks.getKey(PRIVATE_KEY, SeyconKeyStore
                .getKeyStorePassword().getPassword().toCharArray());

        X509Certificate selfSigned = (X509Certificate) ks.getCertificate(PRIVATE_KEY);
        PublicKey publicKey = selfSigned == null ? null : selfSigned.getPublicKey();
        RemoteServiceLocator rsl = new RemoteServiceLocator(serverUrl);
        System.out.println("Connecting to " + serverUrl);
        CertificateEnrollService enrollService = rsl.getCertificateEnrollService();
        String hostName = config.getHostName();
        String port = config.getPort();

        if (publicKey == null || privateKey == null) {
            KeyPair pair = generateNewKey();
            publicKey = pair.getPublic();
            privateKey = pair.getPrivate();
            RemoteInvokerFactory factory = new RemoteInvokerFactory();
            CertificateFactory certfactory = CertificateFactory.getInstance("X509", "BC");

            X509Certificate root = enrollService.getRootCertificate();
            ks.setCertificateEntry(SeyconKeyStore.ROOT_CERT, root);
            selfSigned = createCertificate(adminTenant, hostName, publicKey, privateKey, root, false);
            ks.setKeyEntry(PRIVATE_KEY, privateKey, password.getPassword().toCharArray(),
                    new Certificate[] { selfSigned });
            SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
        }
        
        if (config.getRequestId() == null) {
            long id = enrollService.createRequest(adminTenant, adminUser, adminPassword.getPassword(), domain,
            				hostName+":"+port, publicKey);
            config.setRequestId(Long.toString(id));
            System.out.println ("The certificate request has been issued.");
        } 
        
        while ( true )
        {
            try {
                Long l = Long.decode(config.getRequestId());
                X509Certificate root = enrollService.getRootCertificate();
                X509Certificate cert = enrollService.getCertificate(adminTenant, adminUser,
                        adminPassword.getPassword(), domain, hostName+":"+port, l, remote);
                ks.setKeyEntry(SeyconKeyStore.MY_KEY, privateKey, SeyconKeyStore
                        .getKeyStorePassword().getPassword().toCharArray(), new X509Certificate[] {
                        cert, root });
                ks.deleteEntry(PRIVATE_KEY);
                // Guardar certificado
                SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
                System.out.println ("Your certificate has been successfully generated");
                break;
            } catch (CertificateEnrollDenied e) {
                ks.deleteEntry(PRIVATE_KEY);
                // Guardar certificado
                SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
                throw e;
            } 
            catch (CertificateEnrollWaitingForAproval e)
            {
            	System.out.println ("Waiting for administrator approval...");
            	try
				{
					Thread.sleep(30000);
				}
				catch (InterruptedException e1)
				{
				}
            }
        }

    }

	public KeyPair generateNewKey() throws NoSuchAlgorithmException, NoSuchProviderException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
		SecureRandom random = new SecureRandom();

		keyGen.initialize(2048, random);

		// Generar clave para agente o servidor secundario
		KeyPair pair = keyGen.generateKeyPair();
		return pair;
	}

    private X509V3CertificateGenerator getX509Generator(X509Certificate rootCert) {

        long now = System.currentTimeMillis() - 1000 * 60 * 10; // 10 minutos
        long l = now + 1000L * 60L * 60L * 24L * 365L * 5L; // 5 años
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        if (rootCert == null)
        {
        	generator.setIssuerDN(new X509Name(MASTER_CA_NAME));
        }
        else
        {
        	generator.setIssuerDN(rootCert.getSubjectX500Principal());	
        }
        generator.setNotAfter(new Date(l));
        generator.setNotBefore(new Date(now));
        generator.setSerialNumber(BigInteger.valueOf(now));
        generator.setSignatureAlgorithm("SHA256WithRSA");
        return generator;
    }

    public boolean hasServerKey() throws UnrecoverableKeyException, KeyStoreException,
            NoSuchAlgorithmException {
        return (ks.getKey(SeyconKeyStore.MY_KEY, password.getPassword().toCharArray()) != null);
    }

    public boolean hasRootKey() throws UnrecoverableKeyException, KeyStoreException,
            NoSuchAlgorithmException {
        return (rootks.getKey(SeyconKeyStore.ROOT_KEY, password.getPassword().toCharArray()) != null);
    }

	public void storeCertificate(KeyPair temporaryKey, X509Certificate cert, X509Certificate root) throws KeyStoreException, KeyManagementException, UnrecoverableKeyException, FileNotFoundException, NoSuchAlgorithmException, CertificateException, IOException {
		File f = SeyconKeyStore.getKeyStoreFile();
		File oldFile = new File (f.getPath()+"-"+new Date().toString());
        SeyconKeyStore.saveKeyStore(ks, oldFile);
		
        ks.setCertificateEntry(SeyconKeyStore.ROOT_CERT, root);
        SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());

        PrivateKey privateKey = temporaryKey.getPrivate();
        ks.setKeyEntry(SeyconKeyStore.MY_KEY, privateKey, SeyconKeyStore
                .getKeyStorePassword().getPassword().toCharArray(), new X509Certificate[] {
                cert, root });
        ks.deleteEntry(PRIVATE_KEY);
        // Guardar certificado
        SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
	}
	
	public void regenerateCertificates(boolean force)  throws Exception
	{
		Config config = Config.getConfig();
		Map<String, PublicKey> keys = new HashMap<String, PublicKey>();
		Map<String, AgentManager> managers = new HashMap<String, AgentManager>();
		// Generate new root ca
		File f = SeyconKeyStore.getRootKeyStoreFile();
		if ( !f.canRead())
		{
			if (force)
			{
				log.warn("Generating certificate on server "+config.getHostName()+". Now this is the main syncserver");
			}
			else
			{
				log.warn("Only main syncserver can regenerate certificates. Add -force flag to promote this one as the main syncserver");
				return;
			}
		}
		File oldFile = new File (f.getPath()+"-"+new Date().toString());
		File newFile = new File (f.getPath()+".new");

		log.info("Storing root certificate copy into "+oldFile.getPath());
        SeyconKeyStore.saveKeyStore(rootks, oldFile);

		log.info("Generating new root certificate into "+newFile.getPath()+" ...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        SecureRandom random = new SecureRandom();
        
        keyGen.initialize(2048, random);
    	KeyPair caRootPair = keyGen.generateKeyPair();
    	X509Certificate rootCert = createCertificate("master", "RootCA", caRootPair.getPublic(), caRootPair.getPrivate(), null, true);
    	rootks.setKeyEntry(SeyconKeyStore.ROOT_KEY, caRootPair.getPrivate(), password.getPassword()
    			.toCharArray(), new X509Certificate[] { rootCert });

        SeyconKeyStore.saveKeyStore(rootks, newFile);
    	
    	// Generate new agent keys
        DispatcherService dispatcherService = ServiceLocator.instance().getDispatcherService();
		for ( Server server: dispatcherService.findAllServers())
        {
        	log.info("Generating key for "+server.getUrl());
        	RemoteServiceLocator rsl = new RemoteServiceLocator(server.getUrl());
        	AgentManager agentManager = rsl.getAgentManager();
        	PublicKey key = agentManager.generateNewKey();
        	keys.put(server.getUrl(), key);
        	managers.put(server.getUrl(), agentManager);
        }

        log.info("Generating certificates");
    	// Generate new agent certificates
        for ( Server server: dispatcherService.findAllServers())
        {
        	log.info("Generating certificate for "+server.getUrl());
        	
        	String tenants[] = dispatcherService.getServerTenants(server);
        	PublicKey key = keys.get(server.getUrl());
        	
        	String tenant = tenants != null && tenants.length == 1? tenants[0]: Security.getMasterTenantName();
        	
       		X509Certificate serverCert = createCertificate(tenant, server.getName(),  key, caRootPair.getPrivate(), rootCert, false);
        	
        	AgentManager agentManager = managers.get(server.getUrl());
        	
        	boolean success = false;
        	int retries = 0;
        	do {
        		try {
        			agentManager.storeNewCertificate(serverCert, rootCert);
        			success = true;
        		} catch (Exception e) {
        			if (retries > 10)
        			{
        				log.warn("Unable to install certificate for server "+ server.getUrl(), e);
        				break;
        			}
        			else
        			{
        				log.warn("Error trying to install certificate in server "+server.getUrl()+": " +e.toString() );
        				log.warn("Retrying in 15 seconds...");
        				Thread.sleep(15000);
        			}
        		}
        	} while (! success);
        }
        
        log.info("All sync servers recertified. Commiting new certificate authority");
        
        SeyconKeyStore.saveKeyStore(rootks, f);
        
        log.info("DONE");
        
	}

	public void extendCertificates()  throws Exception
	{
		Config config = Config.getConfig();
		Map<String, PublicKey> keys = new HashMap<String, PublicKey>();
		Map<String, AgentManager> managers = new HashMap<String, AgentManager>();
		// Generate new root ca
		File froot = SeyconKeyStore.getRootKeyStoreFile();
		if ( !froot.canRead()) {
			log.warn("Only main syncserver can regenerate certificates. Add -force flag to promote this one as the main syncserver");
			return;
		}

		System.out.println("Generating new root certificate into "+froot.getPath()+" ...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        SecureRandom random = new SecureRandom();
        
        keyGen.initialize(2048, random);
    	KeyPair caRootPair = keyGen.generateKeyPair();
    	X509Certificate rootCert = createCertificate("master", "RootCA", caRootPair.getPublic(), caRootPair.getPrivate(), null, true);
    	rootks.setKeyEntry(SeyconKeyStore.ROOT_KEY, caRootPair.getPrivate(), password.getPassword()
    			.toCharArray(), new X509Certificate[] { rootCert });

    	SeyconKeyStore.saveKeyStore(rootks, froot);
    	
		System.out.println("Extending certificates");
		// Generate new root ca
		KeyPair pk = generateNewKey();
       	PublicKey key = pk.getPublic();

        log.info("Generating certificates");
    	// Generate new agent certificates
       	String tenant = "master";
        	
  		X509Certificate serverCert = createCertificate(tenant, Config.getConfig().getHostName(),  key, false);
  		
  		ks.setKeyEntry(SeyconKeyStore.MY_KEY, pk.getPrivate(), password.getPassword().toCharArray(), new X509Certificate[] { serverCert, rootCert } );
  		ks.setCertificateEntry(SeyconKeyStore.ROOT_CERT, rootCert);
        File ksFile = new File (config.getHomeDir(), "conf/keystore.jks");
        System.out.println("Storing keystore "+ ksFile);
        SeyconKeyStore.saveKeyStore(ks, ksFile);
		System.out.println("Extended certificates");
        
	}

}

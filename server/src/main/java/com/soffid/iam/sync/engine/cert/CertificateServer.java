package com.soffid.iam.sync.engine.cert;

import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.Vector;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Password;
import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteInvokerFactory;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.service.CertificateEnrollService;

import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;

public class CertificateServer {
    private static final String MASTER_CA_NAME = "CN=RootCA,OU=master,O=Soffid";
	KeyStore ks;
    KeyStore rootks;
    File file;
    boolean acceptNewCertificates;
    boolean noCheck = false;
    private Password password;
    Config config = Config.getConfig();
    Logger log = Log.getLogger("CertificateServer");

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

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        SecureRandom random = new SecureRandom();

        keyGen.initialize(2048, random);

        // Generar clave raiz
        KeyPair pair = keyGen.generateKeyPair();
        X509V3CertificateGenerator generator = getX509Generator();
        generator.setSubjectDN(new X509Name(MASTER_CA_NAME));
        X509Certificate cert = createCertificate("master", "RootCA", pair.getPublic(), pair.getPrivate(), true);
        rootks.setKeyEntry(SeyconKeyStore.ROOT_KEY, pair.getPrivate(), password.getPassword()
                .toCharArray(), new X509Certificate[] { cert });

        // Guardar trusted cert
        ks.setCertificateEntry(SeyconKeyStore.ROOT_CERT, cert);

        // Generar clave servidor
        pair = keyGen.generateKeyPair();
        X509Certificate servercert = createCertificate("master", config.getHostName(), pair.getPublic(), false);
        ks.setKeyEntry(SeyconKeyStore.MY_KEY, pair.getPrivate(), password.getPassword()
                .toCharArray(), new X509Certificate[] { servercert, cert });

        // Guardar certificado
        SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
        SeyconKeyStore.saveKeyStore(rootks, SeyconKeyStore.getRootKeyStoreFile());
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

        Key key = rootks.getKey(SeyconKeyStore.ROOT_KEY, password.getPassword().toCharArray());
        log.debug("Got root key {}", SeyconKeyStore.ROOT_CERT, null);
        return createCertificate(tenant, hostName, certificateKey, (PrivateKey) key, root);
    }

    public X509Certificate createCertificate(String tenant, String hostName, PublicKey certificateKey,
            PrivateKey signerKey, boolean root) throws CertificateEncodingException, InvalidKeyException,
            IllegalStateException, NoSuchProviderException, NoSuchAlgorithmException,
            SignatureException {
        X509V3CertificateGenerator generator = getX509Generator();
        Vector<ASN1ObjectIdentifier> tags = new Vector<ASN1ObjectIdentifier>();
        Vector<String> values = new Vector<String>();
        tags.add (RFC4519Style.cn); values.add (hostName);
        tags.add (RFC4519Style.ou); values.add (tenant);
        tags.add (RFC4519Style.o); values.add ("Soffid");
        X509Name name = new X509Name(tags, values);
        generator.setSubjectDN(name);
        generator.setPublicKey(certificateKey);
        generator.setNotBefore(new Date());
        generator.setSignatureAlgorithm("SHA256WithRSA");
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 10);
        generator.setNotAfter(c.getTime());
        if (root)
        {
        	log.info("Creating root certificate authority", null, null);
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

        log.debug("Creating cert for {} publickey =", name, certificateKey.toString());
        X509Certificate cert = generator.generate(signerKey, "BC");
        log.debug("Generated cert {}", cert, null);

        return cert;
    }

    private static final String PRIVATE_KEY = SeyconKeyStore.MY_KEY + ".private";

    public void obtainCertificate(String serverUrl, String adminTenant, String adminUser, Password adminPassword,
            String domain) throws NoSuchAlgorithmException, NoSuchProviderException,
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

        if (publicKey == null || privateKey == null) {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            SecureRandom random = new SecureRandom();

            keyGen.initialize(2048, random);

            // Generar clave para agente o servidor secundario
            KeyPair pair = keyGen.generateKeyPair();
            publicKey = pair.getPublic();
            privateKey = pair.getPrivate();
            RemoteInvokerFactory factory = new RemoteInvokerFactory();
            CertificateFactory certfactory = CertificateFactory.getInstance("X509", "BC");

            X509Certificate root = enrollService.getRootCertificate();
            ks.setCertificateEntry(SeyconKeyStore.ROOT_CERT, root);
            selfSigned = createCertificate(adminTenant, hostName, publicKey, privateKey, false);
            ks.setKeyEntry(PRIVATE_KEY, privateKey, password.getPassword().toCharArray(),
                    new Certificate[] { selfSigned });
            SeyconKeyStore.saveKeyStore(ks, SeyconKeyStore.getKeyStoreFile());
        }
        
        if (config.getRequestId() == null) {
            long id = enrollService.createRequest(adminTenant, adminUser, adminPassword.getPassword(), domain,
                    hostName, publicKey);
            config.setRequestId(Long.toString(id));
            System.out.println ("The certificate request has been issued.");
        } 
        
        while ( true )
        {
            try {
                Long l = Long.decode(config.getRequestId());
                X509Certificate root = enrollService.getRootCertificate();
                X509Certificate cert = enrollService.getCertificate(adminTenant, adminUser,
                        adminPassword.getPassword(), domain, hostName, l);
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

    private X509V3CertificateGenerator getX509Generator() {

        long now = System.currentTimeMillis() - 1000 * 60 * 10; // 10 minutos
        long l = now + 1000L * 60L * 60L * 24L * 365L * 5L; // 5 años
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        generator.setIssuerDN(new X509Name(MASTER_CA_NAME));
        generator.setNotAfter(new Date(l));
        generator.setNotBefore(new Date(now));
        generator.setSerialNumber(BigInteger.valueOf(now));
        generator.setSignatureAlgorithm("sha1WithRSAEncryption");
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
}

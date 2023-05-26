package com.soffid.iam.sync.service.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.ServerEntity;
import com.soffid.iam.model.ServerEntityDao;
import com.soffid.iam.model.SystemEntity;
import com.soffid.iam.model.SystemEntityDao;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.tools.KubernetesConfig;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.util.Base64;

public class SecretKeyGenerator {
	public KeyPair generate() throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, IllegalStateException, SignatureException, KeyManagementException, UnrecoverableKeyException, InternalErrorException {
        Config config = Config.getConfig();

        Password password = SeyconKeyStore.getKeyStorePassword();
        // Localizar clave publica y privada
        File f = SeyconKeyStore.getKeyStoreFile();
        KeyStore ks = SeyconKeyStore.loadKeyStore(f);
        // Generate key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        SecureRandom r = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(3072, r);
        KeyPair pair = keyGen.genKeyPair();
        
        PrivateKey privateKey = pair.getPrivate();
        
        // Generate public certificate
        PublicKey publickey = pair.getPublic();
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        generator.setSubjectDN(new X509Name("CN=" + config.getHostName()
                + ", O=SEYCON-SECRET"));
        generator.setIssuerDN(new X509Name("CN=" + config.getHostName()
                + ", O=SEYCON-SECRET"));
        generator.setSerialNumber(BigInteger.ONE);
        generator.setPublicKey(publickey);
        generator.setSignatureAlgorithm("SHA256withRSA");
        Date now = new Date ();
        Date start = new Date (now.getTime()-24*60*60*1000);  // Desde ayer
        Date end = new Date (now.getTime()+10*365*24*60*60*1000);  // Por diez años
        generator.setNotAfter(end);
        generator.setNotBefore(start);
        X509Certificate cert = generator.generate(privateKey);
        
        // Store key
        ks.setKeyEntry("secretsKey", privateKey, password.getPassword().toCharArray(),
                new Certificate[] { cert });
        SeyconKeyStore.saveKeyStore(ks, f);
        new KubernetesConfig().save();

        
        final ServerEntityDao dao = (ServerEntityDao) ServiceLocator.instance().getService("serverEntityDao");
        ServiceLocator.instance().getAsyncRunnerService().runNewTransaction(() -> {
        	ServerEntity server = dao.findByName(config.getHostName());
        	server.setPk(pair.getPublic().getEncoded());
        	dao.update(server);
        	return null;
        });
        
        return pair;
	}
}

package bubu.test.seycon;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.crypto.Cipher;

import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.config.Config;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.tools.KubernetesConfig;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.util.Base64;

public class RSATest {
	public static void main(String args[]) throws Exception {
		
		KeyPair pair = generate();
		String src = "ThisIsATest1.0";
		
		Security.addProvider(new BouncyCastleProvider());
		
		for (Provider provider : Security.getProviders()) {
		    for (Provider.Service service : provider.getServices()) {
		        String algorithm = service.getAlgorithm();
		        if (algorithm.contains("OAE"))
		        System.out.println(algorithm);
		    }
		}
		
		final String ALGORITHM = "RSA/None/OAEPWithSHA256AndMGF1Padding";
		Cipher c = Cipher.getInstance(ALGORITHM, "BC");
		c.init(Cipher.ENCRYPT_MODE, pair.getPublic());
		int bs = c.getBlockSize();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
	
		byte[] b = src.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < b.length; i += bs) {
		    byte r[] = c.doFinal(b, i, b.length - i < bs ? b.length - i : bs);
		    bout.write(r);
		    System.out.println(bs);
		    System.out.println(r.length);
		}
		byte rr[] = bout.toByteArray();

		
		System.out.println(Base64.encodeBytes(rr));

		c = Cipher.getInstance(ALGORITHM, "BC");
		c.init(Cipher.DECRYPT_MODE, pair.getPrivate());
		bs = c.getBlockSize();
		System.out.println(bs);
		bout = new ByteArrayOutputStream();
	
		b = rr;
		for (int i = 0; i < b.length; i += bs) {
		    byte r[] = c.doFinal(b, i, b.length - i < bs ? b.length - i : bs);
		    bout.write(r);
		}
		System.out.println(bout.toString());
	}
	
	
	public static KeyPair generate() throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, IllegalStateException, SignatureException, KeyManagementException, UnrecoverableKeyException, InternalErrorException {
        Config config = Config.getConfig();
        String hostName = InetAddress.getLocalHost().getHostName();

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
        
        return pair;
	}

}

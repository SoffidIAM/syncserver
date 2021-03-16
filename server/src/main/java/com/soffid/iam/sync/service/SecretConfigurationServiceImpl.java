package com.soffid.iam.sync.service;

import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.ServerEntity;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.SecretConfigurationServiceBase;
import com.soffid.iam.sync.tools.KubernetesConfig;

import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.exception.InternalErrorException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
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
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

public class SecretConfigurationServiceImpl extends
        SecretConfigurationServiceBase {

    Server currentServer;
    Collection<Server> servers = null;
    long lastServerUpdate = 0;
    
    PrivateKey privateKey;
    
    String currentToken;
    String previousToken;
    Date tokenChange;
    
    Logger log = Log.getLogger("SecretConfiguration");
    private Config config;
    private PublicKey publicKey;

   

    @Override
    protected PrivateKey handleGetPrivateKey() throws Exception {
        init ();
        return privateKey;
    }

    @Override
    protected Collection<Server> handleGetAllServers() throws Exception {
        init ();
        if (servers == null || lastServerUpdate + 300000 < System.currentTimeMillis())
        {
            servers = new LinkedList<Server>( getServerEntityDao().toServerList(getServerEntityDao().loadAll()) );
            for (Iterator<Server> it = servers.iterator(); it.hasNext(); )
            {
            	Server s = it.next();
            	if (s.getType().equals(ServerType.PROXYSERVER) || s.getPk() == null)
            		it.remove();
            }
        }
        return servers;
    }

    @Override
    protected Server handleGetCurrentServer() throws Exception {
        init ();
        return currentServer;
    }

    @Override
    protected void handleChangeAuthToken() throws Exception {
        init ();
        generateAuthToken();
        ServerEntity server = getServerEntityDao().load(currentServer.getId());
        server.setAuth(currentToken);
        getServerEntityDao().update(server);
    }

    private void generateAuthToken() {
        StringBuffer b = new StringBuffer();
        Random r = new Random();
        for (int i = 0; i < 32; i++) {
            int j = r.nextInt( 62 );
            if ( j < 26 )
                b.append((char) ('A' + j));
            else if ( j < 52)
                b.append((char) ('a' + j - 26));
            else
                b.append((char) ('0' + j - 52));
        }
        currentToken = b.toString();
        tokenChange = new Date();
        previousToken = currentToken;
    }

    @Override
    protected boolean handleValidateAuthToken(String token) throws Exception {
        init ();
        if (token.equals (currentToken))
            return true;
        if (token.equals(previousToken) && tokenChange != null) {
            long elapsed = new Date().getTime() - tokenChange.getTime();
            if (elapsed < 300000) // 5 minuts 
                return true;
        }
        return false;
    }


    boolean initialized =false;
    public void init() {
        if (initialized)
            return;
        try {
            config = Config.getConfig();
            
            Password password = SeyconKeyStore.getKeyStorePassword();
            // Localizar clave publica y privada
            File f = SeyconKeyStore.getKeyStoreFile();
            KeyStore ks = SeyconKeyStore.loadKeyStore(f);
            privateKey = (PrivateKey) ks.getKey("secretsKey", password.getPassword().toCharArray());
            if (privateKey == null) {
                log.info("Generating secret key", null, null);
                // Generate key pair
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                SecureRandom r = SecureRandom.getInstance("SHA1PRNG");
                keyGen.initialize(1024, r);
                KeyPair pair = keyGen.genKeyPair();
                
                privateKey = pair.getPrivate();
                
                // Generate public certificate
                PublicKey publickey = pair.getPublic();
                X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
                generator.setSubjectDN(new X509Name("CN=" + config.getHostName()
                        + ", O=SEYCON-SECRET"));
                generator.setIssuerDN(new X509Name("CN=" + config.getHostName()
                        + ", O=SEYCON-SECRET"));
                generator.setSerialNumber(BigInteger.ONE);
                generator.setPublicKey(publickey);
                generator.setSignatureAlgorithm("SHA1withRSA");
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
            }
            Certificate certs [] = ks.getCertificateChain("secretsKey");
            if (certs != null && certs.length > 0) {
                publicKey = certs[0].getPublicKey();
            }
            
            updateServerEntity();
            
//            changeAuthToken();
            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException (e);
        }
    }

    private void storeAuthSecret(String token) throws InternalErrorException, SQLException {
        ServerEntity server = getServerEntityDao().load(currentServer.getId());
        server.setAuth(token);
        getServerEntityDao().update(server);
    }

    private void storePublicKey(PublicKey publickey) throws InternalErrorException, SQLException {
        ServerEntity server = getServerEntityDao().load(currentServer.getId());
        server.setPk(publickey.getEncoded());
        getServerEntityDao().update(server);
    }


    private void updateServerEntity () throws InternalErrorException, SQLException, FileNotFoundException, IOException {
        ServerEntity server = getServerEntityDao().findByName(config.getHostName());
        if (server == null) {
            generateAuthToken();
            server = getServerEntityDao().newServerEntity();
            server.setAuth(currentToken);
            server.setName(config.getHostName());
            server.setPk(publicKey.getEncoded());
            getServerEntityDao().create(server); 
        } else {
            server.setPk(publicKey.getEncoded());
            getServerEntityDao().update(server);
        }
        currentServer = getServerEntityDao().toServer(server);
    }
}

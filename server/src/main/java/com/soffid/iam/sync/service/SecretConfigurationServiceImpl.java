package com.soffid.iam.sync.service;

import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.ServerEntity;
import com.soffid.iam.model.ServerInstanceEntity;
import com.soffid.iam.ssl.SeyconKeyStore;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.SecretConfigurationServiceBase;
import com.soffid.iam.sync.service.impl.SecretKeyGenerator;
import com.soffid.iam.sync.tools.KubernetesConfig;

import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.exception.InternalErrorException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
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
import java.security.interfaces.RSAPrivateKey;
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

import javax.crypto.Cipher;

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
    	if (new KubernetesConfig().isKubernetes()) {
    		ServerInstanceEntity instance = getServerInstanceEntityDao().findByServerNameAndInstanceName(config.getHostName(), hostName);
	        if (instance != null) {
	        	instance.setAuth(currentToken);
	        	getServerInstanceEntityDao().update(instance);
	        }
    	}  else {
	        ServerEntity server = getServerEntityDao().load(currentServer.getId());
	        server.setAuth(currentToken);
	        getServerEntityDao().update(server);
    	}
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
	private String hostName;
    public void init() {
        if (initialized)
            return;
        try {
            config = Config.getConfig();
            hostName = InetAddress.getLocalHost().getHostName();

            Password password = SeyconKeyStore.getKeyStorePassword();
            // Localizar clave publica y privada
            File f = SeyconKeyStore.getKeyStoreFile();
            KeyStore ks = SeyconKeyStore.loadKeyStore(f);
            privateKey = (PrivateKey) ks.getKey("secretsKey", password.getPassword().toCharArray());
            if (privateKey == null) {
                log.info("Generating secret key", null, null);
                // Generate key pair
                KeyPair pair = new SecretKeyGenerator().generate();
                privateKey = pair.getPrivate();
            }
            Certificate certs [] = ks.getCertificateChain("secretsKey");
            if (certs != null && certs.length > 0) {
                publicKey = certs[0].getPublicKey();
            }
            
            int bits = 0;
    		if (privateKey instanceof RSAPrivateKey) 
    			bits = ((RSAPrivateKey) privateKey).getModulus().bitLength();

    		if (bits <= 2048) {
    			log.warn("Warning. The encryption key is too weak. Please, generate a new one executing 'configure -reencodeSecrets'", null, null);
    		}
            updateServerEntity();
            
//            changeAuthToken();
            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException (e);
        }
    }

    private void storePublicKey(PublicKey publickey) throws InternalErrorException, SQLException {
        ServerEntity server = getServerEntityDao().load(currentServer.getId());
        server.setPk(publickey.getEncoded());
        getServerEntityDao().update(server);
    }


    private void updateServerEntity () throws InternalErrorException, SQLException, FileNotFoundException, IOException {
    	ServerEntity server = getServerEntityDao().findByName(config.getHostName());
    	if (new KubernetesConfig().isKubernetes()) {
    		ServerInstanceEntity instance = getServerInstanceEntityDao().findByServerNameAndInstanceName(config.getHostName(), hostName);
	        if (instance != null) {
	        	instance.setAuth(currentToken);
	        	getServerInstanceEntityDao().update(instance);
	        }
    	} 
    	
        if (server == null) {
            generateAuthToken();
            server = getServerEntityDao().newServerEntity();
            server.setAuth(currentToken);
            server.setName(config.getHostName());
            server.setPk(publicKey.getEncoded());
            getServerEntityDao().create(server); 
        } else if (publicKey != null){
            server.setPk(publicKey.getEncoded());
            getServerEntityDao().update(server);
        }
    	currentServer = getServerEntityDao().toServer(server);
    }
}

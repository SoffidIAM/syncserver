package es.caib.seycon.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DownloadCert {

	public static void main(String[] args) throws Exception {
        SSLContext ctx;
        ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$

        ctx.init(new KeyManager[0], new TrustManager[] { new AlwaysTrustManager() }, null);

        SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(args[0], Integer.parseInt(args[1]));
        
        s.getOutputStream().write(0);
        s.close();
	}

}


class AlwaysTrustManager implements X509TrustManager {

    private boolean debug;

	/*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    public X509Certificate[] getAcceptedIssuers() {
           return new X509Certificate[0];

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[],
     *      java.lang.String)
     */
    public void checkServerTrusted(X509Certificate[] arg0, String arg1)
            throws CertificateException {
		System.out.println("Received certificate");
		for (X509Certificate cert: arg0)
		{
			StringBuffer b = new StringBuffer();
			b.append (cert.getSubjectDN());
			b.append ("-----BEGIN CERTIFICATE----- \r\n")
				.append(Base64.encodeBytes(cert.getEncoded()))
				.append("\r\n-----END CERTIFICATE-----");
			System.out.println(cert.getSubjectDN().getName()+"\r\n"+
				b.toString());
		}
    	return ;
    }

    public void checkClientTrusted(X509Certificate[] arg0, String arg1)
            throws CertificateException {
        throw new CertificateException("No allowed to use client certificates");
    }

    public AlwaysTrustManager() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException,
            FileNotFoundException, IOException {
    }


}

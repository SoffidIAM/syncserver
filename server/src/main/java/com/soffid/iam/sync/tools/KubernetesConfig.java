package com.soffid.iam.sync.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import com.soffid.iam.config.Config;

public class KubernetesConfig {
	private String host;
	private String port;
	private String hostname;
	private String token;
	private String cert;
	private BaseHttpConnectionFactory connectionFactory;
	Log log = LogFactory.getLog(getClass());
	private String namespace;
	
	public void load () throws FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		Config config = Config.getConfig();
		
		if (isKubernetesRest()) {
			int i;
			configure();
			
			log.info("Loading kubernetes configuration from kubernetes secret "+System.getenv("KUBERNETES_CONFIGURATION_SECRET"));
			URL url = new URL("https://"+host+":"+port+"/api/v1/namespaces/"+namespace+"/secrets/"+System.getenv("KUBERNETES_CONFIGURATION_SECRET"));
			try {
				String response = readURL(url);
				JSONObject json = new JSONObject(response);
				JSONObject data = json.getJSONObject("data");
				for ( Object key: data.keySet() ) {
					String value = data.getString(key.toString());
					dump (config, key.toString(), value);
				}
			} catch (FileNotFoundException e) {
				// Not found
			}
		}
		if (isKubernetesDatabase())
			try {
				new com.soffid.iam.sync.tools.DatabaseConfig().loadFromDatabase();
			} catch (Exception e) {
				throw new IOException(e);
			}
	}

	public boolean isKubernetesDatabase() {
		return System.getenv("DB_CONFIGURATION_TABLE") != null && 
				System.getenv("DB_URL") != null &&
				System.getenv("DB_USER") != null &&
				System.getenv("DB_PASSWORD") != null;
	}

	public boolean isKubernetesRest() {
		return System.getenv("KUBERNETES_SERVICE_HOST") != null &&
				System.getenv("KUBERNETES_CONFIGURATION_SECRET") != null &&
				new File("/var/run/secrets/kubernetes.io/serviceaccount/token").canRead();
	}

	public boolean isKubernetes() {
		return isKubernetesRest() || isKubernetesDatabase();
	}

	private void dump(Config config, String tag, String value) throws IOException {
		File f = new File (config.getHomeDir(), "conf/"+tag);
		FileOutputStream out = new FileOutputStream(f);
		byte[] data = Base64.getDecoder().decode(value);
		out.write(data);
		out.close();
	}

	public String readURL(URL url) throws RemoteException, IOException {
		int i;
		HttpsURLConnection conn = connectionFactory.getConnection(url);
		conn.addRequestProperty("Authorization", "Bearer "+token);
		InputStream in = conn.getInputStream();
		InputStreamReader reader = new InputStreamReader(in);
		StringBuffer sb = new StringBuffer();
		for (i = reader.read();  i >= 0; i = reader.read()) {
			sb.append((char) i);
		}
		in.close();
		String response = sb.toString();
		return response;
	}

	public String send(String method, URL url, String data) throws RemoteException, IOException {
		int i;
		HttpsURLConnection conn = connectionFactory.getConnection(url);
		conn.addRequestProperty("Authorization", "Bearer "+token);
		conn.addRequestProperty("Content-Type", "application/json");
		conn.setDoInput(true);
		conn.setDoOutput(true);
		if ("PATCH".equals(method))
		{
			conn.setRequestMethod("PUT");
			conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
		} else {
			conn.setRequestMethod(method);
		}
		
		byte[] post = data.getBytes("UTF-8");
		conn.addRequestProperty("Content-Length", Integer.toString( post.length) );
		OutputStream out = conn.getOutputStream();
		out.write(post);
		out.close();
		InputStream in = conn.getInputStream();
		InputStreamReader reader = new InputStreamReader(in);
		StringBuffer sb = new StringBuffer();
		for (i = reader.read();  i >= 0; i = reader.read()) {
			sb.append((char) i);
		}
		in.close();
		String response = sb.toString();
		return response;
	}

	public void configure() throws IOException, KeyManagementException, UnrecoverableKeyException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, FileNotFoundException {
		host = System.getenv("KUBERNETES_SERVICE_HOST");
		port = System.getenv("KUBERNETES_SERVICE_PORT");
		hostname = System.getenv("SOFFID_HOSTNAME");
		token = readFile ("/var/run/secrets/kubernetes.io/serviceaccount/token");
		cert = readFile ("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");
		namespace = readFile ("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
		int i = cert.indexOf('\n');
		cert = cert.substring(i+1);
		i = cert.indexOf("\n---");
		cert = cert.substring(0, i);
		cert = cert.replace("\n","");
		connectionFactory = new BaseHttpConnectionFactory(cert);
	}
	
	String readFile(String path) throws IOException {
		FileReader r = new FileReader(path);
		StringBuffer sb = new StringBuffer();
		for (int i = r.read();  i >= 0; i = r.read()) {
			sb.append((char) i);
		}
		return sb.toString();
	}
	
	byte[] readBinaryFile(String path) throws IOException {
		FileInputStream r = new FileInputStream(path);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = r.read();  i >= 0; i = r.read()) {
			out.write(i);
		}
		return out.toByteArray();
	}

	public void save () throws FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, JSONException {
		Config config = Config.getConfig();
		
		if (isKubernetesRest()) {
			log.info("Storing configuration in kubernetes secret "+System.getenv("KUBERNETES_CONFIGURATION_SECRET"));
			configure();
			
			JSONObject data = new JSONObject();
			File dir = new File (config.getHomeDir(), "conf");
			for (File f: dir.listFiles()) {
				byte[] d = readBinaryFile(f.getPath());
				data.put(f.getName(), Base64.getEncoder().encodeToString(d));
			}
			JSONObject secret = new JSONObject();
			secret.put("data", data);
			URL url = new URL("https://"+host+":"+port+"/api/v1/namespaces/"+namespace+"/secrets/"+System.getenv("KUBERNETES_CONFIGURATION_SECRET"));
			try {
				String s = readURL(url);
				secret = new JSONObject(s);
				secret.put("data", data);
				send("PUT", new URL("https://"+host+":"+port+"/api/v1/namespaces/"+namespace+"/secrets/"+System.getenv("KUBERNETES_CONFIGURATION_SECRET")), secret.toString());
			} catch (FileNotFoundException e) {
				secret.put("apiVersion", "v1");
				secret.put("kind", "Secret");
				JSONObject metadata = new JSONObject();
				metadata.put("name", System.getenv("KUBERNETES_CONFIGURATION_SECRET"));
				metadata.put("type", "syncserver");
				secret.put("metadata", metadata);
				secret.put("type", "Opaque");
				send("POST", new URL("https://"+host+":"+port+"/api/v1/namespaces/"+namespace+"/secrets"), secret.toString());
			}
		}
		if (isKubernetesDatabase())
			try {
				new com.soffid.iam.sync.tools.DatabaseConfig().saveToDatabase();
			} catch (Exception e) {
				throw new IOException(e);
			}
	}
}

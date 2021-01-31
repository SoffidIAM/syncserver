package com.soffid.iam.sync.bootstrap.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

public class KubernetesConfig {
	private String host;
	private String port;
	private String hostname;
	private String token;
	private String cert;
	private BaseHttpConnectionFactory connectionFactory;

	public void load () throws FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		Config config = Config.getConfig();
		
		if (System.getenv("KUBERNETS_SERVICE_HOST") != null && new File("/var/run/secrets/kubernets.io/serviceaccount/token").canRead()) {
			int i;
			configure();
			
			URL url = new URL("https://"+host+":"+port+"/api/v1/namespaces/defaults/secrets/soffid_"+hostname);
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
		conn.setRequestMethod(method);
		
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
		host = System.getenv("KUBERNETS_SERVICE_HOST");
		port = System.getenv("KUBERNETES_SERVICE_PORT");
		hostname = System.getenv("SOFFID_HOSTNAME");
		token = readFile ("/var/run/secrets/kubernets.io/serviceaccount/token");
		cert = readFile ("/var/run/secrets/kubernets.io/serviceaccount/ca.crt");
		int i = cert.indexOf('\n');
		cert = cert.substring(i);
		i = cert.indexOf("\n---");
		cert = cert.substring(0, i);
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
		FileReader r = new FileReader(path);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = r.read();  i >= 0; i = r.read()) {
			out.write(i);
		}
		return out.toByteArray();
	}

	public void save () throws FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		Config config = Config.getConfig();
		
		if (System.getenv("KUBERNETS_SERVICE_HOST") != null && new File("/var/run/secrets/kubernets.io/serviceaccount/token").canRead()) {
			int i;
			configure();
			
			JSONObject data = new JSONObject();
			File dir = new File (config.getHomeDir(), "conf");
			for (File f: dir.listFiles()) {
				byte[] d = readBinaryFile(f.getPath());
				data.put(f.getName(), Base64.getEncoder().encodeToString(d));
			}
			JSONObject secret = new JSONObject();
			secret.put("data", data);
			URL url = new URL("https://"+host+":"+port+"/api/v1/namespaces/defaults/secrets/soffid_"+hostname);
			try {
				readURL(url);
				send("PATCH", new URL("https://"+host+":"+port+"/api/v1/namespaces/defaults/secrets"), secret.toString());
			} catch (FileNotFoundException e) {
				secret.put("apiVersion", "v1");
				secret.put("kind", "Secret");
				JSONObject metadata = new JSONObject();
				metadata.put("name", "soffid_"+hostname);
				metadata.put("type", "syncserver");
				secret.put("metadata", metadata);
				secret.put("type", "Opaque");
				send("POST", new URL("https://"+host+":"+port+"/api/v1/namespaces/defaults/secrets"), secret.toString());
			}
		}
	}
}
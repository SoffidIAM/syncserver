package com.soffid.iam.sync.hub.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.iam.config.Config;
import com.soffid.iam.ssl.ConnectionFactory;
import com.soffid.iam.sync.hub.server.Request;
import com.soffid.iam.sync.jetty.JettyServer;

public class RemoteThread  {
	Log log = LogFactory.getLog(getClass());
	private Config config;
	private String targetUrl;
	private JettyServer server;

	public RemoteThread(JettyServer server) {
		this.server = server;
	}

	public void run() {
		try {
			config = Config.getConfig();
			URL u = new URL(config.getCustomProperty("url"));
			targetUrl = u.getProtocol()+"://"+u.getHost()+":"+u.getPort()+"/seycon/hub"; 
			while (true)
			{
				try {
					Request request = fetchRequest();
					if (request == null)
						Thread.sleep(3000);
					else
						processRequest (request);
				} catch (Throwable th) {
					log.warn("Error fetching request from gateway server "+targetUrl, th);
				}
			}
		} catch (Exception e) {
			log.warn("Error in remote thread handler", e);
			System.exit(3);
		}
	}

	private void processRequest(final Request request) {
		new ProcessRequestThread(server, request, targetUrl).start();
	}

	private Request fetchRequest() throws IOException, ClassNotFoundException {
		HttpURLConnection conn = ConnectionFactory.getConnection(new URL(targetUrl));
		conn.setDoInput(true);
		conn.setDoOutput(false);
		conn.setRequestMethod("GET");
		try {
			conn.connect();
		} catch (Exception e ) {
			log.info(e.toString());
			return null;
		}
		InputStream in = conn.getInputStream();
		if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
		{
			ObjectInputStream oin = new ObjectInputStream(in);
			Request req = (Request) oin.readObject();
			oin.close();
			in.close();
			return req;
		}
		else if (conn.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT)
			return null;
		else
			throw new IOException("Unexpected error code "+conn.getResponseCode()+" from sync server gateway");
	}

}

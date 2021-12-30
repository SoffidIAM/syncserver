package com.soffid.iam.sync.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;


public class Updater {
	String [] filesToFetch = new String[] {
			"jetty-http-10.0.7.jar",
			"jetty-io-10.0.7.jar",
			"jetty-security-10.0.7.jar",
			"jetty-server-10.0.7.jar",
			"jetty-servlet-10.0.7.jar",
			"jetty-servlet-api-4.0.6.jar",
			"jetty-util-10.0.7.jar",
			"jetty-webapp-10.0.7.jar",
			"jetty-xml-10.0.7.jar",
			"mariadb-java-client-1.8.0.jar",
			"metrics-core-3.1.2.jar",
			"ojdbc7-12.1.0.2.0.jar",
			"slf4j-api-2.0.0-alpha5.jar",
			"slf4j-log4j12-2.0.0-alpha5.jar",
			"spring-1.2.7.jar",
			"stax2-api-3.1.4.jar",
			"stax-api-1.0-2.jar",
			"woodstox-core-asl-4.4.1.jar",
			"xmlschema-core-2.2.1.jar",
			"xmlsec-2.0.6.jar"
	};
	
	String [] filesToRemove = new String[] {
			"jetty-6.1.11-soffid-1.jar",
			"jetty-util-6.1.11.jar",
			"mysql-connector-java-5.1.21.jar",
			"ojdbc14-10.2.0.4.0-noseal.jar",
			"postgresql-42.2.5.jre7.jar",
			"slf4j-api-1.6.4.jar",
			"slf4j-log4j12-1.6.4.jar",
			"xml-apis-2.10.0.jar"
	};
	
	public void update() {
		
	}

    public static void downloadFile(String sourceURL, String fileName)
            throws Exception, IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            javax.net.ssl.HttpsURLConnection connection = null;
            try {
                URL url = new URL(sourceURL);
                connection = HttpConnectionFactory.getConnection(url);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.connect();
            } catch (Exception e) {
                throw e;
            }
            in = connection.getInputStream();
            File f = new File(fileName);
            boolean replacing = f.exists();
            
            out = new FileOutputStream(replacing? fileName+".new.jar": fileName);
            byte buffer[] = new byte[10240];
            int size = in.read(buffer);
            while (size >= 0) {
                out.write(buffer, 0, size);
                size = in.read(buffer);
            }
            out.flush();
            out.close();
            in.close();
            connection.disconnect();
            if (replacing)
            {
            	FileInputStream in2 = new FileInputStream(fileName+".new.jar");
            	FileOutputStream out2 = new FileOutputStream(fileName);
            	int read;
            	while ( (read = in2.read(buffer)) > 0)
            		out2.write(buffer, 0, read);
            	in2.close();
            	out2.close();
            	f.delete();
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }
}

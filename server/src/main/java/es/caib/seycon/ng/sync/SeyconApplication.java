package es.caib.seycon.ng.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.config.Config;
import com.soffid.iam.ssl.ConnectionFactory;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.bootstrap.FileVersionManager;

import es.caib.seycon.ng.exception.InternalErrorException;

public class SeyconApplication {

    private static FileVersionManager fvm;



	public static void main(String args[]) throws FileNotFoundException, IOException, Exception {
    	try {
    		Class.forName("com.soffid.iam.utils.Security").getMethod("onSyncServer").invoke(null);
    	} catch (Throwable e) { 
			e.printStackTrace();
			downloadCore();
    	}
    	SoffidApplication.main(args);
    }

	private static void downloadCore()
			throws FileNotFoundException, IOException, InternalErrorException, RemoteException, Exception {
		try {
			fvm = new FileVersionManager();
			downloadFile("iam-core");
			downloadFile("iam-tomee");
			downloadFile("bcprov-jdk15on");
			downloadFile("bcpkix-jdk15on");
			fvm.deleteAllCopies("bcprov-jdk16");
			fvm.deleteAllCopies("bcmail-jdk16");
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Restarting .....");
		System.exit(2);
	}

	private static void downloadFile(String module)
			throws FileNotFoundException, IOException, InternalErrorException, RemoteException, Exception {
		System.out.println("Missing core java classes. Downloading "+module+".jar");
		Config config = Config.getConfig();

		String host = config.getServerList().split("[, ]+")[0];
		
		String sourceURL = host + "downloadLibrary?component="+module;
		String targetFile = new File(config.getHomeDir(), "lib/"+module+"-LATEST.jar").getPath();
		System.out.println("Download URL: "+sourceURL);
		System.out.println("Target file:  "+targetFile);
		downloadFile(sourceURL, targetFile);
		System.out.println("Removing old copies");
		fvm.deleteOldCopies(module);
	}
	
    public static void downloadFile(String sourceURL, String fileName)
            throws Exception, IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            javax.net.ssl.HttpsURLConnection connection = null;
            try {
                URL url = new URL(sourceURL);
                connection = ConnectionFactory.getConnection(url);
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
            byte buffer[] = new byte[1024];
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
            	while ( (read = in2.read()) >= 0)
            		out2.write(read);
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


    
    public static es.caib.seycon.ng.sync.jetty.JettyServer getJetty() {
        return new es.caib.seycon.ng.sync.jetty.JettyServer( SoffidApplication.getJetty() );
    }

}

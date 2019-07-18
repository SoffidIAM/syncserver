package es.caib.seycon.ng.sync.bootstrap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;

public class SeyconLoader {

    public static void main(String args[]) throws FileNotFoundException, IOException, Exception {
    	try {
    		Class.forName("com.soffid.iam.utils.Security").getMethod("onSyncServer").invoke(null);
    	} catch (Exception e) { 
    		downloadCore();
    	}
		com.soffid.iam.sync.bootstrap.SeyconLoader.main(args);
    }

	private static void downloadCore()
			throws FileNotFoundException, IOException, InternalErrorException, RemoteException, Exception {
		System.out.println("Missing core java classes. Downloading iam-core.jar");
		Config config = Config.getConfig();
		String host = config.getServerList().split("[, ]+")[0];
		String sourceURL = host + "downloadLibrary?component=iam-core";
		String targetFile = new File(config.getHomeDir(), "lib/iam-core.jar").getPath();
		com.soffid.iam.sync.bootstrap.SeyconLoader.downloadFile(sourceURL, targetFile);
		System.out.println("Restarting .....");

		System.exit(2);
	}

}

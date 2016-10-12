package com.soffid.iam.sync.engine.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.log4j.PropertyConfigurator;
import org.mortbay.log.Slf4jLog;

import com.soffid.iam.config.Config;

public class LogConfigurator {
    private static final String PATH3 = "es/caib/seycon/logging/logging.properties";
	private static final String PATH2 = "com/soffid/iam/logging/logging.properties";
	private static final String PATH1 = "conf/log4j.properties";

	public static void configureLogging () {
        try {
            Config config =  Config.getConfig ();
            File logFile = new File(config.getHomeDir(), PATH1);
            File logFile2 = new File(config.getHomeDir(), PATH2);
            if (logFile.canRead()) {
                PropertyConfigurator.configure(logFile.toURI().toURL()); 
            } 
            else  
            {
                PropertyConfigurator.configure(ClassLoader
                    .getSystemResource(PATH2));
            }
            org.mortbay.log.Log.setLog(new Slf4jLog());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void configureMinimalLogging () {
        try {
            Config config =  Config.getConfig ();
            PropertyConfigurator.configure(ClassLoader
                    .getSystemResource("com/soffid/iam/logging/minimal.logging.properties"));
            org.mortbay.log.Log.setLog(new Slf4jLog());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

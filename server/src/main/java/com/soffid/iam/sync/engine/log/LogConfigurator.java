package com.soffid.iam.sync.engine.log;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.LogManager;

import org.mortbay.log.Slf4jLog;

import com.soffid.iam.config.Config;

public class LogConfigurator {
	private static final String PATH2 = "com/soffid/iam/logging/logging.properties";
	private static final String PATH1 = "conf/logging.properties";

	public static void configureLogging () {
        try {
            Config config =  Config.getConfig ();
            File logFile = new File(config.getHomeDir(), PATH1);
            if (logFile.canRead()) {
            	LogManager.getLogManager().readConfiguration(new FileInputStream(logFile));
            } 
            else  
            {
            	LogManager.getLogManager().readConfiguration(ClassLoader.getSystemResourceAsStream(PATH2));
            }
            org.mortbay.log.Log.setLog(new Slf4jLog());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void configureMinimalLogging () {
        try {
            LogManager.getLogManager().readConfiguration(ClassLoader
                    .getSystemResourceAsStream("com/soffid/iam/logging/minimal.logging.properties"));
            org.mortbay.log.Log.setLog(new Slf4jLog());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

package es.caib.seycon.ng.sync.engine.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.log4j.PropertyConfigurator;
import org.mortbay.log.Slf4jLog;

import es.caib.seycon.ng.config.Config;

public class LogConfigurator {
    public static void configureLogging () {
        try {
            Config config =  Config.getConfig ();
            File logFile = new File(config.getHomeDir(), "conf/log4j.properties");
            if (logFile.canRead()) {
                PropertyConfigurator.configure(logFile.toURI().toURL()); 
            } else {
                PropertyConfigurator.configure(ClassLoader
                    .getSystemResource("es/caib/seycon/logging/logging.properties"));
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
                    .getSystemResource("es/caib/seycon/logging/minimal.logging.properties"));
            org.mortbay.log.Log.setLog(new Slf4jLog());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

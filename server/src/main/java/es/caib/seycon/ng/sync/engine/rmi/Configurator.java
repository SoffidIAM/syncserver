package es.caib.seycon.ng.sync.engine.rmi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.jetty.JettyServer;

public class Configurator {

    
    public static void configure(JettyServer jetty) throws FileNotFoundException, IOException, InternalErrorException {
        Logger log = LoggerFactory.getLogger(Configurator.class);
        Config config = Config.getConfig();
        es.caib.seycon.ng.remote.URLManager url = config.getURL();
        if (url.isRMI())
        {
            LogonMgrImpl logonServer = new LogonMgrImpl();
            ServerImpl serverImpl = new ServerImpl();
            int port = url.getRMIURL().getPort();
            try {
                Naming.lookup(url.getRMIString());
                log.warn(String.format("Server ya escuchando en %s", url.getRMIString()));
                System.exit(0);
            } catch (Exception e) {
            }
            LocateRegistry.createRegistry(port);
            String url2 = url.getRMIString() + "/logon";
            log.info(String.format("Starting RMI %s", url2));
            Naming.rebind(url2, logonServer);
            Naming.rebind(url.getRMIString(), serverImpl);
            jetty.bind(url.getServerURL(), serverImpl, "agent");
            jetty.bind(url.getLogonURL(), logonServer, null);
        }

    }
}

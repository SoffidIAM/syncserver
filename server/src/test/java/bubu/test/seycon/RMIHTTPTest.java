package bubu.test.seycon;

import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.remote.RemoteInvokerFactory;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.remote.URLManager;
import es.caib.seycon.ng.sync.intf.UserInfo;
import es.caib.seycon.ng.sync.servei.LogonService;
import es.caib.seycon.ng.sync.servei.ServerService;

public class RMIHTTPTest {

    /**
     * @param args
     */
    public static void main(String[] args) {
        RemoteInvokerFactory factory = new RemoteInvokerFactory();
        String serverList;
        try {
            RemoteServiceLocator rsl = new RemoteServiceLocator(
                    "https://tticlin2.test.lab:750/seycon/Server");
            if (true) {

                ServerService s = rsl.getServerService();
                serverList = s.getConfig("seycon.server.list");
                System.out.println("Seycon.server.list=" + serverList);
                Usuari ui = s.getUserInfo("u07286", null);
                System.out.println("ui=");
                System.out.println (ui.toString());
            }

            LogonService ls = rsl.getLogonService();
            ls.propagatePassword("u07286", null, "abcabc");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

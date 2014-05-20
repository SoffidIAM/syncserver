package bubu.test.seycon;

import java.net.URL;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.remote.RemoteInvokerFactory;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.sync.servei.LogonService;

public class Stress {
    static String[] arguments;
    static Thread threads[];

    public static void main(String args[]) {
        arguments = args;
        int number = 150;
        threads = new Thread[number];
        for (int i = 0; i < number; i++) {
            threads[i] = new Thread(new Validator(i));
            threads[i].start();
        }
        for (int i = 0; i < number; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }
        System.exit(0);
    }

}

class Validator implements Runnable {
    private int id;

    public Validator(int i) {
        id = i;
    }

    public void run() {
        LogonService server = null;
        try {
            String serverHosts[] = null;
            if (System.getProperty("seycon.server.url") != null)
                serverHosts = System.getProperty("seycon.server.url").split("[, ]+");
            else
                serverHosts = Config.getConfig().getSeyconServerHostList();

            if (serverHosts == null) {
                throw new es.caib.seycon.ng.exception.InternalErrorException(
                        "Falta propiedad seycon.server.list");
            }
            boolean succeed = false;
            for (int i = 0; i < serverHosts.length && !succeed; i++) {
                try {
                    RemoteServiceLocator rsl = new RemoteServiceLocator(serverHosts[i]);
                    server = rsl.getLogonService();
                    succeed = true;
                } catch (Exception e) {
                    // succeed == false
                }
            }
            if (!succeed) {
                throw new es.caib.seycon.ng.exception.InternalErrorException(
                        "No se ha podido encontrar ningún servidor en "
                                + Config.getConfig().getServerList());
            }

            if (Stress.arguments.length == 3) {
                PasswordValidation pv = server.validatePassword(Stress.arguments[0],
                        Stress.arguments[1], Stress.arguments[2]);
                if (pv == PasswordValidation.PASSWORD_GOOD) {
                    System.out.println("Thread " + id + ":Contraseńa válida");
                } else if (pv == PasswordValidation.PASSWORD_GOOD_EXPIRED) {
                    System.out.println("Thread " + id + ": Debe cambiar la contraseńa");
                } else {
                    System.out.println("Thread " + id + ": Contraseńa no válida");
                }
            } else {
                System.out.println("Thread " + id
                        + ": Número erróneo de parámetros: usuario / contraseńa");
            }
        } catch (Exception e) {
            System.out.println("Thread " + id + " " + e.toString());
        }
    }

}

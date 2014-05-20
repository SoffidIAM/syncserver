package es.caib.seycon.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.sync.ServerServiceLocator;

public class Syslogger {
    static InetAddress host;
    static Logger log = Log.getLogger("SysLogger");
    
    public static void setHost (String hostName) throws UnknownHostException {
        host = InetAddress.getByName(hostName);
    }
    
    public static void send(String type, String msg) {
        try {
            if (host != null) {
                DatagramSocket s = new DatagramSocket();
                s.connect(host, 514);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintStream p = new PrintStream(out, true, "UTF-8");
                p.print("<134>"); // PRI
                p.print("1"); // VERSION
                p.print(" "); // SP
                Calendar d = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
                p.printf("%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
                        d.get(Calendar.YEAR),
                        d.get(Calendar.MONTH)+1,
                        d.get(Calendar.DAY_OF_MONTH),
                        d.get(Calendar.HOUR_OF_DAY),
                        d.get(Calendar.MINUTE),
                        d.get(Calendar.SECOND),
                        d.get(Calendar.MILLISECOND));
                p.print(" "); // SP
                p.print(Config.getConfig().getHostName()); // HOSTNAME
                p.print(" "); // SP
                p.print("seycon"); // APPNAME
                p.print(" "); // SP
                p.print("-"); // PROCID
                p.print(" "); // SP
                p.print(type); // MSGID
                p.print(" - "); // STRUCTURED DATA
                p.print(msg);// MSG     
                p.flush ();
                
                byte buf [] = out.toByteArray();
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                s.send (packet);
            }
        } catch (Exception e) {
            log.warn("Error sending message", e);
        }
    }

    public static void configure() {
        try {
            String server = ServerServiceLocator.instance().getServerService().getConfig("syslog.server");
            if (server != null) {
                log.info("Enabling syslogserver {}", server, null);
                setHost(server);
            } else {
                log.warn("No syslogserver configured (syslog.server)", null, null);
            }
        } catch (Exception e) {
            log.warn("Error configuring", e);
        }
    }

}

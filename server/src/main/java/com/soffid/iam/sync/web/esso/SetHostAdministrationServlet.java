package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Host;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.utils.ConfigurationCache;

import es.caib.seycon.ng.exception.InternalErrorException;

public class SetHostAdministrationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("SetHostAdministrationServlet");
    ConnectionPool pool = ConnectionPool.getPool();
	private ServerService serverService;
	private NetworkService xarxaService;

    public SetHostAdministrationServlet() {
        serverService = ServerServiceLocator.instance().getServerService();
        xarxaService = ServerServiceLocator.instance().getNetworkService();
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {

        String hostIP = com.soffid.iam.utils.Security.getClientIp();

        String hostName = req.getParameter("host");
        String adminUser = req.getParameter("user");
        String adminPass = req.getParameter("pass");
        String serial = req.getParameter("serial");

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        try {
            log.info(
                    "SetHostAdministrationServlet: INICI d'establiment d'usuari-contrasenya d'administrador al host {} amb l'usuari administrador {} des d'ip "
                            + hostIP, hostName, adminUser);

            // Verifiquem paràmeters
            if (hostName == null || (hostName != null && "".equals(hostName.trim()))
                    || adminUser == null || (adminUser != null && "".equals(adminUser.trim()))
                    || adminPass == null || (adminPass != null && "".equals(adminPass.trim())))
                throw new Exception("Incorrect parameters");

            setHostAdministration(serial, hostName, hostIP, adminUser, adminPass);
            writer.write("OK|" + hostName);
            log.info(
                    "SetHostAdministrationServlet: FI correcte d'establiment d'usuari-contrasenya d'administrador al host {} per l'usuari {} des d'ip "
                            + hostIP, hostName, adminUser);
        } catch (Exception e) {
            log.warn(
                    "SetHostAdministrationServlet: ERROR performing setHostAdministration al host {} amb l'usuari administrador {} des d'ip "
                            + hostIP, hostName, adminUser);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        }
        writer.close();

    }

    HashMap<String,Date> lastChange = new HashMap<String,Date>();
    public void setHostAdministration(String serial, String hostname, String hostIP,
            String adminUser, String adminPass) throws InternalErrorException, SQLException {
        Host maq = xarxaService.findHostBySerialNumber(serial);
        boolean trackIp = "true".equals( ConfigurationCache.getProperty("SSOTrackHostAddress"));
        if (maq != null) {
            if (trackIp && !hostIP.equals(maq.getIp())) {
                InternalErrorException ex = new InternalErrorException("IncorrectHostException");
                log.warn("Intent d'establir usuari-contrasenya d'administrador al host " + hostname
                        + " amb una ip que no correspon al host " + hostIP, ex);
                throw ex;
            }
            if (trackIp && !hostname.equals(maq.getName())) {
                InternalErrorException ex = new InternalErrorException("IncorrectHostException");
                log.warn(
                        "Intent d'establir usuari-contrasenya d'administrador al host "
                                + maq.getName() + " amb un nom que no correspon al host " + hostname,
                        ex);
                throw ex;
            }
            synchronized (lastChange) {
	            Date last = lastChange.get(hostname);
	            if (last != null && System.currentTimeMillis() - last.getTime() < 3600000) // A change each hour
	            {
	            	log.warn("Password change storm from {}", hostname, null);
	                throw new InternalErrorException("IncorrectHostException");
	            }
	            lastChange.put(hostname, new Date());
            }
            // Si la comprovació de ip-nomhost ha anat bé, fem
            // l'actualització del usuari-passwd
            xarxaService.setAdministratorPassword(maq.getName(), adminUser, adminPass);

        } else {
            throw new InternalErrorException("No existeix cap màquina amb el nom " + hostname);
        }

    }

}

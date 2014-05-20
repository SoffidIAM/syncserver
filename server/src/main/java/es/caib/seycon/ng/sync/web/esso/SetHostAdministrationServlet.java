package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.servei.ServerService;

public class SetHostAdministrationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("SetHostAdministrationServlet");
    ConnectionPool pool = ConnectionPool.getPool();
    private ServerService serverService;
    private XarxaService xarxaService;

    public SetHostAdministrationServlet() {
        serverService = ServerServiceLocator.instance().getServerService();
        xarxaService = ServerServiceLocator.instance().getXarxaService();
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {

        String hostIP = req.getRemoteAddr();

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

    public void setHostAdministration(String serial, String hostname, String hostIP,
            String adminUser, String adminPass) throws InternalErrorException, SQLException {
        Maquina maq = xarxaService.findMaquinaBySerialNumber(serial);
        if (maq != null) {
            if (!hostIP.equals(maq.getAdreca())) {
                InternalErrorException ex = new InternalErrorException("IncorrectHostException");
                log.warn("Intent d'establir usuari-contrasenya d'administrador al host " + hostname
                        + " amb una ip que no correspon al host " + hostIP, ex);
                throw ex;
            }
            if (!hostname.equals(maq.getNom())) {
                InternalErrorException ex = new InternalErrorException("IncorrectHostException");
                log.warn(
                        "Intent d'establir usuari-contrasenya d'administrador al host "
                                + maq.getNom() + " amb un nom que no correspon al host " + hostname,
                        ex);
                throw ex;
            }
            // Si la comprovació de ip-nomhost ha anat bé, fem
            // l'actualització del usuari-passwd
            xarxaService.setContrasenyaAdministrador(maq.getNom(), adminUser, adminPass);

        } else {
            throw new InternalErrorException("No existeix cap màquina amb el nom " + hostname);
        }

    }

}

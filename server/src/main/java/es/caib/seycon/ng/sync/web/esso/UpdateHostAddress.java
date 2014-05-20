package es.caib.seycon.ng.sync.web.esso;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.caib.seycon.ng.exception.UnknownNetworkException;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.sync.ServerServiceLocator;


public class UpdateHostAddress extends HttpServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UpdateHostAddress.class);

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");
        try {
            String name = request.getParameter("name");
            String serial = request.getParameter("serial");
            String ip = request.getRemoteAddr();
            try {
                String nameIp = InetAddress.getByName(name).getHostAddress();
                if (! ip.equals(nameIp)) {
                    log.warn (String.format("Trying to register host %s(%s) from address %s",
                            name, nameIp, ip));
//                    response.getOutputStream().println("ERROR");
//                    return ;
                }
            } catch (UnknownHostException e) {
            
            }
            XarxaService xs = ServerServiceLocator.instance().getXarxaService();
            xs.registerDynamicIP(name, request.getRemoteAddr(), serial);
            response.getOutputStream().println("OK");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getOutputStream().println("ERROR|"+e.toString());
            if (! (e instanceof UnknownNetworkException))
            	log.warn("Error invoking " + request.getRequestURI(), e);
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(req, response);
    }
}

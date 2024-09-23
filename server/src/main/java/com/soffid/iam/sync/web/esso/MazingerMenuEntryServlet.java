package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.CharBuffer;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.api.AccessTree;
import com.soffid.iam.api.AccessTreeExecution;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.Network;
import com.soffid.iam.api.Session;
import com.soffid.iam.api.User;
import com.soffid.iam.service.AuthorizationService;
import com.soffid.iam.service.EntryPointService;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.service.SessionService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;

public class MazingerMenuEntryServlet extends HttpServlet {


	private SessionService sessioService;
	private NetworkService xarxaService;
	private AuthorizationService autoritzacioService;
	private UserService usuariService;
	private EntryPointService puntEntradaService;

	public MazingerMenuEntryServlet() {
        puntEntradaService = ServerServiceLocator.instance().getEntryPointService();
        sessioService = ServerServiceLocator.instance().getSessionService();
        xarxaService = ServerServiceLocator.instance().getNetworkService();
        autoritzacioService = ServerServiceLocator.instance().getAuthorizationService();
        usuariService = ServerServiceLocator.instance().getUserService();
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    ConnectionPool pool = ConnectionPool.getPool();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        String user = req.getParameter("user");
        String id = req.getParameter("id");
        String codi = req.getParameter("codi");
        String key = req.getParameter("key");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8"));
        
        try {
	        if (user == null) {
        		Security.nestedLogin("-", new String [] { 
        				Security.AUTO_HOST_ALL_QUERY+Security.AUTO_ALL
        		});
        		
        		try {
        			getEntryPoint(id, codi, writer);
        		} catch (Exception e) {
        			log("Error getting menu id:" + id + " codi:" + codi, e);
        			writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        		} finally {
        			Security.nestedLogoff();
        		}	        	
	        } else {
        	
        		Security.nestedLogin(user, new String [] { 
        				Security.AUTO_HOST_ALL_QUERY+Security.AUTO_ALL
        		});
        		
        		try {
        			Session sessio = null;
        			User usuari = usuariService.findUserByUserName(user);
        			for (Session s: sessioService.getActiveSessions(usuari.getId())) {
        				if (key.equals (s.getKey()))
        				{
        					sessio = s;
        					break;
        				}
        			}
        			if (sessio == null) {
        				throw new InternalErrorException("Invalid session key");
        			}
        			Host maq = xarxaService.findHostByName(sessio.getServerHostName());
        	        boolean trackIp = "true".equals( ConfigurationCache.getProperty("SSOTrackHostAddress"));
        			if (trackIp && (maq == null || !maq.getIp().equals(com.soffid.iam.utils.Security.getClientIp()))) {
        				throw new InternalErrorException("Invalid session key");
        			}
        			getEntryPoint(id, codi, writer);
        		} catch (Exception e) {
        			log("Error getting menu id:" + id + " codi:" + codi, e);
        			writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        		} finally {
        			Security.nestedLogoff();
        		}
	        }
        } catch (Exception e1) {
        	throw new ServletException(e1);
        }
        writer.close();
    }

	public void getEntryPoint(String id, String codi, BufferedWriter writer)
			throws InternalErrorException, IOException, SQLException {
		AccessTree pue = null;
		if (id != null)
			pue  = puntEntradaService.findApplicationAccessById(Long.decode(id).longValue());
		
		else if (codi != null) {
			Collection<AccessTree> punts = puntEntradaService.findApplicationAccessByFilter("%", codi,
					"%", "%", "%", "%");
			if (punts.size() == 1)
				pue = punts.iterator().next();
		}
		if (pue == null)
			writer.write ("ERROR|Unknown application entry point");
		else if (!puntEntradaService.canExecute(pue))
			throw new InternalErrorException("Not authorized to execute application");
		else
		{
			String result = generatePuntEntrada(pue, com.soffid.iam.utils.Security.getClientIp());
			writer.write("OK|");
			writer.write(result);
		}
	}

    public String generatePuntEntrada(AccessTree pue, String ip) throws InternalErrorException,
            SQLException {

        String ambit = "I";
        Host maq = xarxaService.findHostByIp(ip);
        if (maq != null) {
            Network xarxa = xarxaService.findNetworkByName(maq.getNetworkCode());
            if (xarxa.getLanAccess().booleanValue())
                ambit = "L";
            else
                ambit = "W";
        }

        for (AccessTreeExecution execucio : puntEntradaService.getExecutions(pue)) {
            if (execucio.getScope().equals(ambit)) {
                return execucio.getExecutionTypeCode() + "|" + execucio.getContent();
            }
        }
        throw new InternalErrorException("Unable to execute");

    }

}

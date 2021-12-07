package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Audit;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.service.AuditService;
import com.soffid.iam.service.AuthorizationService;
import com.soffid.iam.service.NetworkService;
import com.soffid.iam.service.NetworkServiceImpl;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.db.ConnectionPool;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.web.Messages;
import com.soffid.iam.utils.ConfigurationCache;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;

public class GetHostAdministrationServlet extends HttpServlet
{

    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetHostAdministrationServlet"); //$NON-NLS-1$
    ConnectionPool pool = ConnectionPool.getPool();

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
		throws ServletException, IOException
	{
        String hostIP = com.soffid.iam.utils.Security.getClientIp();
        String hostName = req.getParameter("host"); //$NON-NLS-1$
        String usuariPeticio = req.getParameter("user"); //$NON-NLS-1$
        String passPeticio = req.getParameter("pass"); //$NON-NLS-1$

        LogonService logonService = ServerServiceLocator.instance().getLogonService();
        PasswordValidation validPassword = PasswordValidation.PASSWORD_WRONG;

        try
        {
        	validPassword = logonService.validatePassword(usuariPeticio, null, passPeticio);
        }
        catch (Throwable th)
        {
            validPassword = PasswordValidation.PASSWORD_WRONG;
        }

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(),
                "UTF-8")); //$NON-NLS-1$

        if (validPassword == PasswordValidation.PASSWORD_GOOD)
        {
            try
            {
                log.info(String.format(
					"GetHostAdministrationServlet: Starting to obtain admin user-password from host '{}', user request '{}' from IP '%1$s'", 
						hostIP), hostName, usuariPeticio);

                // Verifiquem paràmeters
                if (hostName == null || (hostName != null && "".equals(hostName.trim())) //$NON-NLS-1$
                        || usuariPeticio == null
                        || (usuariPeticio != null && "".equals(usuariPeticio.trim()))) //$NON-NLS-1$
                    throw new Exception(Messages.getString("GetHostAdministrationServlet.IncorrectParamsMessage")); //$NON-NLS-1$

                String resultat = getHostAdministration(hostName, hostIP, usuariPeticio);
                writer.write("OK|" + resultat); //$NON-NLS-1$
                log.info(String.format(
    				"Admin user-password retrieved from host '{}', user request '{}' IP '%1$s'", 
    				hostIP), hostName, usuariPeticio);
            }
            catch (Exception e)
            {
                log.warn(String.format(
    				"GetHostAdministrationServlet: ERROR performing getHostAdministration at '{}', user request '{}' from IP '%1$s'", 
    				hostIP), hostName, usuariPeticio);
                log.warn("GetHostAdministrationServlet: Exception: ", e); 
                writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        else
        {
            log.warn(String.format(
				"GetHostAdministrationServlet: ERROR performing getHostAdministration at '{}', user request '{}' from IP '%1$s'", 
				hostIP), hostName, usuariPeticio);

            InternalErrorException uex = new InternalErrorException(Messages.getString("GetHostAdministrationServlet.IncorrectPasswordMessage")); //$NON-NLS-1$
            log.warn("GetHostAdministrationServlet: Exception: ", uex); 
            writer.write(uex.getClass().getName() + "|" + uex.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        writer.close();
    }

    public String getHostAdministration(String hostname, String hostIP,
		String usuariPeticio) throws InternalErrorException, IOException,
		UnknownHostException, SystemException, RollbackException,
		HeuristicMixedException, HeuristicRollbackException,
		NotSupportedException
	{    
        NetworkService xs = ServerServiceLocator.instance().getNetworkService();
        AuthorizationService as = ServerServiceLocator.instance().getAuthorizationService();
        String[] auths = as.getUserAuthorizationsString(usuariPeticio);
        
        boolean trackIp = "true".equals( ConfigurationCache.getProperty("SSOTrackHostAddress"));
        
        Host maq = xs.findHostByName(hostname);
        if (maq == null)
            throw new InternalErrorException(String.format(
				Messages.getString("GetHostAdministrationServlet.NoHostFoundMessage"), hostname)); //$NON-NLS-1$
        else if (maq.getIp() == null)
        {
            InternalErrorException ex = new InternalErrorException("IncorrectHostException"); //$NON-NLS-1$
            log.warn(String.format("Attempt to obtain admin user-password for host '%1$s' from mismatch IP '%2$s'", 
				hostname, hostIP), ex);
            throw ex;
        }
        else if (trackIp && !maq.getIp().equals(hostIP))
        {
            InternalErrorException ex = new InternalErrorException("IncorrectHostException"); //$NON-NLS-1$
            log.warn(String.format("Attempt to obtain admin user-password for host '%1$s' from mismatch IP '%2$s'", 
				hostname, hostIP), ex);
            throw ex;
        }

        Security.nestedLogin(usuariPeticio, auths);
        try
        {
            boolean authorized = false;
            for (String auth: auths)
            {
                if (auth.equals(Security.AUTO_HOST_ALL_SUPPORT_VNC))
                {
                    authorized = true;
                    break;
                }
            }
            if (!authorized)
            {
                Long nivell = xs.findAccessLevelByHostNameAndNetworkName(maq.getName(), maq.getNetworkCode());
                if (nivell.longValue() >= NetworkServiceImpl.SUPORT)
                    authorized = true;
            }
            
            if ( authorized )
            {
                String userPass[] = xs.getHostAdminUserAndPassword(hostname);
                if (userPass[0] == null || userPass[1] == null)
                    throw new InternalErrorException(Messages.getString("GetHostAdministrationServlet.NoAdminAccountMessage")); //$NON-NLS-1$
                return userPass[0] + "|" + userPass[1]; //$NON-NLS-1$
            }
            else
            {
                Audit auditoria = new Audit();
                auditoria.setAction("N"); // Administrador //$NON-NLS-1$
                auditoria.setHost(hostname);
                auditoria.setAuthor(usuariPeticio);
                auditoria.setObject("SC_ADMMAQ"); //$NON-NLS-1$
                auditoria.setCalendar(Calendar.getInstance());
    
                AuditService auditoriaService = ServerServiceLocator.instance().getAuditService();
                auditoriaService.create(auditoria);
                throw new InternalErrorException(Messages.getString("GetHostAdministrationServlet.UnauthorizedUser")); //$NON-NLS-1$
            }
        }
        finally
        {
            Security.nestedLogoff();
        }
    }
}

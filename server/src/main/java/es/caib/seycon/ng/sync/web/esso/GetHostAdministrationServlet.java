package es.caib.seycon.ng.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
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

import es.caib.seycon.ng.comu.Auditoria;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.servei.AuditoriaService;
import es.caib.seycon.ng.servei.AutoritzacioService;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.servei.XarxaServiceImpl;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.servei.LogonService;
import es.caib.seycon.ng.sync.web.Messages;
import es.caib.seycon.ng.utils.Security;

public class GetHostAdministrationServlet extends HttpServlet
{

    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("GetHostAdministrationServlet"); //$NON-NLS-1$
    ConnectionPool pool = ConnectionPool.getPool();

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
		throws ServletException, IOException
	{
        String hostIP = req.getRemoteAddr();
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
        XarxaService xs = ServerServiceLocator.instance().getXarxaService();
        AutoritzacioService as = ServerServiceLocator.instance().getAutoritzacioService();
        String[] auths = as.getUserAuthorizationsString(usuariPeticio);
        
        Maquina maq = xs.findMaquinaByNom(hostname);
        if (maq == null)
            throw new InternalErrorException(String.format(
				Messages.getString("GetHostAdministrationServlet.NoHostFoundMessage"), hostname)); //$NON-NLS-1$
        else if (maq.getAdreca() == null)
        {
            InternalErrorException ex = new InternalErrorException("IncorrectHostException"); //$NON-NLS-1$
            log.warn(String.format("Attempt to obtain admin user-password for host '%1$s' from mismatch IP '%2$s'", 
				hostname, hostIP), ex);
            throw ex;
        }
        else if (!maq.getAdreca().equals(hostIP))
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
                Long nivell = xs.findNivellAccesByNomMaquinaAndCodiXarxa(maq.getNom(), maq.getCodiXarxa());
                if (nivell.longValue() >= XarxaServiceImpl.SUPORT)
                    authorized = true;
            }
            
            if ( authorized )
            {
                String userPass[] = xs.getUsuariAndContrasenyaAdministradorHost(hostname);
                if (userPass[0] == null || userPass[1] == null)
                    throw new InternalErrorException(Messages.getString("GetHostAdministrationServlet.NoAdminAccountMessage")); //$NON-NLS-1$
                return userPass[0] + "|" + userPass[1]; //$NON-NLS-1$
            }
            else
            {
                Auditoria auditoria = new Auditoria();
                auditoria.setAccio("N"); // Administrador //$NON-NLS-1$
                auditoria.setMaquina(hostname);
                auditoria.setAutor(usuariPeticio);
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "dd/MM/yyyy kk:mm:ss"); //$NON-NLS-1$
                auditoria.setData(dateFormat.format(GregorianCalendar.getInstance()
                        .getTime()));
                auditoria.setObjecte("SC_ADMMAQ"); //$NON-NLS-1$
    
                AuditoriaService auditoriaService = ServerServiceLocator.instance().getAuditoriaService();
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

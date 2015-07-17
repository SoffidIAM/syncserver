package es.caib.seycon.ng.sync.web.esso;

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

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.comu.ExecucioPuntEntrada;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.PuntEntrada;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.Xarxa;
import es.caib.seycon.ng.servei.AutoritzacioService;
import es.caib.seycon.ng.servei.PuntEntradaService;
import es.caib.seycon.ng.servei.SessioService;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.session.SessionManager;
import es.caib.seycon.ng.utils.Security;

public class MazingerMenuEntryServlet extends HttpServlet {

    private PuntEntradaService puntEntradaService;
    private SessioService sessioService;
    private XarxaService xarxaService;
    private AutoritzacioService autoritzacioService;
    private UsuariService usuariService;

    public MazingerMenuEntryServlet() {
        puntEntradaService = ServerServiceLocator.instance().getPuntEntradaService();
        sessioService = ServerServiceLocator.instance().getSessioService();
        xarxaService = ServerServiceLocator.instance().getXarxaService();
        autoritzacioService = ServerServiceLocator.instance().getAutoritzacioService();
        usuariService = ServerServiceLocator.instance().getUsuariService();
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
        String[] auths = null;
        try {
            Security.nestedLogin(user, new String [] { 
                Security.AUTO_HOST_ALL_QUERY+Security.AUTO_ALL
            });

            try {
                Sessio sessio = null;
                Usuari usuari = usuariService.findUsuariByCodiUsuari(user);
                for (Sessio s: sessioService.getActiveSessions(usuari.getId())) {
                    if (key.equals (s.getClau()))
                    {
                        sessio = s;
                        break;
                    }
                }
                if (sessio == null) {
                    throw new InternalErrorException("Invalid session key");
                }
                Maquina maq = xarxaService.findMaquinaByNom(sessio.getNomMaquinaServidora());
                if (maq == null || !maq.getAdreca().equals(req.getRemoteAddr())) {
                    throw new InternalErrorException("Invalid session key");
                }
                PuntEntrada pue = null;
                if (id != null)
                    pue = puntEntradaService.findPuntEntradaById(Long.decode(id).longValue());

                else if (codi != null) {
                    Collection<PuntEntrada> punts = puntEntradaService.findPuntsEntrada("%", codi,
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
	                String result = generatePuntEntrada(pue, req.getRemoteAddr());
	                writer.write("OK|");
	                writer.write(result);
                }
            } catch (Exception e) {
                log("Error getting menu id:" + id + " codi:" + codi, e);
                writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
            } finally {
                Security.nestedLogoff();
            }
        } catch (Exception e1) {
            throw new ServletException(e1);
        }
        writer.close();
    }

    public String generatePuntEntrada(PuntEntrada pue, String ip) throws InternalErrorException,
            SQLException {

        String ambit = "I";
        Maquina maq = xarxaService.findMaquinaByIp(ip);
        if (maq != null) {
            Xarxa xarxa = xarxaService.findXarxaByCodi(maq.getCodiXarxa());
            if (xarxa.getNormalitzada().booleanValue())
                ambit = "L";
            else
                ambit = "W";
        }

        for (ExecucioPuntEntrada execucio : puntEntradaService.getExecucions(pue)) {
            if (execucio.getAmbit().equals(ambit)) {
                return execucio.getCodiTipusExecucio() + "|" + execucio.getContingut();
            }
        }
        throw new InternalErrorException("Unable to execute");

    }

}

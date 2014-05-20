package es.caib.seycon.ng.sync.web;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.image4j.codec.ico.ICOEncoder;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.PuntEntrada;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.PuntEntradaService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.util.Base64;

public class MazingerIconsServlet extends HttpServlet
{
	Logger log = Log.getLogger("MazingerIconsServlet");
	private PuntEntradaService puntEntradaService;

	public MazingerIconsServlet ()
	{
		puntEntradaService = ServerServiceLocator.instance().getPuntEntradaService();
	}

	@Override
	protected void doGet (HttpServletRequest req, HttpServletResponse resp)
					throws ServletException, IOException
	{
		resp.setContentType("text/plain; charset=UTF-8");
		resp.setCharacterEncoding("UTF-8");
		String appID = req.getParameter("appID");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
						resp.getOutputStream(), "UTF-8"));

		try
		{
			log.info("Obtaining image icon for {}", appID, null);
			String iconImage = generateAppIcon(appID);

			writer.write("OK|");
			writer.write(iconImage);
		}
		catch (Exception e)
		{
			log("Error obtaining icon", e);
			writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
		}
		finally
		{
			writer.close();
		}
	}

	public String generateAppIcon (String appID)
					throws InternalErrorException, IOException
	{
		PuntEntrada pue = puntEntradaService.findPuntEntradaById(Long.parseLong(appID));
		BufferedImage bufferedImage = null;
		
		if (pue.getIdIcona1() != null)
		{
			InputStream in = new ByteArrayInputStream(pue.getImgIcona1());
			bufferedImage = ImageIO.read(in);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ICOEncoder.write(bufferedImage, out);

			return Base64.encodeBytes(out.toByteArray(), Base64.DONT_BREAK_LINES);
		}
		
		return "";
	}

}

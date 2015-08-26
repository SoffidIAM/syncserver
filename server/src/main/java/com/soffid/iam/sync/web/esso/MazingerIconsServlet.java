package com.soffid.iam.sync.web.esso;

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

import com.soffid.iam.api.AccessTree;
import com.soffid.iam.service.EntryPointService;
import com.soffid.iam.sync.ServerServiceLocator;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.util.Base64;

public class MazingerIconsServlet extends HttpServlet
{
	Logger log = Log.getLogger("MazingerIconsServlet");
	private EntryPointService puntEntradaService;

	public MazingerIconsServlet ()
	{
		puntEntradaService = ServerServiceLocator.instance().getEntryPointService();
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
		AccessTree pue = puntEntradaService.findApplicationAccessById(Long.parseLong(appID));
		BufferedImage bufferedImage = null;
		
		if (pue.getIcon1Id() != null)
		{
			InputStream in = new ByteArrayInputStream(pue.getIcon1Image());
			bufferedImage = ImageIO.read(in);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ICOEncoder.write(bufferedImage, out);

			return Base64.encodeBytes(out.toByteArray(), Base64.DONT_BREAK_LINES);
		}
		
		return "";
	}

}

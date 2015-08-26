package com.soffid.iam.sync.web.admin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.sync.ServerServiceLocator;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.utils.Security;

public class StressTest extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setContentType("text/plain");
		ServletOutputStream out = resp.getOutputStream();
		
		out.println("Generating");
		out.flush();
		Security.nestedLogin("anonuymous", new String[] {Security.AUTO_USER_UPDATE+Security.AUTO_ALL});
		try {
			UsuariService svc = ServiceLocator.instance().getUsuariService();
			for (int i = 0; i < 100; i++) {
				Usuari usuari = svc.findUsuariByCodiUsuari("admin");
				usuari.setComentari("Modificat on "+new Date());
				usuari.setActiu(new Boolean(true));
				svc.update(usuari);
				out.println ("Usuari "+i);
				out.flush ();
				
			}
		} catch (Exception e) {
			e.printStackTrace(new PrintStream(out));
		} finally {
			Security.nestedLogoff();
		}
		out.close ();
		
	}

}

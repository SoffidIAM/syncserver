package es.caib.seycon.ng.sync.web.admin;

import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.caib.seycon.ng.comu.Xarxa;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.servei.XarxaService;
import es.caib.seycon.ng.sync.ServerServiceLocator;

public class ErrorServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		XarxaService xs = ServerServiceLocator.instance().getXarxaService();
		Xarxa x = new Xarxa();
		x.setCodi("loopback");
		x.setAdreca("127.0.0.1");
		x.setMascara("255.255.255.255");
		x.setDescripcio("XXX");
		x.setDhcpSupport(true);
		x.setNormalitzada(new Boolean(true));
		resp.setContentType("text/plain");
		ServletOutputStream out = resp.getOutputStream();
		PrintStream ps = new PrintStream (out);
		try {
			xs.create(x);
			out.println (" OK !!!");
		} catch (Exception e) {
			e.printStackTrace(ps);
			System.out.println ("ERROR "+e.toString());
		}
		ps.close ();
		out.close();
	}

}

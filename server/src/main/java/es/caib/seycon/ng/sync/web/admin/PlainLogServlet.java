package es.caib.seycon.ng.sync.web.admin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.config.Config;

public class PlainLogServlet extends HttpServlet {
	Logger log = Log.getLogger("View Log Servlet");
	private static String LOG_PATH = null;
	private static String FILE_SEPARATOR = File.separator;

	static {
		try {
			LOG_PATH = Config.getConfig().getLogFile().getAbsolutePath();
		} catch (IOException ioe) {
			Log.getLog().warn("No s'ha pogut carregar la configuració", ioe);
		}
	}

	private static boolean isWindows() {
		return File.separator.compareTo("\\") == 0;
	}


	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		File f = new File(LOG_PATH);
		int length = 0;
		ServletOutputStream op = response.getOutputStream();
		ServletContext context = getServletConfig().getServletContext();
	        response.setContentType("text/plain; charset=UTF-8");
	        response.addHeader("content-disposition", "attachment; filename=seycon.log");

		byte[] bbuf = new byte[1024];
		InputStream in = new FileInputStream(f);
		int leido;
		while ((leido = in.read(bbuf)) > 0) {
		    op.write (bbuf, 0, leido);
		}
		in.close();
		op.close();
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse response)
			throws ServletException, IOException {
		doPost(req, response);
	}

}
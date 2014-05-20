package es.caib.seycon.ng.sync.web.admin;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.sync.engine.db.ConnectionPool;

public class DatabaseStatusServlet extends HttpServlet {
	Logger log = Log.getLogger("Tasques Pendents Servlet");

	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
	        response.setContentType("text/html; charset=UTF-8");
		try {
			try {
				PrintWriter printWriter = response.getWriter();
				printWriter.println("<p>[<a href=\"main\">main</a>] -- Database Connection status.</p>");
				ConnectionPool pool = ConnectionPool.getPool();
				printWriter.println(pool!=null && pool.getStatus()!=null?pool.getStatus().replaceAll("\n", "<br>"):"");
				printWriter.flush();
				response.flushBuffer();
			} catch (Throwable t) {
				PrintWriter printWriter = response.getWriter();
				printWriter.println("<p>DatabaseStatusServlet: Error generando página: </p>"
						+ (t.getCause()!=null?t.getCause().getMessage():t.toString()));
			}
		} catch (Exception e) {
			log.warn("Error invoking " + request.getRequestURI(), e);
		}
	}


	protected void doGet(HttpServletRequest req, HttpServletResponse response)
			throws ServletException, IOException {
		doPost(req, response);
	}

}

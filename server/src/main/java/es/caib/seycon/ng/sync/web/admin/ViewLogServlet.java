package es.caib.seycon.ng.sync.web.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.config.Config;

public class ViewLogServlet extends HttpServlet {
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

	private String HTMLhead() {
		return "<html>" + "<head>" + "<script type=\"text/javascript\">"
				+ "function pointToBottom(){"
				+ "window.location.href = \"viewLog#bottomlink\";"
				+ "}</script>"
				+ "<style type='text/css'>"
				+ "span {font-family: monospace; font-size: 8: color:black} " +
				  "span.date {color:sienna} " +
				  "span.level {color:red} " +
				  "span.facility {color:blue} " +
				  "</style>" 
				+ "</head>"
				+ "<body onLoad=\"pointToBottom()\">";
	}

	private String HTMLend() {
		return "<a name=\"bottomlink\">&nbsp;</a>" + "</body>" + "</html>";
	}

	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		File f = new File(LOG_PATH);
		int length = 0;
		ServletOutputStream op = response.getOutputStream();
		ServletContext context = getServletConfig().getServletContext();
	        response.setContentType("text/html; charset=ISO-8859-1");

		op.print(HTMLhead());

		byte[] bbuf = new byte[1024];
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(f)));
		String line = "";
		int lineNumber = 1;
		while ((line = bufferedReader.readLine()) != null) {
		    String split[] = line.split(":");
		    /* Line Format
		     * yyyy-mm-dd hh:mi:ss.sss:FACILITY:LEVEL:MESSAGE
		     */
		    if ( split.length >= 5 &&
		            split[0].length()+split[1].length()+split[2].length() > 16 &&
		            split[0].length()+split[1].length()+split[2].length() < 22)
		    {
		        int i = 0;
		        op.println ("<span><span class='date'>");
		        op.print(split[i++]); // date + hour
		        op.print(':');
                        op.print(split[i++]); // mins
                        op.print(':');
                        op.print(split[i++]); // secs.milis
                        op.print("</span>:<span class='facility'>");
                        op.print(split[i++]); // facility
                        op.print("</span>:<span class='level'>");
                        op.print(split[i++]); // facility
                        op.print("</span>");
                        while ( i < split.length)
                        {
                            op.print(':');
                            op.print(split[i++]); // Message
                        }
                        op.print ("</span><br>");
		    }
		    else
		    {
		        op.print ("<span>"+line+"</span><br>");
		    }
		}
		response.flushBuffer();
		bufferedReader.close();

		op.print(HTMLend());
		op.flush();
		op.close();
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse response)
			throws ServletException, IOException {
		doPost(req, response);
	}

}
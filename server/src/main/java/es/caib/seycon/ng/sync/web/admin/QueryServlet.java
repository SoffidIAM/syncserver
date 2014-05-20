package es.caib.seycon.ng.sync.web.admin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.ServerServiceLocator;

public class QueryServlet extends HttpServlet {

    private es.caib.seycon.ng.sync.servei.QueryService queryService;
    public QueryServlet () {
        queryService = ServerServiceLocator.instance().getQueryService();
    }
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        String format = req.getParameter("format");
        String nofail = req.getParameter("nofail");
        if (format == null)
            format = "text/xml";
        resp.setContentType(format+"; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        try {
            queryService.query(path, format, writer);
        } catch (InternalErrorException e) {
            if (e.getMessage().equals ("not found") && nofail != null) {
		log ("No data found on "+path);
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	    }
            else
            {
                log ("Error consultando path "+path, e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                if ("text/xml".equals(format))
                    writer.write("<error>"+e.toString()+"</error>");
                else
                    writer.write("ERROR: "+e.toString());
            }
        }
        writer.flush ();
    }

}

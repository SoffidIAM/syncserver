package es.caib.seycon.ng.sync.web.admin;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.output.ByteArrayOutputStream;

import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.QueryService;

public class QueryHQL extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        resp.setContentType("text/html");
        PrintWriter w = resp.getWriter();
        w.println("<HTML><BODY>");
        w.println("<p>Query HQL</p>");
        w.println("<FORM action='/queryhql' method='post'>");
        w.println("<TEXTAREA rows='15' cols='80' NAME='query'> </TEXTAREA>");
        w.println("<BUTTON TYPE='submit' TEXT='submit'>Executar</BUTTON>");
        w.println("</FORM></BODY></HTML>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        resp.setContentType("text/html");
        PrintWriter w = resp.getWriter();
        String query = req.getParameter("query");

        w.println("<HTML><BODY>");
        w.println("<p>Query HQL</p>");
        w.println("<FORM action='/queryhql' method='post'>");
        w.println("<TEXTAREA rows='15' cols='80' NAME='query'>");
        w.println(query);
        w.println("</TEXTAREA>");
        w.println("<BUTTON TYPE='submit' TEXT='submit'>Executar</BUTTON>");
        
        QueryService qs = ServerServiceLocator.instance().getQueryService();
        try {  
            @SuppressWarnings("rawtypes")
            List<List> result = qs.queryHql(query);
            w.println ("<TABLE>");
            for (List row: result) {
                w.println ("<TR>");
                for ( Object obj : row ) {
                    w.write ("<TD>");
                    w.write (canonicalizaHtml(obj.toString()));
                    w.println ("</TD>");
                }
                w.println("<tr>");
            }
            w.println("</TABLE>");
        } catch (Exception e) {
            w.println ("<P>Error</P><P>");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(out);
            e.printStackTrace(pw);
            pw.flush();
            pw.close();
            out.close();
            String s = canonicalizaHtml(out.toString());
            w.println(s);
        }
        w.println("</FORM></BODY></HTML>");
    }

    private String canonicalizaHtml(String string) {
        return string.
                replaceAll("<", "&lt;").
                replaceAll(">", "&gt;").
                replaceAll("\n", "</p><p>").
                replaceAll(" ", "&nbsp;");
    }
    

}

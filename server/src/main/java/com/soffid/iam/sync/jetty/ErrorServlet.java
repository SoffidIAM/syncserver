package com.soffid.iam.sync.jetty;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.handler.ErrorHandler;

public class ErrorServlet extends ErrorHandler {
	@Override
    protected void writeErrorPageBody(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks)
            throws IOException
    {
        if (message == null)
            message = HttpStatus.getMessage(code);

        writer.write("HTTP/"+code+" ");
        writer.write(message);
    }
}

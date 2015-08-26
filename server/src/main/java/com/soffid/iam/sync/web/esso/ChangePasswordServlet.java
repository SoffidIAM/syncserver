package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.iam.api.Password;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.service.LogonService;

import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.InvalidPasswordException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class ChangePasswordServlet extends HttpServlet {

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String user = req.getParameter("user");
        String pass [] = req.getParameterValues("password");
        Password oldPass = new Password(pass[0]);
        Password newPass = new Password(pass[1]);
        resp.setContentType("text/plain; charset=UTF-8");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(resp.getOutputStream(),"UTF-8"));
        try {
            LogonService ls = ServerServiceLocator.instance().getLogonService();
            ls.changePassword(user, null, oldPass.getPassword(), newPass.getPassword());
            writer.write("ok");
        } catch (es.caib.seycon.ng.exception.InternalErrorException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log ("Error changing password ", e);
            writer.write("ERROR: "+e.toString());
        } catch (BadPasswordException e) {
            writer.write("ERROR: "+e.toString());
        } catch (InvalidPasswordException e) {
            log ("Error changing password. Invalid password for user: "+user);
            writer.write("ERROR: "+e.toString());
        }
        writer.close ();
    }

}

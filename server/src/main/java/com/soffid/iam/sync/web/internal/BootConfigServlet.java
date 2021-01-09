package com.soffid.iam.sync.web.internal;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.config.Config;

public class BootConfigServlet extends HttpServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("Configuration servlet");

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) 
            throws ServletException, IOException 
    {
		String userName = URLDecoder.decode(request.getRemoteUser(), "UTF-8");
		int i = userName.indexOf('\\');
		String tenant;
		if ( i < 0 )
		{
			tenant = Security.getMasterTenantName();
			userName = URLDecoder.decode(userName,"UTF-8");
		}
		else
		{
			tenant = URLDecoder.decode(userName.substring(0, i), "UTF-8");
			userName = URLDecoder.decode(userName.substring(i+1), "UTF-8");
		}
		Security.nestedLogin(tenant, userName, Security.ALL_PERMISSIONS);
		try {
			Properties props = ServiceLocator.instance().getServerService().getMyConfig();
			response.setContentType("text/plain; charset=UTF-8");
			ServletOutputStream out = response.getOutputStream();
			props.store(out, "Genertated by "+Config.getConfig().getHostName());
			out.close();
		} catch (Exception e) {
			log.warn("Error generating configuration for "+tenant+"\\"+userName, e);
			throw new ServletException("Error generating configuration file");
		} finally {
			Security.nestedLogoff();
		}
    }

}

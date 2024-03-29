package com.soffid.iam.sync.jetty;

import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

import com.soffid.iam.remote.HeadersFactory;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.utils.Security;

public class SecurityHeaderFactory implements HeadersFactory {

	public void addHeaders(HttpURLConnection connection) {
		if (Security.getCurrentAccount() != null)
		{
			try {
				connection.setRequestProperty("Soffid-Tenant", Security.getCurrentTenantName());
			} catch (InternalErrorException e) {
				throw new RuntimeException(e);
			}
		}
	}

}

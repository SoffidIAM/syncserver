package com.soffid.iam.sync.tools;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class IgnoreHostNameVerifier implements HostnameVerifier {

	@Override
	public boolean verify(String hostname, SSLSession session) {
		return true;
	}

}

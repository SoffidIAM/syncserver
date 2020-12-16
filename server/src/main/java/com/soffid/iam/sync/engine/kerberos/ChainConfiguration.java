package com.soffid.iam.sync.engine.kerberos;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

public class ChainConfiguration extends Configuration {
	static List<Configuration> configurations = new LinkedList<Configuration>();
	static HashSet<String> domains = new HashSet<String>();
	
	public static void addConfiguration (String domain, Configuration config)
	{
		if ( !domains.contains(domain))
			configurations.add (config);
	}

	public static void addConfiguration (Configuration config)
	{
		configurations.add (config);
	}

	@Override
	public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
		for (Configuration c: configurations)
		{
			AppConfigurationEntry[] entries = c.getAppConfigurationEntry(name);
			if (entries != null && entries.length > 0)
				return entries;
		}
		return null;
	}

}

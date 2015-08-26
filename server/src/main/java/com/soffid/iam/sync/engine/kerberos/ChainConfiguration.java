package com.soffid.iam.sync.engine.kerberos;

import java.util.LinkedList;
import java.util.List;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

public class ChainConfiguration extends Configuration {
	static List<Configuration> configurations = new LinkedList<Configuration>();
	
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

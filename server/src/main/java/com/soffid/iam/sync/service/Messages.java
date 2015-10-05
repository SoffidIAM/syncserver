package com.soffid.iam.sync.service;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Soffid
 * 
 */
public class Messages
{
	private static final String BUNDLE_NAME = "com.soffid.iam.sync.service.messages"; //$NON-NLS-1$

	private Messages ()
	{
	}

	public static String getString (String key)
	{
		try
		{
			// return RESOURCE_BUNDLE.getString(key);
			return com.soffid.iam.lang.MessageFactory
							.getString(BUNDLE_NAME, key);
		}
		catch (MissingResourceException e)
		{
			return '!' + key + '!';
		}
	}
}

/**
 * 
 */
package es.caib.seycon.ng.sync.web;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author areina
 *
 */
public class Messages
{
	private static final String BUNDLE_NAME = "es.caib.seycon.ng.sync.web.messages"; //$NON-NLS-1$

	private Messages ()
	{
	}

	public static String getString (String key)
	{
		try
		{
			return es.caib.seycon.ng.comu.lang.MessageFactory.getString(BUNDLE_NAME, key);
		}
		catch (MissingResourceException e)
		{
			return '!' + key + '!';
		}
	}
}

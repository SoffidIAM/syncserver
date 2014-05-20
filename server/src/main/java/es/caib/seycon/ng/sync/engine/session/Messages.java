/**
 * 
 */
package es.caib.seycon.ng.sync.engine.session;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Soffid
 * 
 */
public class Messages
{
	private static final String BUNDLE_NAME = "es.caib.seycon.ng.sync.engine.session.messages"; //$NON-NLS-1$

	private Messages ()
	{
	}

	public static String getString (String key)
	{
		try
		{
			return es.caib.seycon.ng.comu.lang.MessageFactory
							.getString(BUNDLE_NAME, key);
		}
		catch (MissingResourceException e)
		{
			return '!' + key + '!';
		}
	}
}

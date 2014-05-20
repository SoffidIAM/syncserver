/*
 * Created on 22-abr-2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package bubu.test.seycon;

import java.io.InputStream;
import java.net.URL;

import es.caib.sso.client.SingleSignOnFactory;

/**
 * @author u07286adm
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class Web {

	/**
	 * 
	 */
	public Web() {
		super();
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		try {
			SingleSignOnFactory.register();
			URL url = new URL ("https://intranet.caib.es/SeyconNet/index.jsp");
			InputStream is = url.openStream();
			System.out.println ("Abierto stream");
			int b;
			while ((b=is.read()) > 0) System.out.write(b);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

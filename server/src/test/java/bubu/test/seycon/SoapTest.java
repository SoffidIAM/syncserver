/*
 * Created on 21-sep-2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package bubu.test.seycon;

import javax.xml.namespace.QName;
import javax.xml.rpc.Call;
import javax.xml.rpc.ParameterMode;

import org.apache.axis.client.Service;

//import com.sun.rsasign.s;

import es.caib.seycon.ng.sync.intf.UserInfo;

/**
 * @author u07286
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class SoapTest {

	public static void main(String[] args) {
		try {
			Service service = new Service ();
		    Call call = (Call) service.createCall();
	
//		    call.setProperty(Call.USERNAME_PROPERTY, "u07286");
//		    call.setProperty(Call.PASSWORD_PROPERTY, "XXXXXX");
		    call.setTargetEndpointAddress("http://epreinf2:8080/jboss-net/services/UsuarisService");
		    call.setOperationName(new QName("findUsuari"));
		    call.addParameter( "in0", org.apache.axis.Constants.XSD_STRING, ParameterMode.IN );
		    call.setReturnType(new QName ("http://epreinf2:8080/jboss-net/services/UsuarisService", "findUsuariResponse"), UserInfo.class);
		    UserInfo ui = (UserInfo) call.invoke(new Object[] {"u07286"});
		    System.out.println("Result : " + ui);
		} catch (Exception e ) {
			e.printStackTrace ();
		}
		
	}
}

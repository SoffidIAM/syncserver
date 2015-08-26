package bubu.test.seycon;

import java.io.IOException;

import junit.framework.TestCase;

import com.soffid.iam.remote.RemoteServiceLocator;

import es.caib.seycon.ng.exception.InternalErrorException;

public class RemoteTest extends TestCase {
	public void testRemote () throws IOException, InternalErrorException
	{
		try {
			RemoteServiceLocator rsl = new RemoteServiceLocator("https://localhost:760/");
			rsl.getAgentManager();
		} catch (IOException e) {
			
		}
	}
}

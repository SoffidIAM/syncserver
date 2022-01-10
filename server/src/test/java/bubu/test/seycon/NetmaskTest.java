package bubu.test.seycon;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import com.soffid.iam.sync.jetty.NetmaskMatcher;

import junit.framework.TestCase;

public class NetmaskTest extends TestCase{

	public void testNetmask() throws UnknownHostException {
		NetmaskMatcher m = new NetmaskMatcher("10.120.20.128/25, 129.128.120.14");
		
		assertFalse(m.match(new InetSocketAddress("10.120.20.1", 0)));
		assertTrue (m.match(new InetSocketAddress("10.120.20.129", 0)));
		assertFalse(m.match(new InetSocketAddress("129.127.120.14", 0)));
		assertFalse(m.match(new InetSocketAddress("129.128.120.15", 0)));
		assertTrue (m.match(new InetSocketAddress("129.128.120.14", 0)));
	}
}

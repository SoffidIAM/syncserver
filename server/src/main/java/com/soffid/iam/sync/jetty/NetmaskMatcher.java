package com.soffid.iam.sync.jetty;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.LinkedList;

public class NetmaskMatcher {
	Collection<NetmaskMatch> matches = null;
	public NetmaskMatcher(String s) throws UnknownHostException {
		if (s == null)
			matches = null;
		else {
			matches = new LinkedList<>();
			for (String part: s.split("\\s*,\\s*")) {
				int slash = part.indexOf("/");
				if (slash < 0) {
					byte[] addr = InetAddress.getByName(part).getAddress();
					matches.add(new NetmaskMatch(addr, addr.length * 8));
				} else {
					byte[] addr = InetAddress.getByName(part.substring(0, slash)).getAddress();
					int bits = Integer.parseInt(part.substring(slash+1));
					matches.add(new NetmaskMatch(addr, bits));
				}
			}
		}
	}
	
	public boolean match(SocketAddress socketAddress) {
		if (matches == null)
			return true;
		if (socketAddress instanceof InetSocketAddress) {
			byte[] addrBytes = ((InetSocketAddress)socketAddress).getAddress().getAddress();
			for (NetmaskMatch m: matches) {
				if (m.match(addrBytes))
					return true;
			}
			return false;
		}
		else
			return true;	
	}
	
	static class NetmaskMatch {
		public NetmaskMatch(byte[] address, int bits) {
			super();
			this.address = address;
			this.bits = bits;
		}
		static byte pattern[] = new byte [] { 0, (byte) 0x80, (byte) 0xc0, (byte) 0xe0, (byte) 0xf0, (byte) 0xf8, (byte) 0xfc, (byte) 0xfe, (byte) 0xff} ;
		byte address[];
		int bits;
		public boolean match (byte[] addr2) {
			if (addr2.length  != address.length)
				return false;
			for (int pos = 0, i = 0; pos < addr2.length && i < bits; i += 8, pos ++) {
				if ( bits - i >= 8 ) {
					if (addr2[pos] != address[pos])
						return false;
				}
				else
				{
					byte p = pattern [bits-i];
					if ( (addr2[pos] & p) != (address[pos] & p))
						return false;
				}
			}
			return true;
		}
	}
}

package org.mortbay.log;

import com.soffid.iam.sync.jetty.SeyconLog;

public class Log {

	public static Logger getLogger(String string) {
		return new SeyconLog( string );
	}

	public static Logger getLogger(Class cl) {
		return new SeyconLog( cl.getName());
	}

	public static Logger getLog() {
		return new SeyconLog();
	}

}

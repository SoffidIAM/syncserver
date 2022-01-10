package org.mortbay.log;

import com.soffid.iam.sync.jetty.SeyconLog;

public interface Logger {
	public boolean isDebugEnabled() ;
	public void setDebugEnabled(boolean enabled) ;
	public void info(String msg, Object arg0, Object arg1) ;
	public void debug(String msg, Throwable th) ;
	public void debug(String msg, Object arg0, Object arg1) ;
	public void warn(String msg, Object arg0, Object arg1) ;
	public void warn(String msg, Throwable th) ;
}

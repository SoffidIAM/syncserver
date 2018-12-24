package com.soffid.iam.sync.agent;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import net.sf.jasperreports.util.StringBufferWriter;

public class CaptureLogger implements Logger {
	StringBuffer buffer;
	private PrintWriter writer;
	private DateFormat dateFormat;
	public CaptureLogger()
	{
		dateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.MEDIUM);
		buffer = new StringBuffer();
		writer = new PrintWriter( new StringBufferWriter(buffer));
	}
	
	public String toString()
	{
		return buffer.toString();
	}

	private void dump(String level, String msg, Throwable th) {
		writer.print( dateFormat.format(new Date()) );
		writer.print (" ");
		writer.print (level);
		writer.print (" ");
		writer.println (msg);
		if (th != null)
			th.printStackTrace(writer);
	}
	
	public String getName() {
		return "CaptureLogger";
	}

	public boolean isTraceEnabled() {
		return true;
	}

	public void trace(String msg) {
		dump ("TRACE", msg, null);
	}

	public void trace(String format, Object arg) {
		FormattingTuple ft = MessageFormatter.format(format, arg);
		dump ("TRACE", ft.getMessage(), ft.getThrowable());
	}

	public void trace(String format, Object arg1, Object arg2) {
		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		dump ("TRACE", ft.getMessage(), ft.getThrowable());
	}

	public void trace(String format, Object[] argArray) {
		FormattingTuple ft = MessageFormatter.format(format, argArray);
		dump ("TRACE", ft.getMessage(), ft.getThrowable());
	}

	public void trace(String msg, Throwable t) {
		dump ("TRACE", msg, t);
	}

	public boolean isTraceEnabled(Marker marker) {
		return true;
	}

	public void trace(Marker marker, String msg) {
		trace(msg);
	}

	public void trace(Marker marker, String format, Object arg) {
		trace (format, arg);
	}

	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		trace (format, arg1, arg2);
	}

	public void trace(Marker marker, String format, Object[] argArray) {
		trace (format, argArray);
	}

	public void trace(Marker marker, String msg, Throwable t) {
		trace (msg, t);
	}

	public boolean isDebugEnabled() {
		return true;
	}

	public boolean isDebugEnabled(Marker marker) {
		return true;
	}
	
	public void debug(String msg) {
		dump ("DEBUG", msg, null);
	}

	
	public void debug(String format, Object arg) {
		FormattingTuple ft = MessageFormatter.format(format, arg);
		dump ("DEBUG", ft.getMessage(), ft.getThrowable());
	}

	public void debug(String format, Object arg1, Object arg2) {
		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		dump ("DEBUG", ft.getMessage(), ft.getThrowable());
	}

	public void debug(String format, Object[] argArray) {
		FormattingTuple ft = MessageFormatter.format(format, argArray);
		dump ("DEBUG", ft.getMessage(), ft.getThrowable());
	}

	public void debug(String msg, Throwable t) {
		dump ("DEBUG", msg, t);
	}

	public void debug(Marker marker, String msg) {
		debug(msg);
	}

	public void debug(Marker marker, String format, Object arg) {
		debug (format, arg);
	}

	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		debug (format, arg1, arg2);
	}

	public void debug(Marker marker, String format, Object[] argArray) {
		debug (format, argArray);
	}

	public void debug(Marker marker, String msg, Throwable t) {
		debug (msg, t);
	}

	public boolean isInfoEnabled() {
		return true;
	}


	public void info(String msg) {
		dump ("INFO", msg, null);
	}

	public void info(String format, Object arg) {
		FormattingTuple ft = MessageFormatter.format(format, arg);
		dump ("INFO", ft.getMessage(), ft.getThrowable());
	}

	public void info(String format, Object arg1, Object arg2) {
		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		dump ("INFO", ft.getMessage(), ft.getThrowable());
	}

	public void info(String format, Object[] argArray) {
		FormattingTuple ft = MessageFormatter.format(format, argArray);
		dump ("INFO", ft.getMessage(), ft.getThrowable());
	}

	public void info(String msg, Throwable t) {
		dump ("INFO", msg, t);
	}

	public boolean isInfoEnabled(Marker marker) {
		return true;
	}

	public void info(Marker marker, String msg) {
		info(msg);
	}

	public void info(Marker marker, String format, Object arg) {
		info (format, arg);
	}

	public void info(Marker marker, String format, Object arg1, Object arg2) {
		info (format, arg1, arg2);
	}

	public void info(Marker marker, String format, Object[] argArray) {
		info (format, argArray);
	}

	public void info(Marker marker, String msg, Throwable t) {
		info (msg, t);
	}
	
	public boolean isWarnEnabled() {
		return true;
	}
	
	public void warn(String msg) {
		dump ("WARN", msg, null);
	}


	public void warn(String format, Object arg) {
		FormattingTuple ft = MessageFormatter.format(format, arg);
		dump ("WARN", ft.getMessage(), ft.getThrowable());
	}
	
	public void warn(String format, Object arg1, Object arg2) {
		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		dump ("WARN", ft.getMessage(), ft.getThrowable());
	}
	
	public void warn(String format, Object[] argArray) {
		FormattingTuple ft = MessageFormatter.format(format, argArray);
		dump ("WARN", ft.getMessage(), ft.getThrowable());
	}
	
	public void warn(String msg, Throwable t) {
		dump ("WARN", msg, t);
	}
	
	public boolean isWarnEnabled(Marker marker) {
		return true;
	}
	
	public void warn(Marker marker, String msg) {
		warn(msg);
	}
	
	public void warn(Marker marker, String format, Object arg) {
		warn (format, arg);
	}
	
	public void warn(Marker marker, String format, Object arg1, Object arg2) {
		warn (format, arg1, arg2);
	}
	
	public void warn(Marker marker, String format, Object[] argArray) {
		warn (format, argArray);
	}
	
	public void warn(Marker marker, String msg, Throwable t) {
		warn (msg, t);
	}

	public boolean isErrorEnabled() {
		return true;
	}
	
	public void error(String msg) {
		dump ("ERROR", msg, null);
	}


	public void error(String format, Object arg) {
		FormattingTuple ft = MessageFormatter.format(format, arg);
		dump ("INFO", ft.getMessage(), ft.getThrowable());
	}
	
	public void error(String format, Object arg1, Object arg2) {
		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		dump ("INFO", ft.getMessage(), ft.getThrowable());
	}
	
	public void error(String format, Object[] argArray) {
		FormattingTuple ft = MessageFormatter.format(format, argArray);
		dump ("INFO", ft.getMessage(), ft.getThrowable());
	}
	
	public void error(String msg, Throwable t) {
		dump ("INFO", msg, t);
	}
	
	public boolean isErrorEnabled(Marker marker) {
		return true;
	}
	
	public void error(Marker marker, String msg) {
		error(msg);
	}
	
	public void error(Marker marker, String format, Object arg) {
		error (format, arg);
	}
	
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		error (format, arg1, arg2);
	}
	
	public void error(Marker marker, String format, Object[] argArray) {
		error (format, argArray);
	}
	
	public void error(Marker marker, String msg, Throwable t) {
		error (msg, t);
	}

}


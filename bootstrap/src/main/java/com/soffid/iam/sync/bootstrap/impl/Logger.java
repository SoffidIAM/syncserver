package com.soffid.iam.sync.bootstrap.impl;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.soffid.iam.sync.bootstrap.SyncLoader;

public class Logger {
	DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
	String cl  = null;
	public Logger(String class1) {
		cl = class1;
	}
	public void warn(String string, Throwable e) {
		try {
			SyncLoader.getLogStream().println(df.format(new Date())+" WARN ["+cl+"] "+string+": ");
			if (e == null)
				SyncLoader.getLogStream().println();
			else
				e.printStackTrace();
		} catch (IOException e1) {
		}
	}
	public void info(String string) {
		try {
			SyncLoader.getLogStream().println(df.format(new Date())+" INFO ["+cl+"] "+string);
		} catch (IOException e) {
		}
	}

	public void warn(String string) {
		try {
			SyncLoader.getLogStream().println(df.format(new Date())+" WARN ["+cl+"] "+string);
		} catch (IOException e) {
		}
	}

}

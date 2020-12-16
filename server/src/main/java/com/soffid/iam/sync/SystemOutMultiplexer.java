package com.soffid.iam.sync;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;

public class SystemOutMultiplexer extends PrintStream {
	public static ThreadLocal<PrintWriter> log = new ThreadLocal<PrintWriter>();
	
	public static void attachLogger (PrintWriter writer)
	{
		SystemOutMultiplexer.log.set(writer);
	}
	
	public static void detachLogger ()
	{
		SystemOutMultiplexer.log.remove();
	}

	public SystemOutMultiplexer(OutputStream out) {
		super(out);
	}

	@Override
	public void flush() {
		super.flush();
	}

	@Override
	public void close() {
		detachLogger();
		super.close();
	}

	@Override
	public boolean checkError() {
		return super.checkError();
	}

	@Override
	protected void setError() {
		super.setError();
	}

	@Override
	protected void clearError() {
		super.clearError();
	}

	@Override
	public void write(int b) {
		super.write(b);
	}

	@Override
	public void write(byte[] buf, int off, int len) {
		super.write(buf, off, len);
	}

	@Override
	public void print(boolean b) {
		super.print(b);
		if (log.get() != null)
			log.get().print(b);
	}

	@Override
	public void print(char c) {
		super.print(c);
		if (log.get() != null)
			log.get().print(c);
	}

	@Override
	public void print(int i) {
		super.print(i);
		if (log.get() != null)
			log.get().print(i);
	}

	@Override
	public void print(long l) {
		super.print(l);
		if (log.get() != null)
			log.get().print(l);
	}

	@Override
	public void print(float f) {
		super.print(f);
		if (log.get() != null)
			log.get().print(f);

	}

	@Override
	public void print(double d) {
		super.print(d);
		if (log.get() != null)
			log.get().print(d);

	}

	@Override
	public void print(char[] s) {
		super.print(s);
		if (log.get() != null)
			log.get().print(s);
	}

	@Override
	public void print(String s) {
		super.print(s);
		if (log.get() != null)
			log.get().print(s);
	}

	@Override
	public void print(Object obj) {
		super.print(obj);
		if (log.get() != null)
			log.get().print(obj);
	}

	@Override
	public void println() {
		super.println();
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(boolean x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(char x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(int x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(long x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(float x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(double x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(char[] x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(String x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public void println(Object x) {
		super.println(x);
		if (log.get() != null)
			log.get().println();
	}

	@Override
	public PrintStream printf(String format, Object... args) {
		if (log.get() != null)
			log.get().println(String.format(format, args));
		return super.printf(format, args);
	}

	@Override
	public PrintStream printf(Locale l, String format, Object... args) {
		if (log.get() != null)
			log.get().println(String.format(l, format, args));
		return super.printf(l, format, args);
	}

	@Override
	public PrintStream format(String format, Object... args) {
		return super.format(format, args);
	}

	@Override
	public PrintStream format(Locale l, String format, Object... args) {
		return super.format(l, format, args);
	}

	@Override
	public PrintStream append(CharSequence csq) {
		if (log.get() != null)
			log.get().append(csq);
		return super.append(csq);
	}

	@Override
	public PrintStream append(CharSequence csq, int start, int end) {
		if (log.get() != null)
			log.get().append(csq.subSequence(start, end));
		return super.append(csq, start, end);
	}

	@Override
	public PrintStream append(char c) {
		if (log.get() != null)
			log.get().append(c);
		return super.append(c);
	}

	@Override
	public void write(byte[] b) throws IOException {
		super.write(b);
	}

}

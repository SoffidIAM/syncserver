package com.soffid.iam.sync.engine.cron;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;

public class SplitPrintWriter extends PrintWriter {

	private PrintWriter out2;

	public SplitPrintWriter(PrintWriter out1, PrintWriter out2) {
		super(out1);
		this.out2 = out2;
	}

	public void flush() {
		out2.flush();
		super.flush();
	}

	public void close() {
		out2.close();
		super.close();
	}

	public void write(int c) {
		out2.write(c);
		super.write(c);
	}

	public void write(char[] buf, int off, int len) {
		out2.write(buf, off, len);
		super.write(buf, off, len);
	}

	public void write(char[] buf) {
		out2.write(buf);
		super.write(buf);
	}

	public void write(String s, int off, int len) {
		out2.write(s, off, len);
		super.write(s, off, len);
	}

	public void write(String s) {
		out2.write(s);
		super.write(s);
	}

	public void print(boolean b) {
		out2.print(b);
		super.print(b);
		
	}

	public void print(char c) {
		out2.print(c);
		super.print(c);
		
	}

	public void print(int i) {
		out2.print(i);
		super.print(i);
		
	}

	public void print(long l) {
		out2.print(l);
		super.print(l);
		
	}

	public void print(float f) {
		out2.print(f);
		super.print(f);
	}

	public void print(double d) {
		out2.print(d);
		super.print(d);
	}

	public void print(char[] s) {
		out2.print(s);
		super.print(s);
	}

	public void print(String s) {
		out2.print(s);
		super.print(s);
	}

	public void print(Object obj) {
		out2.print(obj);
		super.print(obj);
	}

	public void println() {
		out2.println();
		super.println();
	}

	public void println(boolean x) {
		out2.println(x);
		super.println(x);
	}

	public void println(char x) {
		out2.println(x);
		super.println(x);
	}

	public void println(int x) {
		out2.println(x);
		super.println(x);
	}

	public void println(long x) {
		out2.println(x);
		super.println(x);
	}

	public void println(float x) {
		out2.println(x);
		super.println(x);
	}

	public void println(double x) {
		out2.println(x);
		super.println(x);
	}

	public void println(char[] x) {
		out2.println(x);
		super.println(x);
	}

	public void println(String x) {
		out2.println(x);
		super.println(x);
	}

	public void println(Object x) {
		out2.println(x);
		super.println(x);
	}

	public PrintWriter printf(String format, Object... args) {
		out2.printf(format, args);
		super.printf(format, args);
		return this;
	}

	public PrintWriter printf(Locale l, String format, Object... args) {
		out2.printf(l, format, args);
		super.printf(l, format, args);
		return this;
	}

	public PrintWriter format(String format, Object... args) {
		out2.format(format, args);
		super.format(format, args);
		return this;
	}

	public PrintWriter format(Locale l, String format, Object... args) {
		out2.format(l, format, args);
		super.format(l, format, args);
		return this;
	}

	public PrintWriter append(CharSequence csq) {
		out2.append(csq);
		super.append(csq);
		return this;
	}

	public PrintWriter append(CharSequence csq, int start, int end) {
		out2.append(csq, start, end);
		super.append(csq, start, end);
		return this;
	}

	public PrintWriter append(char c) {
		out2.append(c);
		super.append(c);
		return this;
	}

}

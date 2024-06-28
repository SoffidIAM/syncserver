package com.soffid.iam.sync.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class VoidWriter extends PrintWriter {
	public VoidWriter() {
		super(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				// Do nothing
			}
		});
	}
}

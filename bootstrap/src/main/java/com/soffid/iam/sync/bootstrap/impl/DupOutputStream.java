package com.soffid.iam.sync.bootstrap.impl;

import java.io.IOException;
import java.io.OutputStream;

public class DupOutputStream extends OutputStream {
    OutputStream out1;
    OutputStream out2;

    public DupOutputStream(OutputStream out1, OutputStream out2) {
        super();
        this.out1 = out1;
        this.out2 = out2;
    }

    public void close() throws IOException {
        out1.close ();
//        out2.close (); stdout should not be closed
    }

    @Override
    public void flush() throws IOException {
        out1.flush ();
        out2.flush ();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out1.write(b, off, len);
        out2.write(b,off,len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out1.write (b);
        out2.write(b);
    }

    @Override
    public void write(int b) throws IOException {
        out1.write(b);
        out2.write(b);
    }

}

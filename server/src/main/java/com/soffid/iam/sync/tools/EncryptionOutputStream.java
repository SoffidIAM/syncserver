package com.soffid.iam.sync.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionOutputStream extends OutputStream {
	int blockSize;
	OutputStream out;
	private byte[] buffer;
	private int offset;
	private Cipher cipher;
	boolean eof = false;
	
	public EncryptionOutputStream ( OutputStream out, String seed) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
		this.out = out;
		
		MessageDigest d = MessageDigest.getInstance("SHA-256");
		byte[] key = d.digest(seed.getBytes(StandardCharsets.UTF_8));
		Key secretKey = new SecretKeySpec(key, "AES");
		cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        blockSize = cipher.getBlockSize();
        if (blockSize <= 0) blockSize = 2048;
        buffer = new byte[blockSize];
        offset = 0;
	}

	@Override
	public void write(int b) throws IOException {
         buffer [offset++] = (byte) b;
         if (offset == buffer.length)
        	 flush();
	}

	@Override
	public void flush() throws IOException {
		flush(false);
	}

	public void flush(boolean end) throws IOException {
		if (offset > 0  || end && !eof) {
			try {
				byte[] r = end ? 
						cipher.doFinal(buffer, 0, offset):
						cipher.update(buffer, 0, offset);
				if (r != null)
					out.write(r);
			} catch (IllegalBlockSizeException e) {
				throw new IOException("Error encrypting", e);
			} catch (BadPaddingException e) {
				throw new IOException("Error encrypting", e);
			}
			if (end) eof = true;
		}
		offset = 0;
	}

    public void close() throws IOException {
    	flush(true);
    	out.close();
    }
}

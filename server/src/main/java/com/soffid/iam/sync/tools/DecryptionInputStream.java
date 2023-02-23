package com.soffid.iam.sync.tools;

import java.io.IOException;
import java.io.InputStream;
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

public class DecryptionInputStream extends InputStream {
	int blockSize;
	InputStream in;
	private byte[] buffer;
	private int offset;
	private int bufferSize;
	private Cipher cipher;
	boolean eof = false;
	
	public DecryptionInputStream ( InputStream in, String seed) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
		this.in = in;
		MessageDigest d = MessageDigest.getInstance("SHA-256");
		byte[] key = d.digest(seed.getBytes(StandardCharsets.UTF_8));
		Key secretKey = new SecretKeySpec(key, "AES");
		cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        blockSize = cipher.getBlockSize();
        if (blockSize <= 0) blockSize = 2048;
        buffer = null;
        offset = bufferSize = 0;
	}

    public void close() throws IOException {
    	in.close();
    }

	@Override
	public int read() throws IOException {
		if (offset >= bufferSize) {
			if (eof) return -1;
			
			byte data[] = new byte[blockSize];
			int read;
			do {
				read = in.read(data);
				try {
					if (read < 0) {
						eof = true;
//						try {
						buffer = cipher.doFinal() ;
//						} catch (BadPaddingException e) {
//							return -1;
//						}
						if (buffer == null || buffer.length == 0)
							return -1;
					} else {
						buffer = cipher.update(data, 0, read);
					}
					bufferSize = buffer.length;
					offset = 0;
				} catch (Exception e) {
					throw new IOException("Decrypting error", e);
				}
			} while (bufferSize == 0);
		}
		int r = (int) buffer[offset ++];
		if (r < 0) r += 256;
		return r;
	}
}

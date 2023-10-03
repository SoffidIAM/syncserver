import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.NoSuchPaddingException;

import com.soffid.iam.sync.bootstrap.impl.DecryptionInputStream;
import com.soffid.iam.sync.bootstrap.impl.EncryptionInputStream;

public class EncryptionTest {
	static String sourceText = "This is a test to be encrypted in some way";
	static String pass = "ThisIsAPassword";
	public static void main (String args[]) throws Exception {
		DecryptionInputStream i = new EncryptionInputStream(new ByteArrayInputStream(sourceText.getBytes()), pass);
		byte[] r = read (i);
		System.out.println(Base64.getEncoder().encodeToString(r));
		
		i = new DecryptionInputStream(new ByteArrayInputStream(r), pass, true);
		r = read(i);
		System.out.println(new String(r));
	}
	
	private static byte[] read(DecryptionInputStream i) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int read = i.read(); read >= 0; read = i.read())
			out.write(read);
		i.close();
		return out.toByteArray();
	}
}

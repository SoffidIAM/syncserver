package bubu.test.seycon;

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import es.caib.seycon.util.Base64;

public class AESTest {
	public void main (String args[]) throws DataLengthException, IllegalStateException, InvalidCipherTextException 
	{
		byte[] key = new byte[0];
		byte[] iv = new byte[0];
		PaddedBufferedBlockCipher cypher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
		ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(key), iv);
		cypher.init(true, parameterIV);
		byte[] out = new byte[4096];
		byte[] in = "".getBytes();
		int r = cypher.processBytes(in, 0, in.length, out, 0);
		Base64.encodeBytes(out, 0, r);
		
	}

}

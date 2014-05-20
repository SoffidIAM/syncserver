
// Copyright (c) 2000 Govern  de les Illes Balears
package bubu.test.seycon;
import java.security.*;

/**
 * A Class class.
 * <P>
 * @author DGTIC
 */
public class Password extends Object
{
  private static MessageDigest digest;

  /**
   * Constructor
   */
  public Password()
  {
  }

  public static void main (String args [])
  {
   try {
    if (args.length == 2 && args[0].equals ("decode") )
    {
      es.caib.seycon.ng.comu.Password p = es.caib.seycon.ng.comu.Password.decode(args[1]);
      System.out.println ("Coded  ="+p.toString ());
      System.out.println ("Decoded="+p.getPassword ());
      System.out.println ("Base 64="+toBase64(p.getPassword().getBytes("UTF-8")));
      digest = MessageDigest.getInstance("SHA-1");
      byte bytes [] = digest.digest (p.getPassword().getBytes());
      System.out.println ("HASH = "+ toBase64 (bytes) );
    } else 
    if (args.length == 2 && args[0].equals ("base64tohex") )
    {
      System.out.println ("From base 64 "+args[1]);
      char c [] = fromBase64 (args[1]);
      for (int i = 0; i < c.length; i++)
      {
        int j = c[i] / 16;
        if ( j < 10 ) System.out.print (j);
        else System.out.print ( (char) ('a'+j-10));
        j = c[i] % 16;
        if ( j < 10 ) System.out.print (j);
        else System.out.print ( (char) ('a'+j-10));
      }
    }
    else
    {
      String s = args[0];
      if (s.startsWith("0x"))
      {
        byte b [] = new byte[s.length()/2-1];
        for (int i = 2; i < s.length(); i++)
        {
          int j = i / 2 - 1;
          b[j] = 0;
          if (s.charAt(i) >= '0' && s.charAt(i) <= '9')
            b [j] = (byte) ((s.charAt(i)-'0') * 16);
          else if (s.charAt(i) >= 'a' && s.charAt(i) <= 'f')
            b [j] = (byte) ((s.charAt(i)-'a'+10) * 16);
          else if (s.charAt(i) >= 'A' && s.charAt(i) <= 'F')
            b [j] = (byte) ((s.charAt(i)-'A'+10) * 16);
          else
            throw new Exception ("Caracter no esperador "+s.charAt(i));
          i ++;
          if (s.charAt(i) >= '0' && s.charAt(i) <= '9')
            b [j] += (s.charAt(i)-'0');
          else if (s.charAt(i) >= 'a' && s.charAt(i) <= 'f')
            b [j] += (s.charAt(i)-'a'+10);
          else if (s.charAt(i) >= 'A' && s.charAt(i) <= 'F')
            b [j] += (s.charAt(i)-'A'+10);
          else
            throw new Exception ("Caracter no esperador "+s.charAt(i));
        }
        s = new String (b);
      }
      es.caib.seycon.ng.comu.Password p = new es.caib.seycon.ng.comu.Password(s);
      System.out.println ("Coded  ="+p.toString ());
      System.out.println ("Decoded="+p.getPassword ());
      System.out.println ("Base 64="+toBase64(s.getBytes("UTF-8")));
      digest = MessageDigest.getInstance("SHA-1");
      byte bytes [] = digest.digest (s.getBytes());
      System.out.println ("HASH = "+ toBase64 (bytes) );
    }
   } catch (Exception e) {e.printStackTrace ();}
  }
  // Codificar BASE 64
  static private String base64Array = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
				  "abcdefghijklmnopqrstuvwxyz" +
				  "0123456789+/";

  private static String toBase64 (byte source [])
  {
	  int i ;
  	int j = 0;
    int len = source.length;
    String result = "";
   	for ( i = 0; i < len; i+=3)
	  {
	  	int c1, c2, c3;
		  int index;
	  	c1 = source[i];
 		  if ( i+1 < len ) c2 = (char) source[i+1];
 		  else c2 = 0;
		  if ( i+2 < len ) c3 = (char) source [i+2];
		  else c3 = 0;
        index = (c1 & 0xfc) >> 2;
      result = result + base64Array.charAt ( index );
		  index = ((c1 & 0x03) << 4) | ((c2 & 0xf0) >> 4);
      result = result + base64Array.charAt ( index );
      if ( i+1 >= len ) result = result + "=";
  		else
	  	{
		  	index = ((c2 & 0x0f) << 2) | ((c3 & 0xc0) >> 6);
        result = result + base64Array.charAt ( index );
		  }
      if ( i+2 >= len ) result = result + '=';
  		else
		  {
 		   	index = (c3 & 0x3f);
        result = result + base64Array.charAt ( index );
		  }
	  }
	  return result;
  }
  /** Genera un array de caracteres a partir de su codificación base 64 */
  public static char [] fromBase64 (String source)
  {
	  int i ;
  	int j = 0;
    int len = source.length ();
    int len2 = (len / 4) * 3;
    if (source.endsWith ("==")) len2 = len2 - 2;
    else if (source.endsWith ("=")) len2 = len2 - 1;
    char result [] = new char[len2];
   	for ( i = 0; i < len; i+=4)
	  {
	  	char c1, c2, c3, c4;
      int c1d, c2d, c3d, c4d;
	  	c1 = source.charAt (i);
	  	c2 = source.charAt (i+1);
	  	c3 = source.charAt (i+2);
	  	c4 = source.charAt (i+3);
      c1d = base64Array.indexOf (c1);
      c2d = base64Array.indexOf (c2);
      c3d = base64Array.indexOf (c3);
      c4d = base64Array.indexOf (c4);
      result [ j ++ ] = (char) ((c1d << 2) | ((c2d & 0x30) >> 4));
      if ( c3 != '=')
      {
        result [ j ++ ] = (char) (((c2d & 0x0f) << 4) | ((c3d & 0x3c) >> 2));
        if ( c4 != '=')
        {
          result [ j ++ ] = (char) (((c3d & 0x03) << 6) | c4d);
        }
      }
	  }
	  return result;
  }

}


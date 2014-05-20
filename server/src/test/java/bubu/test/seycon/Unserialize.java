
// Copyright (c) 2000 Govern  de les Illes Balears
package bubu.test.seycon;
import java.io.*;

/**
 * A Class class.
 * <P>
 * @author DGTIC
 */
public class Unserialize extends Object
{

  /**
   * Constructor
   */
  public Unserialize()
  {
  }

  /**
   * main
   * @param args
   */
  public static void main(String[] args)
  {
     try {
         System.out.println ("Recuperando archivo");
         FileInputStream in = new FileInputStream (args[0]);
         ObjectInputStream oin = new ObjectInputStream (in);
         oin.readObject ();
         oin.close ();
         in.close ();
         System.out.println ("Fin");
     } catch (Exception e)
     {
         e.printStackTrace(System.err);
     }
  }
}

 
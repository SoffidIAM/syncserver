
// Copyright (c) 2000 Govern  de les Illes Balears
package bubu.test.seycon;

/**
 * A Class class.
 * <P>
 * @author DGTIC
 */
public class tz extends Object
{

  /**
   * Constructor
   */
  public tz()
  {
  }

  /**
   * main
   * @param args
   */
  public static void main(String[] args)
  {
//    tz tz = new tz();

   java.util.TimeZone tz2 = java.util.TimeZone.getDefault ();
   System.out.println ("TZ="+tz2.getID ());
   java.util.TimeZone tz = java.util.TimeZone.getTimeZone(args[0]);
      java.text.SimpleDateFormat df2 = new java.text.SimpleDateFormat ("dd/MM/yyyy HH:mm:ss, zzzz");
      System.out.println ("Now = "+
        df2.format(new java.util.Date ()));
   if (tz == null) System.out.println ("Zona no encontrada"+args[0]);
   else {
     java.util.TimeZone.setDefault (tz);
     System.out.println (tz.toString());
     System.out.println ("TZ="+tz.getID ());
   }
      df2 = new java.text.SimpleDateFormat ("dd/MM/yyyy HH:mm:ss, zzzz");
      System.out.println ("Now = "+
        df2.format(new java.util.Date ()));
    String array [] = java.util.TimeZone.getAvailableIDs (2*60*60*1000);
    int i;
    for ( i = 0; i < array.length; i++)
    {
      System.out.println (array[i]);
    }
    System.exit (0);
  }
}

 
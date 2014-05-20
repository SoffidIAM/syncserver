
// Copyright (c) 2000 Govern  de les Illes Balears
package bubu.util;
import java.text.*;

/**
 * bubu.util.javadisk
 * <P>
 * @author DGTIC
 */
public class javadisk {

  /**
   * Constructor
   */
  public javadisk() {
    System.loadLibrary ("javadisk");
  }

  /**
   * main
   * @param args
   */
/*  public static void main(String[] args) {
    javadisk j = new javadisk();
    java.lang.String array [] = j.enumDrives ();
    int i ;
    for (i=0; i < array.length; i++)
    {
      System.out.println (array[i]);
      String s = j.driveType (array[i]);
      if (s.equals("fixed"))
        System.out.println (NumberFormat.getInstance().format(j.freeSpace(array[i])));
    }
    System.exit (0);
  }
*/
  public native String [] enumDrives();
  public native long freeSpace(String drive);
  public native String driveType(String drive);
}



// Copyright (c) 2000 Govern  de les Illes Balears
package bubu.test.seycon;
import java.io.*;
import java.util.LinkedList;

import com.soffid.iam.api.Account;

import es.caib.seycon.ng.comu.Usuari;

/**
 * A Class class.
 * <P>
 * @author DGTIC
 */
public class Transform extends Object
{

  /**
   * Constructor
   */
  public Transform()
  {
  }

  /**
   * main
   * @param args
   */
  public static void main(String[] args)
  {
     try {
         System.out.println ("Transformando cuenta");
         Usuari u = new Usuari();
         u.setCodi("test");
         es.caib.seycon.ng.comu.Account a = new es.caib.seycon.ng.comu.Account();
         a.setName("test");
         a.setOwnerUsers(new LinkedList<Usuari>());
         a.getOwnerUsers().add(u);
         System.out.println(a);
         Account acc = Account.toAccount(a);
         System.out.println(acc);
     } catch (Exception e)
     {
         e.printStackTrace(System.err);
     }
  }
}

 
package es.caib.seycon.ng.sync.engine.diag;

import java.util.Iterator;
import java.util.Map;

public class DiagThread extends Thread {

	@Override
	public void run() {
		while (true) {
			
	        Map m = Thread.getAllStackTraces();
	        for (Iterator it = m.keySet().iterator(); it.hasNext();)
	        {
	            Thread t = (Thread) it.next();
	            System.out.print (t.toString());
	            System.out.print (" "+t.getState().toString());
	            System.out.print (" priority "+t.getPriority());
	            System.out.print ("\n");
	            StackTraceElement elements[] = (StackTraceElement[]) m.get(t);
	            for (int i = 0 ; elements != null && i < elements.length; i++)
	            {
	            	System.out.print("  "+elements[i].toString()+"\n");
	            }
	            System.out.print("\n");
	        }
	        System.out.flush();
	        try {
				sleep(5000);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

}

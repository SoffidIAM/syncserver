package es.caib.seycon.ng.sync;

import java.rmi.RemoteException;

import com.soffid.iam.sync.SoffidApplication;

import es.caib.seycon.ng.exception.InternalErrorException;

public class SeyconApplication {

    public static void main(String args[]) throws RemoteException, InternalErrorException {
    	SoffidApplication.main(args);
    }
    
    public static es.caib.seycon.ng.sync.jetty.JettyServer getJetty() {
        return new es.caib.seycon.ng.sync.jetty.JettyServer( SoffidApplication.getJetty() );
    }

}

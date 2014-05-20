package es.caib.seycon.ng.sync.engine.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import es.caib.seycon.Server;

public class ServerImpl extends UnicastRemoteObject implements Server {

    protected ServerImpl() throws RemoteException {
        super();
    }

    private static final long serialVersionUID = 1L;

}

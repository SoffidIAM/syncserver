package es.caib.seycon.ng.sync.intf;

import java.rmi.RemoteException;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.comu.Tasca;

public interface CustomTaskMgr {
	void processTask(Tasca task) throws RemoteException, InternalErrorException;
}

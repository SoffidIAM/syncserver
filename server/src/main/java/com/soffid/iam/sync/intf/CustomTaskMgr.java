package com.soffid.iam.sync.intf;

import java.rmi.RemoteException;

import com.soffid.iam.api.Task;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.comu.Tasca;

public interface CustomTaskMgr {
	void processTask(Task task) throws RemoteException, InternalErrorException;
}

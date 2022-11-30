/**
 * 
 */
package com.soffid.iam.sync.agent;

import java.rmi.RemoteException;

import es.caib.seycon.ng.exception.InternalErrorException;

/**
 * @author bubu
 *
 */
public interface AgentInterface
{
	String getAgentVersion();
	
	boolean supportsRename ();
	
	void startCaptureLog ();
	
	String endCaptureLog ();

	String getCapturedLog (); 
	
	boolean isSingleton ();
	
	void setDebug(boolean debug);
	
	void close();
	
	void checkConnectivity() throws InternalErrorException;

}

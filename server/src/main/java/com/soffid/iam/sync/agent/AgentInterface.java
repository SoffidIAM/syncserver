/**
 * 
 */
package com.soffid.iam.sync.agent;

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
}

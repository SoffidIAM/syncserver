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
}

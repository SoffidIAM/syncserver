/**
 * 
 */
package es.caib.seycon.ng.sync.agent;

/**
 * @author bubu
 *
 */
public interface AgentInterface extends com.soffid.iam.sync.agent.AgentInterface
{
	String getAgentVersion();
	
	boolean supportsRename ();
}

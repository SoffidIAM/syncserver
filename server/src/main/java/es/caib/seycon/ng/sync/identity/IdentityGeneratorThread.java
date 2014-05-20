/**
 * 
 */
package es.caib.seycon.ng.sync.identity;

import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool.ThreadBound;

/**
 * @author bubu
 *
 */
public class IdentityGeneratorThread extends es.caib.seycon.ng.model.identity.IdentityGeneratorThread
{ 
	
	private ThreadBound bound;

	public IdentityGeneratorThread( ThreadBound bound)
	{
		this.bound = bound;
	}

	@Override
	public void run ()
	{
		ConnectionPool.getPool().setThreadStatus(bound);
		super.run();
	}
	
	
}

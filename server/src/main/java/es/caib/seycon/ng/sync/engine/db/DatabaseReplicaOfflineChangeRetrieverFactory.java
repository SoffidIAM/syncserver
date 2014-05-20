/**
 * 
 */
package es.caib.seycon.ng.sync.engine.db;

import java.sql.Connection;

import es.caib.seycon.ng.sync.intf.DatabaseReplicaOfflineChangeRetriever;


/**
 * @author bubu
 *
 */
public abstract class DatabaseReplicaOfflineChangeRetrieverFactory
{

	/**
	 * 
	 */
	public static final String PROPERTY_NAME = "es.caib.syeon.ng.sync.engine.db.OfflineChangeRetrieverFactory";

	public abstract DatabaseReplicaOfflineChangeRetriever newInstance (Connection conn);
	
	public static DatabaseReplicaOfflineChangeRetrieverFactory getFactory() throws ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		String factoryName = System.getProperty(PROPERTY_NAME);
		if (factoryName == null)
			throw new RuntimeException ("No factory defined");
		Class cl = Class.forName(factoryName);
		
		DatabaseReplicaOfflineChangeRetrieverFactory factory = (DatabaseReplicaOfflineChangeRetrieverFactory) cl.newInstance();
		
		return factory;
		
	}
}

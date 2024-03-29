/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.HashMap;
import java.util.Map;

import com.soffid.iam.config.Config;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.tools.NullSqlObjet;

import bsh.BshClassManager;
import bsh.ExternalNameSpace2;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.UtilEvalError;
import bsh.Variable;
import es.caib.seycon.ng.ServiceLocator;

public class LegacyExtensibleObjectNamespace extends ExternalNameSpace2
{
	private ExtensibleObject object;
	private ServerService serverService;
	private BSHAgentObject agent;
	Map<String, Object> externalMap = new HashMap<String, Object>();
	
	/**
	 * @param parent
	 * @param classManager
	 * @param name
	 * @param dades
	 * @param ead
	 * @param accountName
	 */
	@SuppressWarnings("unchecked")
	public LegacyExtensibleObjectNamespace (NameSpace parent, BshClassManager classManager, String name,
					ExtensibleObject object,
					ServerService serverService, BSHAgentObject bshAgentObject)
	{
		super(parent, name, new HashMap<String, Object>());
		externalMap = super.getMap();
		this.object = object;
		this.serverService = serverService;
		this.agent = bshAgentObject;
	}

	boolean inSetVariable = false;
	
	@Override
	protected Variable getVariableImpl(String name, boolean recurse)
			throws UtilEvalError {
		try
		{
			if (inSetVariable || externalMap.get(name) != null)
				return super.getVariableImpl(name, recurse);

			Object value = object.get(name);
			if ("serverService".equals (name))
				externalMap.put(name,  serverService);
			else if ("serviceLocatorV1".equals (name))
				externalMap.put(name,  ServiceLocator.instance());
			else if ("serviceLocator".equals (name))
			{
				Config config = Config.getConfig();
				if (config.isAgent())
					externalMap.put(name,  new RemoteServiceLocator());
				else
					externalMap.put(name,  com.soffid.iam.ServiceLocator.instance());
			}
			else if ("remoteServiceLocator".equals (name))
				externalMap.put(name,  new RemoteServiceLocator());
			else if ("dispatcherService".equals (name))
				externalMap.put(name,  agent);
			else if ("THIS".equalsIgnoreCase(name) )
				externalMap.put(name,  object);
			else if (object.containsKey(name)){
				if (value == null || value instanceof NullSqlObjet || value instanceof es.caib.seycon.ng.sync.bootstrap.NullSqlObjet)
					externalMap.put(name,  Primitive.NULL);
				else
					externalMap.put(name,  value);				
			}

			return super.getVariableImpl(name, recurse);
		}
		catch (Exception e)
		{
			throw new UtilEvalError(e.toString());
		}
	}

}

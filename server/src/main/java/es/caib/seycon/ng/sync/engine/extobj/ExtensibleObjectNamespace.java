/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.remote.RemoteServiceLocator;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;
import bsh.BshClassManager;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.UtilEvalError;

class ExtensibleObjectNamespace extends NameSpace
{
	private ExtensibleObject object;
	private ServerService serverService;

	/**
	 * @param parent
	 * @param classManager
	 * @param name
	 * @param dades
	 * @param ead
	 * @param accountName
	 */
	public ExtensibleObjectNamespace (NameSpace parent, BshClassManager classManager, String name,
					ExtensibleObject object,
					ServerService serverService)
	{
		super(parent, classManager, name);
		this.object = object;
		this.serverService = serverService;

	}

	@Override
	public Object getVariable (String name, boolean recurse) throws UtilEvalError
	{
		Object value = null; 
		try
		{
			if ("serverService".equals (name))
				return serverService;
			if ("remoteServiceLocator".equals (name))
				return new RemoteServiceLocator();
			if ("serviceLocator".equals (name))
				return ServiceLocator.instance();
			
			value = object.getAttribute(name);
	    	if (value != null)
	    		return value;
	    	else if (object.containsKey(name))
	    		return Primitive.NULL;
	    	else
	    	{
	    		value = super.getVariable(name, recurse);
    			return value;
	    	}
		}
		catch (Exception e)
		{
			throw new UtilEvalError(e.toString());
		}
	}

}
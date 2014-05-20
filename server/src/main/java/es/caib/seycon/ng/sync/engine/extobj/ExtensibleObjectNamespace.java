/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import bsh.BshClassManager;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.UtilEvalError;

class ExtensibleObjectNamespace extends NameSpace
{
	private ExtensibleObject object;

	/**
	 * @param parent
	 * @param classManager
	 * @param name
	 * @param dades
	 * @param ead
	 * @param accountName
	 */
	public ExtensibleObjectNamespace (NameSpace parent, BshClassManager classManager, String name,
					ExtensibleObject object)
	{
		super(parent, classManager, name);
		this.object = object;

	}

	@Override
	public Object getVariable (String name, boolean recurse) throws UtilEvalError
	{
		Object value = null; 
		try
		{
			value = object.getAttribute(name);
	    	if (value != null)
	    		return value;
	    	else if (object.containsKey(name))
	    		return Primitive.NULL;
	    	else
	    	{
	    		value = super.getVariable(name, recurse);
	    		if (Primitive.VOID.equals (value))
	    			return Primitive.NULL;
	    		else
	    			return value;
	    	}
		}
		catch (Exception e)
		{
			throw new UtilEvalError(e.toString());
		}
	}

}
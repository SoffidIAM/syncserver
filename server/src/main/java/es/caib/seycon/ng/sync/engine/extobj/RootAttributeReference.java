package es.caib.seycon.ng.sync.engine.extobj;

import java.util.Map;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public class RootAttributeReference extends AttributeReference
{
	public RootAttributeReference(ExtensibleObject object) {
		super();
		this.object = object;
	}

	ExtensibleObject object;
	
	@Override
	public
	void setValue(Object value)
	{
		throw new RuntimeException ("Cannot set root object value");
	}

	@Override
	public
	Object getValue()
	{
		return object;
	}

	@Override
	public
	String getName()
	{
		return "";
	}

}

package com.soffid.iam.sync.engine.extobj;

public abstract class AttributeReference
{
	public AttributeReference getParentReference()
	{
		return parentReference;
	}

	public void setParentReference(AttributeReference parentReference)
	{
		this.parentReference = parentReference;
	}

	AttributeReference parentReference;
	
	public abstract void setValue (Object value);
	
	public abstract Object getValue ();
	
	public abstract String getName();
}

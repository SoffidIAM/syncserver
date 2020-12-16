package com.soffid.iam.sync.engine.extobj;

import java.lang.reflect.Array;
import java.util.LinkedList;
import java.util.List;

public class ArrayAttributeReference extends AttributeReference
{
	int index;
	
	public ArrayAttributeReference(int index) {
		super();
		this.index = index;
	}

	public int getIndex()
	{
		return index;
	}

	public void setIndex(int index)
	{
		this.index = index;
	}

	@Override
	public
	void setValue(Object value)
	{
		if (parentReference == null)
			throw new RuntimeException("Unknown array parent object");
		Object p = parentReference.getValue();
		if (p == null)
		{
			p = new LinkedList<Object>();
			parentReference.setValue(p);
		}
		if (p instanceof List)
		{
			@SuppressWarnings("unchecked")
			List<Object> l = (List<Object>) p;
			while (l.size() <= index)
				l.add(null);
			l.set(index, value);
		}
		else if (p.getClass().isArray())
			Array.set(p, index, value);
		else
			throw new RuntimeException("Object " + parentReference.getName()+" is not an array");
		
	}

	@Override
	public
	Object getValue()
	{
		if (parentReference == null)
			return null;
		Object p = parentReference.getValue();
		if (p == null)
			return null;
		else if (p instanceof List)
			return ((List) p).get(index);
		else if (p.getClass().isArray())
			return Array.get(p, index);
		else
			return null;
		
	}

	@Override
	public
	String getName()
	{
		return parentReference.getName()+"["+index+"]";
	}

}

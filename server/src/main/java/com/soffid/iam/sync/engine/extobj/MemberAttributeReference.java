package com.soffid.iam.sync.engine.extobj;

import java.util.HashMap;
import java.util.Map;

public class MemberAttributeReference extends AttributeReference
{
	public MemberAttributeReference(String member) {
		super();
		this.member = member;
	}

	public String getMember()
	{
		return member;
	}

	public void setMember(String member)
	{
		this.member = member;
	}

	String member;
	

	@Override
	public
	void setValue(Object value)
	{
		if (parentReference == null)
			throw new RuntimeException("Unknown parent object");
		Object p = parentReference.getValue();
		if (p == null)
		{
			p = new HashMap<String,Object>();
			parentReference.setValue(p);
		}
		if (p instanceof Map)
		{
			@SuppressWarnings("unchecked")
			Map<String,Object> m = (Map<String,Object>) p;
			m.put(member, value);
		}
		else
			throw new RuntimeException("Object " + parentReference.getName()+" is not a map");
		
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
		else if (p instanceof Map)
			return ((Map) p).get(member);
		else
			return null;
		
	}

	@Override
	public
	String getName()
	{
		return parentReference.getName()+"{\""+member+"\"}";
	}

}

/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;


/**
 * @author bubu
 *
 */
public class CustomExtensibleObject extends es.caib.seycon.ng.sync.intf.ExtensibleObject
{
	CustomObject object;
	ServerService serverService;
	private String dispatcher;
	public CustomExtensibleObject (CustomObject obj, ServerService serverService)
	{
		super();
		this.object = obj;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_CUSTOM.getValue());
	}
	
	
	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		if (obj != null)
			return obj;
		
		if ("name".equals(attribute))
			obj = object.getName();
		else if ("type".equals(attribute))
			obj = object.getType();
		else if ("description".equals(attribute))
			obj = object.getDescription();
		else if ("id".equals(attribute))
			obj = object.getId();
		else if ("attributes".equals(attribute))
			obj = object.getAttributes();
		else
			return null;
		put (attribute, obj);
		return obj;
	}
}

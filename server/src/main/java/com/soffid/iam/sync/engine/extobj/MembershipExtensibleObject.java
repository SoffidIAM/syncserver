/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;
/**
 * @author bubu
 *
 */
public class MembershipExtensibleObject extends ExtensibleObject
{
	Account account;
	User user;
	Group grup;
	
	ServerService serverService;

	public MembershipExtensibleObject (Account account, User user, Group grup, ServerService serverService)
	{
		super();
		this.grup = grup;
		this.account = account;
		this.user = user;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_GRANTED_GROUP.getValue());

	}

	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		if (obj != null)
			return obj;
		
		if ("user".equals(attribute))
			obj = new UserExtensibleObject(account, user, serverService);
		else if ("group".equals(attribute))
			obj = new GroupExtensibleObject(grup, account.getSystem(), serverService);
		else if ("userName".equals(attribute))
			obj = user.getUserName();
		else if ("groupName".equals(attribute))
			obj = grup.getName();
		else
			return null;
		put (attribute, obj);
		return obj;
		
	}
}

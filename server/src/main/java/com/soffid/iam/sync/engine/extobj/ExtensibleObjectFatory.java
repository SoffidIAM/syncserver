package com.soffid.iam.sync.engine.extobj;

import java.util.HashMap;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class ExtensibleObjectFatory {
	ServerService server;
	String agentName;
	
	public ExtensibleObject getExtensibleObject (SoffidObjectType type, String object1, String object2) throws InternalErrorException
	{
		if (type == SoffidObjectType.OBJECT_ACCOUNT)
		{
			Account acc = server.getAccountInfo(object1, getAgentName());
			if (acc == null)
			{
				ExtensibleObject eo = new ExtensibleObject();
				eo.setObjectType(SoffidObjectType.OBJECT_ACCOUNT.getValue());
				eo.setAttribute("accountName", object1);
				eo.setAttribute("system", object2);
				return eo;

			}
			else
				return new AccountExtensibleObject(acc, server);
		}
		else if (type == SoffidObjectType.OBJECT_CUSTOM)
		{
			CustomObject obj = server.getCustomObject(object1, object2);
			if (obj == null)
			{
				ExtensibleObject eo = new ExtensibleObject();
				eo.setObjectType(SoffidObjectType.OBJECT_CUSTOM.getValue());
				eo.setAttribute("name", object2);
				eo.setAttribute("type", object1);
				return eo;
			}
			else
				return new CustomExtensibleObject(obj, server);
		}
		else if (type == SoffidObjectType.OBJECT_GROUP)
		{
			Group group;
			try {
				group = server.getGroupInfo(object1, getAgentName());
				return new GroupExtensibleObject(group, getAgentName(),server);
			} catch (UnknownGroupException e) {
				ExtensibleObject eo = new ExtensibleObject();
				eo.setObjectType(SoffidObjectType.OBJECT_GROUP.getValue());
				eo.setAttribute("name", object1);
				return eo;
			}
		}
		else if (type == SoffidObjectType.OBJECT_ROLE)
		{
			Role role;
			try {
				role = server.getRoleInfo(object1, getAgentName());
				if (role == null) {
					ExtensibleObject eo = new ExtensibleObject();
					eo.setObjectType(SoffidObjectType.OBJECT_ROLE.getValue());
					eo.setAttribute("name", object1);
					eo.setAttribute("system", object2);
					return eo;
				} else
					return new RoleExtensibleObject(role, server);
			} catch (UnknownRoleException e) {
				ExtensibleObject eo = new ExtensibleObject();
				eo.setObjectType(SoffidObjectType.OBJECT_ROLE.getValue());
				eo.setAttribute("name", object1);
				eo.setAttribute("system", object2);
				return eo;
			}
		}
		else if (type == SoffidObjectType.OBJECT_USER)
		{
			User user;
			try {
				user = server.getUserInfo(object1, getAgentName());
				Account acc = server.getAccountInfo(object1, getAgentName());
				return new UserExtensibleObject(acc, user, server);
			} catch (UnknownUserException e) {
				ExtensibleObject eo = new ExtensibleObject();
				eo.setObjectType(SoffidObjectType.OBJECT_USER.getValue());
				eo.setAttribute("accountName", object1);
				eo.setAttribute("userName", object1);
				eo.setAttribute("attributes", new HashMap<String, Object>());
				return eo;
			}
		}
		else
			return null;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public ServerService getServer() {
		return server;
	}

	public void setServer(ServerService server) {
		this.server = server;
	}

}

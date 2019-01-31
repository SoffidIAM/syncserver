/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.LinkedList;

import com.soffid.iam.api.MailList;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserData;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
/**
 * @author bubu
 *
 */
public class MailListExtensibleObject extends ExtensibleObject
{
	ServerService serverService;
	private MailList mailList;
	
	public MailListExtensibleObject (MailList mailList, ServerService serverService)
	{
		super();
		this.mailList = mailList;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_MAIL_LIST.getValue());
	}

	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		if (obj != null)
			return obj;

		try {
			if ("id".equals(attribute))
				obj = mailList.getId();
			else if ("description".equals(attribute))
				obj = mailList.getDescription();
			else if ("name".equals(attribute))
				obj = mailList.getName();
			else if ("domain".equals(attribute))
				obj = mailList.getDomainCode();
			else if ("users".equals(attribute))
				obj = mailList.getUsersList().split("[, ]+");
			else if ("groups".equals(attribute))
				obj = mailList.getGroupMembers().split("[, ]+");
			else if ("roles".equals(attribute))
				obj = mailList.getRoleMembers().split("[, ]+");
			else if ("lists".equals(attribute))
				obj = mailList.getLists().split("[, ]+");
			else if ("explodedUsers".equals(attribute))
				obj = mailList.getExplodedUsersList().split("[, ]+");
			else if ("explodedUserAddresses".equals(attribute))
				obj = resolveAddresses (mailList.getExplodedUsersList().split("[, ]+"));
			else if ("attributes".equals(attribute))
				obj = mailList.getAttributes();
			else
				return null;
		}
		catch (InternalErrorException e)
		{
			throw new RuntimeException (e);
		}
		
		put (attribute, obj);
		return obj;
	}

	private Object resolveAddresses(String[] split) throws InternalErrorException {
		if (serverService == null)
			return null;
		
		LinkedList<String> addr = new LinkedList<String>();
		for (String user: split)
		{
			try
			{
				User userInfo = serverService.getUserInfo(user, null);
				if (userInfo != null)
				{
					if ( userInfo.getShortName() != null && userInfo.getMailDomain() != null &&
							userInfo.getShortName().length() > 0)
					{
						addr.add(userInfo.getShortName()+"@"+userInfo.getMailDomain());
					} else {
						UserData data = serverService.getUserData(userInfo.getId(), "EMAIL");
						if (data != null && data.getValue() != null && data.getValue().trim().length() > 0)
							addr.add(data.getValue().trim());
							
					}
				}
			} catch (UnknownUserException e ) {}
		}
		return addr.toArray(new String [addr.size()]);
	}

	@Override
	public boolean equals (Object o)
	{
		if (o instanceof MailListExtensibleObject)
			return mailList.getId().equals (((MailListExtensibleObject) o).mailList.getId());
		else
			return false;
	}

	@Override
	public int hashCode ()
	{
		return mailList.getId().hashCode();
	}


}

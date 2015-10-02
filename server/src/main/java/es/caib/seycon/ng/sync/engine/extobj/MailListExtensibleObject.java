/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import java.util.LinkedList;

import com.soffid.iam.api.MailList;

import es.caib.seycon.ng.comu.DadaUsuari;
import es.caib.seycon.ng.comu.LlistaCorreu;
import es.caib.seycon.ng.comu.SoffidObjectType;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import es.caib.seycon.ng.sync.servei.ServerService;

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

	public MailListExtensibleObject (LlistaCorreu mailList, ServerService serverService)
	{
		super();
		this.mailList = MailList.toMailList(mailList);
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
				Usuari userInfo = serverService.getUserInfo(user, null);
				if (userInfo != null)
				{
					if ( userInfo.getNomCurt() != null && userInfo.getDominiCorreu() != null &&
							userInfo.getNomCurt().length() > 0)
					{
						addr.add(userInfo.getNomCurt()+"@"+userInfo.getDominiCorreu());
					} else {
						DadaUsuari data = serverService.getUserData(userInfo.getId(), "EMAIL");
						if (data != null && data.getValorDada() != null && data.getValorDada().trim().length() > 0)
							addr.add(data.getValorDada().trim());
							
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

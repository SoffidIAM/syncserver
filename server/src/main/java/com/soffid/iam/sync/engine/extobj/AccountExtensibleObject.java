/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;

/**
 * @author bubu
 *
 */
public class AccountExtensibleObject extends ExtensibleObject
{
	ServerService serverService;
	private Account account;
	
	public AccountExtensibleObject (Account account, ServerService serverService)
	{
		super();
		this.account = account;
		this.serverService = serverService;
		setObjectType(SoffidObjectType.OBJECT_ACCOUNT.getValue());
		if (account.getId() == null)
		{
			try {
				Account account2 = serverService.getAccountInfo(account.getName(), account.getSystem());
				if (account2 != null)
					account = account2;
			} catch (InternalErrorException e) {
			}
		}

	}

}

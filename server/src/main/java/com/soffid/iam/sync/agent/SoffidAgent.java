package com.soffid.iam.sync.agent;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Application;
import com.soffid.iam.api.CrudHandler;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.CustomObjectType;
import com.soffid.iam.api.DataType;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Host;
import com.soffid.iam.api.MailList;
import com.soffid.iam.api.ObjectMappingTrigger;
import com.soffid.iam.api.PagedResult;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.service.AdditionalDataService;
import com.soffid.iam.sync.engine.extobj.AccountExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.UserExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObjectMapping;
import com.soffid.iam.sync.intf.ExtensibleObjectMgr;
import com.soffid.iam.sync.intf.GroupMgr;
import com.soffid.iam.sync.intf.HostMgr;
import com.soffid.iam.sync.intf.IndexMgr;
import com.soffid.iam.sync.intf.MailAliasMgr;
import com.soffid.iam.sync.intf.UserMgr;

import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.exception.InternalErrorException;

public class SoffidAgent extends DummyAgent implements IndexMgr, UserMgr, ExtensibleObjectMgr, MailAliasMgr, GroupMgr, HostMgr {
	@Override
	public void index(String objectClass, long id) throws InternalErrorException {
		try {
			CustomObjectType dt = ServiceLocator.instance().getAdditionalDataService().findCustomObjectTypeByName(objectClass);
			if (dt.isTextIndex()) {
				Object object = search(objectClass, id);
				if (object != null)
					ServiceLocator.instance().getLuceneIndexService().indexObject(objectClass, object);
			}
		} catch (InternalErrorException e) {
			throw e;
		} catch (Exception e) {
			throw new InternalErrorException("Error tracking account changes", e);
		}
	}

	private Object search(String objectClass, long id) throws InternalErrorException {
		if (objectClass.equals(User.class.getName()))
			return ServiceLocator.instance().getUserService().findUserByUserId(id);
		if (objectClass.equals(Group.class.getName()))
			return ServiceLocator.instance().getGroupService().findGroupById(id);
		if (objectClass.equals(Application.class.getName())) {
			for (Application app: ServiceLocator.instance().getApplicationService().findApplicationByJsonQuery("id eq "+id)) {
				return app;
			}
			return null;
		}
		if (objectClass.equals(MailList.class.getName())) {
			for (MailList app: ServiceLocator.instance().getMailListsService().findMailListByJsonQuery("id eq "+id)) {
				return app;
			}
			return null;
		}
		if (objectClass.equals(Role.class.getName()))
			return ServiceLocator.instance().getApplicationService().findRoleById(id);
		if (objectClass.equals(Host.class.getName())) {
			return ServiceLocator.instance().getNetworkService().findHostById(id);
		}
		for (CustomObject app: ServiceLocator.instance().getCustomObjectService().findCustomObjectByJsonQuery(objectClass, "id eq "+id)) {
			return app;
		}
		return null;
	}

	@Override
	public void init() throws Exception {
		super.init();
	}

	@Override
	public void updateListAlias(MailList list) throws InternalErrorException {
		index(MailList.class.getName(), list.getId());
	}

	@Override
	public void updateGroup(Group group) throws RemoteException, InternalErrorException {
		super.updateGroup(group);
		index (Group.class.getName(), group.getId());
	}

	@Override
	public void updateHost(Host host) throws RemoteException, InternalErrorException {
		super.updateHost(host);
		index (Host.class.getName(), host.getId());
	}

	@Override
	public void updateUser(Account account, User user) throws RemoteException, InternalErrorException {
		super.updateUser(account, user);
		index(User.class.getName(), user.getId());
	}
}

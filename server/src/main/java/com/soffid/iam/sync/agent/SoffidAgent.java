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

public class SoffidAgent extends Agent implements IndexMgr, UserMgr, ExtensibleObjectMgr, MailAliasMgr, GroupMgr, HostMgr {

	private ObjectTranslator objectTranslator;
	private Collection<ExtensibleObjectMapping> objectMappings;

	@Override
	public void updateUser(Account account, User user) throws RemoteException, InternalErrorException {
		UserExtensibleObject aeo = new UserExtensibleObject(account, user, getServer());
		processAccountObject(account, aeo);
	}

	private void processAccountObject(Account account, ExtensibleObject aeo) throws InternalErrorException {
		try {
			for (ExtensibleObject target: objectTranslator.generateObjects(aeo).getObjects()) {
				DeltaChangesManager dcm = new DeltaChangesManager(log, this);
				ExtensibleObject previ = dcm.getPreviousObject(account);
				if (previ == null) {
					runTriggers(target.getObjectType(), SoffidObjectTrigger.PRE_INSERT, null, target, aeo);
					dcm.updateDeltaAttribute(account, target);
					runTriggers(target.getObjectType(), SoffidObjectTrigger.POST_INSERT, null, target, aeo);
				} else {
					runTriggers(target.getObjectType(), SoffidObjectTrigger.PRE_UPDATE, previ, target, aeo);
					dcm.updateDeltaAttribute(account, target);
					runTriggers(target.getObjectType(), SoffidObjectTrigger.POST_UPDATE, previ, target, aeo);
				}
				dcm.apply(account, getServer().getAccountRoles(account.getName(), account.getSystem()), getServer(), true,	true);
			}
		} catch (InternalErrorException e) {
			throw e;
		} catch (Exception e) {
			throw new InternalErrorException("Error tracking account changes", e);
		}
	}

	@Override
	public void updateUser(Account account) throws RemoteException, InternalErrorException {
		AccountExtensibleObject aeo = new AccountExtensibleObject(account, getServer());
		processAccountObject(account, aeo);
	}

	@Override
	public void removeUser(String userName) throws RemoteException, InternalErrorException {
		try {
			Account acc = getServer().getAccountInfo(userName, getAgentName());
			if (acc != null) {
				DeltaChangesManager dcm = new DeltaChangesManager(log, this);
				ExtensibleObject old = dcm.getPreviousObject(acc);
				if (old != null) {
					AccountExtensibleObject aeo = new AccountExtensibleObject(acc, getServer());
					for (ExtensibleObject target: objectTranslator.generateObjects(aeo).getObjects()) {
						runTriggers(target.getObjectType(), SoffidObjectTrigger.PRE_DELETE, old, target, aeo);
						acc.getAttributes().remove(DeltaChangesManager.STATUS_ATTRIBUTE);
						new RemoteServiceLocator().getAccountService().updateAccount(acc);
						runTriggers(target.getObjectType(), SoffidObjectTrigger.POST_DELETE, old, target, aeo);
					}
				}
			}
		} catch (InternalErrorException e) {
			throw e;
		} catch (Exception e) {
			throw new InternalErrorException("Error tracking account changes", e);
		}
	}

	@Override
	public void updateUserPassword(String userName, User userData, Password password, boolean mustchange)
			throws RemoteException, InternalErrorException {
	}

	@Override
	public boolean validateUserPassword(String userName, Password password)
			throws RemoteException, InternalErrorException {
		return false;
	}

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
	public void configureMappings(Collection<ExtensibleObjectMapping> objects)
			throws RemoteException, InternalErrorException {
		objectTranslator = new ObjectTranslator(getSystem(), getServer(), objects);
		objectMappings = objects;
	}

	@Override
	public ExtensibleObject getNativeObject(SoffidObjectType type, String object1, String object2)
			throws RemoteException, InternalErrorException {
		return null;
	}

	@Override
	public ExtensibleObject getSoffidObject(SoffidObjectType type, String object1, String object2)
			throws RemoteException, InternalErrorException {
		return null;
	}

	@Override
	public void updateExtensibleObject(ExtensibleObject obj) throws RemoteException, InternalErrorException {
	}

	@Override
	public void removeExtensibleObject(ExtensibleObject obj) throws RemoteException, InternalErrorException {
	}

	private boolean runTriggers(String objectType, SoffidObjectTrigger triggerType, 
			ExtensibleObject existing, 
			ExtensibleObject obj,
			ExtensibleObject src) throws InternalErrorException {
		return runTriggers(objectType, triggerType.toString(), existing, obj, src) ;
	}
	public boolean runTriggers(String objectType, String triggerType, 
			ExtensibleObject existing, 
			ExtensibleObject obj,
			ExtensibleObject src) throws InternalErrorException {
		List<ObjectMappingTrigger> triggers = getTriggers (objectType, triggerType);
		for (ObjectMappingTrigger trigger: triggers)
		{
	
			ExtensibleObject eo = new ExtensibleObject();
			eo.setAttribute("source", src);
			eo.setAttribute("newObject", obj);
			eo.setAttribute("oldObject", existing);
			if ( ! objectTranslator.evalExpression(eo, trigger.getScript()) )
			{
				log.info("Trigger "+trigger.getTrigger().toString()+" returned false");
				if (isDebug())
				{
					if (existing != null)
						debugObject("old object", existing, "  ");
					if (obj != null)
						debugObject("new object", obj, "  ");
				}
				return false;
			}
		}
		return true;
	}

	private List<ObjectMappingTrigger> getTriggers(String objectType, String type) {
		List<ObjectMappingTrigger> triggers = new LinkedList<ObjectMappingTrigger>();
		for ( ExtensibleObjectMapping objectMapping: objectMappings)
		{
			if (objectMapping.getSystemObject().equals(objectType))
			{
				for ( ObjectMappingTrigger trigger: objectMapping.getTriggers())
				{
					if (trigger.getTrigger().toString().equals(type))
						triggers.add(trigger);
				}
			}
		}
		return triggers;
	}

	void debugObject (String msg, Map<String,Object> obj, String indent)
	{
		if (isDebug())
		{
			if (indent == null)
				indent = "";
			if (msg != null)
				log.info(indent + msg);
			for (String attribute: obj.keySet())
			{
				Object subObj = obj.get(attribute);
				if (subObj == null)
				{
					log.info (indent+attribute.toString()+": null");
				}
				else if (subObj instanceof Map)
				{
					log.info (indent+attribute.toString()+": Object {");
					debugObject (null, (Map<String, Object>) subObj, indent + "   ");
					log.info (indent+"}");
				}
				else
				{
					log.info (indent+attribute.toString()+": "+subObj.toString());
				}
			}
		}
	}

	@Override
	public void init() throws Exception {
		super.init();
		try {
			final AdditionalDataService additionalDataService = new RemoteServiceLocator().getAdditionalDataService();
			if (additionalDataService.findSystemDataType(getSystem().getName(), DeltaChangesManager.STATUS_ATTRIBUTE) == null) {
				DataType dt = new DataType();
				dt.setCode(DeltaChangesManager.STATUS_ATTRIBUTE);
				dt.setLabel("Previous state data");
				dt.setBuiltin(false);
				dt.setVisibilityExpression("false");
				dt.setType(TypeEnumeration.BINARY_TYPE);
				dt.setUnique(false);
				dt.setRequired(false);
				dt.setSystemName(getSystem().getName());
				additionalDataService.create(dt);
			}
		} catch (Exception e) {
			throw new InternalErrorException("Error configuring metadata", e);
		}
	}

	protected ObjectTranslator getObjectTranslator() {
		return objectTranslator;
	}

	protected Collection<ExtensibleObjectMapping> getObjectMappings() {
		return objectMappings;
	}

	@Override
	public void updateUserAlias(String alias, User user) throws InternalErrorException {
	}

	@Override
	public void removeUserAlias(String alias) throws InternalErrorException {
	}

	@Override
	public void updateListAlias(MailList list) throws InternalErrorException {
		index(MailList.class.getName(), list.getId());
	}

	@Override
	public void removeListAlias(String alias) throws InternalErrorException {
	}

	@Override
	public void updateGroup(Group group) throws RemoteException, InternalErrorException {
		index (Group.class.getName(), group.getId());
	}

	@Override
	public void removeGroup(String group) throws RemoteException, InternalErrorException {
	}

	@Override
	public void updateHost(Host host) throws RemoteException, InternalErrorException {
		index (Host.class.getName(), host.getId());
	}

	@Override
	public void removeHost(String name) throws RemoteException, InternalErrorException {
	}
}

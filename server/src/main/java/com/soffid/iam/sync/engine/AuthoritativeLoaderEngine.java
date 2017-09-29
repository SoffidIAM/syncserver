package com.soffid.iam.sync.engine;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4jFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Configuration;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.ReconcileTrigger;
import com.soffid.iam.api.ScheduledTask;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.authoritative.service.AuthoritativeChangeService;
import com.soffid.iam.service.ConfigurationService;
import com.soffid.iam.service.CustomObjectService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.GroupService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.extobj.AccountExtensibleObject;
import com.soffid.iam.sync.engine.extobj.CustomExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GroupExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.UserExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ValueObjectMapper;
import com.soffid.iam.sync.intf.AuthoritativeChange;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;

public class AuthoritativeLoaderEngine {
	Log log;
	
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> preInsertTrigger;
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> postInsertTrigger;
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> preDeleteTrigger;
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> postDeleteTrigger;
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> preUpdateTrigger;
	private com.soffid.iam.api.System system;
	DispatcherHandlerImpl handler;
	private DispatcherService dispatcherService;

	private UserService userService;

	private GroupService groupService;

	private CustomObjectService objectService;

	private ServerService server;

	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> postUpdateTrigger;

	private AuthoritativeChangeService authoritativeService;

	private HashSet<String> userNames;

	private HashSet<String> groupNames;

	private HashMap<String, Set<String>> objectNames;

	
	public AuthoritativeLoaderEngine(DispatcherHandlerImpl handler)
	{
		this.handler = handler;
		system = handler.system;
		dispatcherService = ServiceLocator.instance().getDispatcherService();
		userService = ServiceLocator.instance().getUserService();
		groupService = ServiceLocator.instance().getGroupService();
		objectService = ServiceLocator.instance().getCustomObjectService();
        server = ServerServiceLocator.instance().getServerService();
        authoritativeService = ServerServiceLocator.instance().getAuthoritativeChangeService();
		
		log = LogFactory.getLog(system.getName());
	}
	/**
	 * @throws Exception 
	 * 
	 */
	public void doAuthoritativeImport (ScheduledTask task, PrintWriter out) 
	{

		try {
			userNames = new HashSet<String>();
			groupNames = new HashSet<String>();
			objectNames = new HashMap<String,Set<String>>();
			
			preInsertTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			postInsertTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			preDeleteTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			postDeleteTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			preUpdateTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			postUpdateTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> postUpdateTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			for (ReconcileTrigger trigger: dispatcherService.findReconcileTriggersByDispatcher(system.getId()))
			{
				SoffidObjectType type = trigger.getObjectType();
				Map<SoffidObjectType, LinkedList<ReconcileTrigger>> map = null;
				
				if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_INSERT))
					map = preInsertTrigger;
				else if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_UPDATE))
					map = preUpdateTrigger;
				else if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_INSERT))
					map = postInsertTrigger;
				else if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_UPDATE))
					map = postUpdateTrigger;
				else if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_DELETE))
					map = preDeleteTrigger;
				else if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_DELETE))
					map = postDeleteTrigger;

				if ( map != null)
				{
					LinkedList<ReconcileTrigger> list = map.get(type);
					if (list == null)
					{
						list = new LinkedList<ReconcileTrigger>();
						map.put(type, list);
					}
					list.add(trigger);
				}
			}
			ObjectTranslator objectTranslator = new ObjectTranslator (system);
			ValueObjectMapper vom = new ValueObjectMapper();

    		Object agent = handler.connect(false);
    	
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source = InterfaceWrapper.getAuthoritativeIdentitySource(agent);
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource2 source2 = InterfaceWrapper.getAuthoritativeIdentitySource2(agent);
    		if (source != null)
    		{
    			Collection<com.soffid.iam.sync.intf.AuthoritativeChange> changes = source.getChanges();
    			if ( changes != null && !changes.isEmpty())
    			{
	    	        for (com.soffid.iam.sync.intf.AuthoritativeChange change: changes)
	    	        {
	    	        	processChange(change, source, out, objectTranslator, vom);
	    	        }
	    			changes = source.getChanges();
    			}
    		} else if (source2 != null)
    		{
    			String lastId = null;
    			Configuration cfg = null;
    			String cfgId = null;
    			ConfigurationService cfgSvc = null;
    			if ( ! system.isFullReconciliation())
    			{
	    			cfgSvc = ServerServiceLocator.instance().getConfigurationService();
	    			cfgId = "soffid.sync.authoritative.change."+getSystem().getName();
	    			cfg = cfgSvc.findParameterByNameAndNetworkName(cfgId, null);
	    			if (cfg != null)
	    				lastId = cfg.getValue();
    			}
				boolean anyError = false;
				boolean moreData;
				do
				{
					Collection<com.soffid.iam.sync.intf.AuthoritativeChange> changes;
					changes = source2.getChanges(lastId);
					if (changes == null || changes.isEmpty())
						break;
	    	        for (com.soffid.iam.sync.intf.AuthoritativeChange change: changes)
	    	        {
	    	        	if ( processChange(change, null, out, objectTranslator, vom) )
	    	        		anyError = true;
	    	        }
   				} while (source2.hasMoreData());
				
				if (! anyError && system.isFullReconciliation())
				{
					removeUsers (out, objectTranslator, vom);
					removeGroups(out, objectTranslator, vom);
					removeCustomObjects(out, objectTranslator, vom);
				}
				String nextChange = source2.getNextChange() ;
				if (! anyError && nextChange != null && ! system.isFullReconciliation())
				{
					if (cfg == null) {
						cfg = new Configuration();
						cfg.setValue(nextChange);
						cfg.setCode(cfgId);
						cfg.setDescription("Last authoritative change id loaded");
						cfgSvc.create(cfg);
					} else {
						cfg.setValue(nextChange);
						cfgSvc.update(cfg);
					}
				}
    		} else {
    			out.println("This agent does not support account reconciliation");
    		}
		} 
		catch (Exception e)
		{
			log.info("Error performing authoritative data load process", e);
			out.println("*************");
			out.println ("**  ERROR **");
			out.println ("*************");
			out.println (e.toString());
			out.println();
			out.println();
			out.println("Stack trace:");
			SoffidStackTrace.printStackTrace(e, out);
			task.setError(true);
		}
		
	}

	private void removeUsers(PrintWriter out, ObjectTranslator objectTranslator, ValueObjectMapper vom) throws InternalErrorException {
		
		if (userNames.isEmpty())
			return;
		
		HashSet<String> names = new HashSet<String>(userService.findUserNames());
		for (String un: userNames)
		{
			names.remove(un);
		}

		List<ReconcileTrigger> preDelete = preDeleteTrigger.get(SoffidObjectType.OBJECT_USER);
		List<ReconcileTrigger> postDelete = postDeleteTrigger.get(SoffidObjectType.OBJECT_USER);
		for (String userName: names)
		{
			User user = userService.findUserByUserName(userName);
			if (user != null && user.getActive().booleanValue())
			{
				boolean ok = true;
				user.setActive(false);
				UserExtensibleObject eo = new UserExtensibleObject(new Account(), user, server);
				if (preDelete != null && !preDelete.isEmpty())
				{
					ok = executeTriggers(preDelete, eo, null, objectTranslator);
					user = vom.parseUser(eo);
				}
				if (ok)
				{
					out.println("Disabling user "+userName+"\n");
					try {
						AuthoritativeChange change = new AuthoritativeChange();
						change.setObjectType(SoffidObjectType.OBJECT_USER);
						change.setSourceSystem(system.getName());
						change.setUser(user);
						if (authoritativeService
								.startAuthoritativeChange(change))
						{
							out.append(
									"Applied authoritative disable for  ")
									.append(user.getUserName())
									.append("\n");
							log.info(
									"Applied authoritative disable for  object "+user.getUserName());
						}
						else
						{
							out.append(
									"Prepared authoritative disable for  ")
									.append(user.getUserName())
									.append("\n");
							log.info(
									"Prepared authoritative disable for  object "+user.getUserName());
						}

						executeTriggers(postDelete, eo, null, objectTranslator);
					} catch (Exception e) {
						out.println("Error: "+e.toString());
					}
				} else {
					out.println("Pre-delete trigger rejected change for "+userName);
				}
			}
		}
	}
	
	private void removeGroups(PrintWriter out, ObjectTranslator objectTranslator, ValueObjectMapper vom) throws InternalErrorException {
		if (groupNames.isEmpty())
			return;

		HashSet<String> names = new HashSet<String>(groupService.findGroupNames());
		for (String un: groupNames)
		{
			names.remove(un);
		}

		List<ReconcileTrigger> preDelete = preDeleteTrigger.get(SoffidObjectType.OBJECT_GROUP);
		List<ReconcileTrigger> postDelete = postDeleteTrigger.get(SoffidObjectType.OBJECT_GROUP);
		for (String groupName: names)
		{
			Group group = groupService.findGroupByGroupName(groupName);
			if (group != null && ( group.getObsolete() == null || group.getObsolete().equals(Boolean.FALSE)))
			{
				boolean ok = true;
				group.setObsolete(true);
				GroupExtensibleObject eo = new GroupExtensibleObject(group, system.getName(), server);
				if (preDelete != null && !preDelete.isEmpty())
				{
					ok = executeTriggers(preDelete, eo, null, objectTranslator);
					group = vom.parseGroup(eo);
				}
							
				if (ok)
				{
					out.println("Disabling group "+groupName+"\n");
					try {
						AuthoritativeChange change = new AuthoritativeChange();
						change.setObjectType(SoffidObjectType.OBJECT_GROUP);
						change.setGroup(group);
						if (authoritativeService
								.startAuthoritativeChange(change))
						{
							out.append(
									"Applied authoritative disable for  ")
									.append(change.getGroup().getName())
									.append("\n");
							log.info(
									"Applied authoritative disable for  object "+change.getGroup().getName());
						}
						else
						{
							out.append(
									"Prepared authoritative disable for  ")
									.append(change.getGroup().getName())
									.append("\n");
							log.info(
									"Prepared authoritative disable for  object "+change.getGroup().getName());
						}

						executeTriggers(postDelete, eo, null, objectTranslator);
					} catch (Exception e) {
						out.println("Error: "+e.toString());
					}
				} else {
					out.println("Pre-delete trigger rejected change for group "+groupName);
				}
			}
		}
	}
	
	
	private void removeCustomObjects(PrintWriter out, ObjectTranslator objectTranslator, ValueObjectMapper vom) throws InternalErrorException {
		for (String ot: objectNames.keySet())
		{
			removeCustomObjects(ot, out, objectTranslator);
		}

	}

	private void removeCustomObjects(String objectType, 
			PrintWriter out, ObjectTranslator objectTranslator) 
					throws InternalErrorException {

		Set<String> names = objectNames.get(objectType);
		Set<String> existing = new HashSet<String>(objectService.findCustomObjectNames(objectType));
		for (String un: names)
		{
			existing.remove(un);
		}

		List<ReconcileTrigger> preDelete = preDeleteTrigger.get(SoffidObjectType.OBJECT_CUSTOM);
		List<ReconcileTrigger> postDelete = postDeleteTrigger.get(SoffidObjectType.OBJECT_CUSTOM);
		for (String objectName: existing)
		{
			CustomObject object = objectService.findCustomObjectByTypeAndName(objectType, objectName);
			if (object != null)
			{
				boolean ok = true;
				CustomExtensibleObject eo = new CustomExtensibleObject(object, server);
				if (preDelete != null && !preDelete.isEmpty())
					ok = executeTriggers(preDelete, eo, null, objectTranslator);
					
				if (ok)
				{
					out.println("Removing custom object "+objectType+" "+objectName+"\n");
					try {
						objectService.deleteCustomObject(object);
						executeTriggers(postDelete, eo, null, objectTranslator);
					} catch (Exception e) {
						out.println("Error: "+e.toString());
					}
				} else {
					out.println("Pre-delete trigger rejected change for "+objectType+" "+objectName);
				}
			}
		}
	}

	private boolean processChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, PrintWriter out,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		if (change.getUser() != null)
			return processUserChange(change, source, out, objectTranslator, vom);
		else if (change.getGroup() != null)
			return processGroupChange(change, source, out, objectTranslator, vom);
		else if (change.getObject() != null)
			return processObjectChange(change, source, out, objectTranslator, vom);
		else
			return false;
	}

	private boolean processUserChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, PrintWriter out,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getSystem().getName());
		try {
			if (system.isFullReconciliation() && change.getUser() != null &&
					change.getUser().getUserName() != null)
			{
				userNames.add(change.getUser().getUserName());
			}
			User previousUser = change.getUser() == null ||
					change.getUser().getUserName() == null ? null :
						userService.findUserByUserName(change.getUser().getUserName());
			boolean ok = true;
			if (previousUser == null)
			{
				LinkedList<ReconcileTrigger> pi = preInsertTrigger.get(SoffidObjectType.OBJECT_USER);
				if (pi != null && ! pi.isEmpty())
				{
					UserExtensibleObject eo = buildExtensibleObject(change);
					eo.setAttribute("attributes", change.getAttributes());
					if (executeTriggers(pi, null, eo, objectTranslator))
					{
						change.setUser( vom.parseUser(eo));
						change.setAttributes((Map<String, Object>) eo.getAttribute("attributes"));
					}
					else
					{
						out.append("Change to user "+change.getUser().getUserName()+" is rejected by pre-insert trigger\n");
						log.info("Change to user "+change.getUser().getUserName()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				LinkedList<ReconcileTrigger> pu = preUpdateTrigger.get(SoffidObjectType.OBJECT_USER);
				if (pu != null && ! pu.isEmpty())
				{
					UserExtensibleObject eo = buildExtensibleObject(change);
					if (executeTriggers(pu, 
							new UserExtensibleObject(new Account (), previousUser, server), 
							eo, objectTranslator))
					{
						change.setUser( vom.parseUser(eo));
						change.setAttributes((Map<String, Object>) eo.getAttribute("attributes"));
					}
					else
					{
						out.append("Change to user "+change.getUser().getUserName()+" is rejected by pre-update trigger\n");
						log.info("Change to user "+change.getUser().getUserName()+" is rejected by pre-update trigger");
						ok = false;
					}
				}
			}
			if (ok)
			{
				if (authoritativeService
						.startAuthoritativeChange(change))
				{
					out.append(
							"Applied authoritative change for  ")
							.append(change.getUser().getUserName())
							.append("\n");
					log.info(
							"Applied authoritative change for  "+change.getUser().getUserName());
				}
				else
				{
					log.info("Prepared authoritative change for  "+change.getUser().getUserName());
					out.append(
							"Prepared authoritative change for  ")
							.append(change.getUser().getUserName())
							.append("\n");
				}

				if (previousUser == null)
				{
					LinkedList<ReconcileTrigger> pi = postInsertTrigger.get(SoffidObjectType.OBJECT_USER);
					if (pi != null && ! pi.isEmpty())
					{
						UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
						executeTriggers(pi, null, eo, objectTranslator);
					}
				} else {
					LinkedList<ReconcileTrigger> pu = postUpdateTrigger.get(SoffidObjectType.OBJECT_USER);
					if (pu != null && ! pu.isEmpty())
					{
						UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
						executeTriggers(pu, 
								new UserExtensibleObject(new Account (), previousUser, server), 
								eo, objectTranslator);
					}
				}
				if (source != null)
					source.commitChange(change.getId());
			}
		} catch ( Exception e) {
			error = true;
			log.info("Error uploading change "+change.getId().toString(), e);
			log.info("User information: "+change.getUser().toString());
			log.info("Exception: "+e.toString());
			out.println ("Error uploading change ");
			out.print(change.getId().toString());
			out.print(":");
			e.printStackTrace (out);
			out.print("User information: ");
			out.println(change.getUser());
		}
		return error;
	}

	private boolean processGroupChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, PrintWriter out,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getSystem().getName());
		try {
			if (system.isFullReconciliation() && change.getGroup() != null &&
					change.getGroup().getName() != null)
			{
				groupNames.add(change.getGroup().getName());
			}
			Group previousGroup = change.getGroup() == null ||
					change.getGroup().getName() == null ? null :
						groupService.findGroupByGroupName(change.getGroup().getName());
			boolean ok = true;
			if (previousGroup == null)
			{
				LinkedList<ReconcileTrigger> pi = preInsertTrigger.get(SoffidObjectType.OBJECT_GROUP);
				if (pi != null && ! pi.isEmpty())
				{
					GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
							getSystem().getName(),
							server);
					if (executeTriggers(pi, null, eo, objectTranslator))
					{
						change.setGroup( vom.parseGroup(eo));
					}
					else
					{
						out.append("Change to group "+change.getGroup().getName()+" is rejected by pre-insert trigger\n");
						log.info("Change to group "+change.getGroup().getName()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				LinkedList<ReconcileTrigger> pu = preUpdateTrigger.get(SoffidObjectType.OBJECT_GROUP);
				if (pu != null && ! pu.isEmpty())
				{
					GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
							getSystem().getName(),
							server);
					if (executeTriggers(pu, 
							new GroupExtensibleObject(previousGroup, getSystem().getName(), server), 
							eo, objectTranslator))
					{
						change.setGroup(vom.parseGroup(eo));
					}
					else
					{
						out.append("Change to group "+change.getGroup().getName()+" is rejected by pre-update trigger\n");
						log.info("Change to group "+change.getGroup().getName()+" is rejected by pre-update trigger");
						ok = false;
					}
				}
			}
			if (ok)
			{
				if (authoritativeService
						.startAuthoritativeChange(change))
				{
					out.append(
							"Applied authoritative change for  ")
							.append(change.getGroup().getName())
							.append("\n");
					log.info(
							"Applied authoritative change for  "+change.getGroup().getName());
				}
				else
				{
					log.info("Prepared authoritative change for  "+change.getGroup().getName());
					out.append(
							"Prepared authoritative change for  ")
							.append(change.getGroup().getName())
							.append("\n");
				}

				if (previousGroup == null)
				{
					LinkedList<ReconcileTrigger> pi = postInsertTrigger.get(SoffidObjectType.OBJECT_GROUP);
					if (pi != null && ! pi.isEmpty())
					{
						GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
								getSystem().getName(),
								server);
						executeTriggers(pi, null, eo, objectTranslator);
					}
				} else {
					LinkedList<ReconcileTrigger> pu = postUpdateTrigger.get(SoffidObjectType.OBJECT_GROUP);
					if (pu != null && ! pu.isEmpty())
					{
						GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
								getSystem().getName(),
								server);
						GroupExtensibleObject old = new GroupExtensibleObject(previousGroup,
								getSystem().getName(),
								server);
						executeTriggers(pu, 
								old, 
								eo, objectTranslator);
					}
				}
				if (source != null)
					source.commitChange(change.getId());
			}
		} catch ( Exception e) {
			error = true;
			log.info("Error uploading change "+change.getId().toString(), e);
			log.info("Group information: "+change.getGroup().toString());
			log.info("Exception: "+e.toString());
			out.append ("Error uploading change ")
				.append(change.getId().toString())
				.append(":");
			StringWriter sw = new StringWriter();
			e.printStackTrace (new PrintWriter(sw));
			out.println(sw.getBuffer());
			out.append("Group information: ");
			out.println(change.getGroup());
		}
		return error;
	}

	private boolean processObjectChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, PrintWriter out,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getSystem().getName());
		try {
			if (system.isFullReconciliation() && change.getObject() != null &&
					change.getObject().getName() != null &&
					change.getObject().getType() != null)
			{
				Set<String> set = objectNames.get(change.getObject().getType());
				if (set == null)
				{
					set = new HashSet<String>();
					objectNames.put(change.getObject().getType(), set);
				}
				set.add(change.getObject().getName());
			}

			CustomObject previousObject = change.getObject() == null ||
					change.getObject().getName() == null ? null :
						objectService.findCustomObjectByTypeAndName(change.getObject().getType(),
								change.getObject().getName());
			boolean ok = true;
			if (previousObject == null)
			{
				LinkedList<ReconcileTrigger> pi = preInsertTrigger.get(SoffidObjectType.OBJECT_CUSTOM);
				if (pi != null && ! pi.isEmpty())
				{
					CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
							server);
					if (executeTriggers(pi, null, eo, objectTranslator))
					{
						change.setGroup( vom.parseGroup(eo));
					}
					else
					{
						out.append("Change to object "+change.getObject().getType()+" "+
								change.getObject().getName()+" is rejected by pre-insert trigger\n");
						log.info("Change to object "+change.getObject().getType()+" "+
								change.getObject().getName()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				LinkedList<ReconcileTrigger> pu = preUpdateTrigger.get(SoffidObjectType.OBJECT_CUSTOM);
				if (pu != null && ! pu.isEmpty())
				{
					CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
							server);
					if (executeTriggers(pu, 
							new CustomExtensibleObject(previousObject, server),
							eo, objectTranslator))
					{
						change.setObject( vom.parseCustomObject (eo));
					}
					else
					{
						out.append("Change to object "+change.getObject().getType()+" "+
								change.getObject().getName()+" is rejected by pre-update trigger\n");
						log.info("Change to object "+change.getObject().getType()+" "+
								change.getObject().getName()+" is rejected by pre-update trigger");
						ok = false;
					}
				}
			}
			if (ok)
			{
				if (authoritativeService
						.startAuthoritativeChange(change))
				{
					out.append(
							"Applied authoritative change for  ")
							.append(change.getObject().getType())
							.append(" ")
							.append(change.getObject().getName())
							.append("\n");
					log.info(
							"Applied authoritative change for  object "+change.getObject().getType()+" "+
								change.getObject().getName());
				}
				else
				{
					log.info("Prepared authoritative change for object "+change.getObject().getType()+" "+
								change.getObject().getName());
					out.append(
							"Prepared authoritative change for  ")
							.append(change.getObject().getType())
							.append(" ")
							.append(change.getObject().getName())
							.append("\n");
				}

				if (previousObject == null)
				{
					LinkedList<ReconcileTrigger> pi = postInsertTrigger.get(SoffidObjectType.OBJECT_CUSTOM);
					if (pi != null && ! pi.isEmpty())
					{
						CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
								server);
						executeTriggers(pi, null, eo, objectTranslator);
					}
				} else {
					LinkedList<ReconcileTrigger> pu = postUpdateTrigger.get(SoffidObjectType.OBJECT_CUSTOM);
					if (pu != null && ! pu.isEmpty())
					{
						CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
								server);
						CustomExtensibleObject old = new CustomExtensibleObject(previousObject,
								server);
						executeTriggers(pu, 
								old, 
								eo, objectTranslator);
					}
				}
				if (source != null)
					source.commitChange(change.getId());
			}
		} catch ( Exception e) {
			error = true;
			if (change.getId() == null)
				log.info("Error uploading change ", e);
			else
				log.info("Error uploading change "+ change.getId().toString(), e);
			log.info("Group information: "+change.getObject().toString());
			log.info("Exception: "+e.toString());
			out.append ("Error uploading change ")
				.append(change.getId().toString())
				.append(":");
			StringWriter sw = new StringWriter();
			e.printStackTrace (new PrintWriter(sw));
			out.append(sw.getBuffer())
				.append ("\n");
			out.append("Object information: ");
			out.println(change.getObject());
		}
		return error;
	}

	private UserExtensibleObject buildExtensibleObject(
			AuthoritativeChange change) throws InternalErrorException {
		UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
		eo.setAttribute("attributes", change.getAttributes());
		List<ExtensibleObject> l = new LinkedList<ExtensibleObject>();
		if (change.getGroups() != null)
		{
			for (String s: change.getGroups())
			{
				Group g = null;
				
				try {
					g = server.getGroupInfo(s, system.getName());
				} catch (UnknownGroupException e) {
				}
				
				if (g == null)
				{
					ExtensibleObject eo2 = new ExtensibleObject();
					eo2.setAttribute("name", s);
					l.add(eo2);
				}
				else
				{
					l.add( new GroupExtensibleObject(g, system.getName(), server));
				}
			}
		}
		eo.setAttribute("secondaryGroups", l);
		return eo;
	}

	private boolean executeTriggers (List<ReconcileTrigger> triggerList, ExtensibleObject old, ExtensibleObject newObject, ObjectTranslator objectTranslator) throws InternalErrorException
	{
		if (triggerList == null )
			return true;
		
		ExtensibleObject eo = new ExtensibleObject ();
		eo.setAttribute("oldObject", old);
		eo.setAttribute("newObject", newObject);
		boolean ok = true;
		for (ReconcileTrigger t: triggerList)
		{
			if (! objectTranslator.evalExpression(eo, t.getScript()))
				ok = false;
		}
		
		return ok;
	}
	public com.soffid.iam.api.System getSystem() {
		return system;
	}
	public void setSystem(com.soffid.iam.api.System system) {
		this.system = system;
	}

}

package com.soffid.iam.sync.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
import com.soffid.iam.sync.engine.extobj.CustomExtensibleObject;
import com.soffid.iam.sync.engine.extobj.GroupExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ObjectTranslator;
import com.soffid.iam.sync.engine.extobj.UserExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ValueObjectMapper;
import com.soffid.iam.sync.intf.AuthoritativeChange;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.comu.SoffidObjectTrigger;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.SoffidStackTrace;
import es.caib.seycon.ng.exception.UnknownGroupException;

public class AuthoritativeLoaderEngine {
	Log log;
	
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> preInsertTrigger;
	private HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> postInsertTrigger;
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
	public void doAuthoritativeImport (ScheduledTask task) 
	{

		StringBuffer result = task.getLastLog();
		try {
			
			preInsertTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			postInsertTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			preUpdateTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			postUpdateTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>> postUpdateTrigger = new HashMap<SoffidObjectType, LinkedList<ReconcileTrigger>>();
			for (ReconcileTrigger trigger: dispatcherService.findReconcileTriggersByDispatcher(system.getId()))
			{
				SoffidObjectType type = trigger.getObjectType();
				Map<SoffidObjectType, LinkedList<ReconcileTrigger>> map = null;
				if (trigger.getObjectType().equals(SoffidObjectType.OBJECT_USER))
				{
					if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_INSERT))
						map = preInsertTrigger;
					else if (trigger.getTrigger().equals(SoffidObjectTrigger.PRE_UPDATE))
						map = preUpdateTrigger;
					else if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_INSERT))
						map = postInsertTrigger;
					else if (trigger.getTrigger().equals(SoffidObjectTrigger.POST_UPDATE))
						map = postUpdateTrigger;
				}
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

    		Object agent = handler.getRemoteAgent();
    	
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source = InterfaceWrapper.getAuthoritativeIdentitySource(agent);
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource2 source2 = InterfaceWrapper.getAuthoritativeIdentitySource2(agent);
    		if (source != null)
    		{
    			Collection<com.soffid.iam.sync.intf.AuthoritativeChange> changes = source.getChanges();
    			if ( changes != null && !changes.isEmpty())
    			{
	    	        for (com.soffid.iam.sync.intf.AuthoritativeChange change: changes)
	    	        {
	    	        	processChange(change, source, result, objectTranslator, vom);
	    	        }
	    			changes = source.getChanges();
    			}
    		} else if (source2 != null)
    		{
    			ConfigurationService cfgSvc = ServerServiceLocator.instance().getConfigurationService();
    			String lastId = null;
    			String cfgId = "soffid.sync.authoritative.change."+getSystem().getName();
    			Configuration cfg = cfgSvc.findParameterByNameAndNetworkName(cfgId, null);
    			if (cfg != null)
    				lastId = cfg.getValue();
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
	    	        	if ( processChange(change, null, result, objectTranslator, vom) )
	    	        		anyError = true;
	    	        }
   				} while (source2.hasMoreData());
				String nextChange = source2.getNextChange() ;
				if (! anyError && nextChange != null)
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
    			result.append ("This agent does not support account reconciliation");
    		}
		} 
		catch (Exception e)
		{
			log.info("Error performing authoritative data load process", e);
			result.append ("*************\n");
			result.append ("**  ERROR **\n");
			result.append ("*************\n");
			result.append (e.toString());
			result.append("\n\nStack trace:\n")
				.append(SoffidStackTrace.getStackTrace(e));
			if (result.length() > 32000)
			{
				result.replace(0, result.length()-32000, "** TRUNCATED FILE ***\n...");
			}
			task.setError(true);
		}
		
	}

	private boolean processChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, StringBuffer result,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		if (change.getUser() != null)
			return processUserChange(change, source, result, objectTranslator, vom);
		else if (change.getGroup() != null)
			return processGroupChange(change, source, result, objectTranslator, vom);
		else if (change.getObject() != null)
			return processObjectChange(change, source, result, objectTranslator, vom);
		else
			return false;
	}

	private boolean processUserChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, StringBuffer result,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getSystem().getName());
		try {
			User previousUser = change.getUser() == null ||
					change.getUser().getUserName() == null ? null :
						userService.findUserByUserName(change.getUser().getUserName());
			boolean ok = true;
			if (previousUser == null)
			{
				if (preInsertTrigger.get(SoffidObjectType.OBJECT_USER) != null )
				{
					UserExtensibleObject eo = buildExtensibleObject(change);
					eo.setAttribute("attributes", change.getAttributes());
					if (executeTriggers(preInsertTrigger.get(SoffidObjectType.OBJECT_USER), null, eo, objectTranslator))
					{
						change.setUser( vom.parseUser(eo));
						change.setAttributes((Map<String, Object>) eo.getAttribute("attributes"));
					}
					else
					{
						result.append("Change to user "+change.getUser().getUserName()+" is rejected by pre-insert trigger\n");
						log.info("Change to user "+change.getUser().getUserName()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				if (preUpdateTrigger.get(SoffidObjectType.OBJECT_USER) != null)
				{
					UserExtensibleObject eo = buildExtensibleObject(change);
					if (executeTriggers(preUpdateTrigger.get(SoffidObjectType.OBJECT_USER), 
							new UserExtensibleObject(new Account (), previousUser, server), 
							eo, objectTranslator))
					{
						change.setUser( vom.parseUser(eo));
						change.setAttributes((Map<String, Object>) eo.getAttribute("attributes"));
					}
					else
					{
						result.append("Change to user "+change.getUser().getUserName()+" is rejected by pre-update trigger\n");
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
					result.append(
							"Applied authoritative change for  ")
							.append(change.getUser().getUserName())
							.append("\n");
					log.info(
							"Applied authoritative change for  "+change.getUser().getUserName());
				}
				else
				{
					log.info("Prepared authoritative change for  "+change.getUser().getUserName());
					result.append(
							"Prepared authoritative change for  ")
							.append(change.getUser().getUserName())
							.append("\n");
				}

				if (previousUser == null)
				{
					if (! postInsertTrigger.isEmpty())
					{
						UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
						executeTriggers(postInsertTrigger.get(SoffidObjectType.OBJECT_USER), null, eo, objectTranslator);
					}
				} else {
					if (! postUpdateTrigger.isEmpty())
					{
						UserExtensibleObject eo = new UserExtensibleObject(new Account (), change.getUser(), server);
						executeTriggers(postUpdateTrigger.get(SoffidObjectType.OBJECT_USER), 
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
			result.append ("Error uploading change ")
				.append(change.getId().toString())
				.append(":");
			StringWriter sw = new StringWriter();
			e.printStackTrace (new PrintWriter(sw));
			result.append(sw.getBuffer())
				.append ("\n");
			result.append("User information: ").append(change.getUser()).append("\n");
		}
		return error;
	}

	private boolean processGroupChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, StringBuffer result,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getSystem().getName());
		try {
			Group previousGroup = change.getGroup() == null ||
					change.getGroup().getName() == null ? null :
						groupService.findGroupByGroupName(change.getGroup().getName());
			boolean ok = true;
			if (previousGroup == null)
			{
				if (preInsertTrigger.get(SoffidObjectType.OBJECT_GROUP) != null )
				{
					GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
							getSystem().getName(),
							server);
					if (executeTriggers(preInsertTrigger.get(SoffidObjectType.OBJECT_GROUP), null, eo, objectTranslator))
					{
						change.setGroup( vom.parseGroup(eo));
					}
					else
					{
						result.append("Change to group "+change.getGroup().getName()+" is rejected by pre-insert trigger\n");
						log.info("Change to group "+change.getGroup().getName()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				if (preUpdateTrigger.get(SoffidObjectType.OBJECT_GROUP) != null)
				{
					GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
							getSystem().getName(),
							server);
					if (executeTriggers(preUpdateTrigger.get(SoffidObjectType.OBJECT_GROUP), 
							new GroupExtensibleObject(previousGroup, getSystem().getName(), server), 
							eo, objectTranslator))
					{
						change.setGroup(vom.parseGroup(eo));
					}
					else
					{
						result.append("Change to group "+change.getGroup().getName()+" is rejected by pre-update trigger\n");
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
					result.append(
							"Applied authoritative change for  ")
							.append(change.getGroup().getName())
							.append("\n");
					log.info(
							"Applied authoritative change for  "+change.getGroup().getName());
				}
				else
				{
					log.info("Prepared authoritative change for  "+change.getGroup().getName());
					result.append(
							"Prepared authoritative change for  ")
							.append(change.getGroup().getName())
							.append("\n");
				}

				if (previousGroup == null)
				{
					if (postInsertTrigger.get(SoffidObjectType.OBJECT_GROUP) != null)
					{
						GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
								getSystem().getName(),
								server);
						executeTriggers(postInsertTrigger.get(SoffidObjectType.OBJECT_GROUP), null, eo, objectTranslator);
					}
				} else {
					if (postUpdateTrigger.containsKey(SoffidObjectType.OBJECT_GROUP))
					{
						GroupExtensibleObject eo = new GroupExtensibleObject(change.getGroup(),
								getSystem().getName(),
								server);
						GroupExtensibleObject old = new GroupExtensibleObject(previousGroup,
								getSystem().getName(),
								server);
						executeTriggers(postUpdateTrigger.get(SoffidObjectType.OBJECT_GROUP), 
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
			result.append ("Error uploading change ")
				.append(change.getId().toString())
				.append(":");
			StringWriter sw = new StringWriter();
			e.printStackTrace (new PrintWriter(sw));
			result.append(sw.getBuffer())
				.append ("\n");
			result.append("Group information: ").append(change.getGroup()).append("\n");
		}
		return error;
	}

	private boolean processObjectChange(com.soffid.iam.sync.intf.AuthoritativeChange change,
			com.soffid.iam.sync.intf.AuthoritativeIdentitySource source, StringBuffer result,
			ObjectTranslator objectTranslator, ValueObjectMapper vom) {
		boolean error = false;
		change.setSourceSystem(getSystem().getName());
		try {
			CustomObject previousGroup = change.getObject() == null ||
					change.getGroup().getName() == null ? null :
						objectService.findCustomObjectByTypeAndName(change.getObject().getType(),
								change.getObject().getName());
			boolean ok = true;
			if (previousGroup == null)
			{
				if (preInsertTrigger.get(SoffidObjectType.OBJECT_CUSTOM) != null )
				{
					CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
							server);
					if (executeTriggers(preInsertTrigger.get(SoffidObjectType.OBJECT_CUSTOM), null, eo, objectTranslator))
					{
						change.setGroup( vom.parseGroup(eo));
					}
					else
					{
						result.append("Change to object "+change.getObject().getType()+" "+
								change.getObject().getName()+" is rejected by pre-insert trigger\n");
						log.info("Change to object "+change.getObject().getType()+" "+
								change.getObject().getName()+" is rejected by pre-insert trigger");
						ok = false;
					}
				}
			} else {
				if (preUpdateTrigger.get(SoffidObjectType.OBJECT_CUSTOM) != null)
				{
					CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
							server);
					if (executeTriggers(preUpdateTrigger.get(SoffidObjectType.OBJECT_CUSTOM), 
							new CustomExtensibleObject(previousGroup, server),
							eo, objectTranslator))
					{
						change.setObject( vom.parseCustomObject (eo));
					}
					else
					{
						result.append("Change to object "+change.getObject().getType()+" "+
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
					result.append(
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
					result.append(
							"Prepared authoritative change for  ")
							.append(change.getObject().getType())
							.append(" ")
							.append(change.getObject().getName())
							.append("\n");
				}

				if (previousGroup == null)
				{
					if (postInsertTrigger.get(SoffidObjectType.OBJECT_CUSTOM) != null)
					{
						CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
								server);
						executeTriggers(postInsertTrigger.get(SoffidObjectType.OBJECT_CUSTOM), null, eo, objectTranslator);
					}
				} else {
					if (postUpdateTrigger.containsKey(SoffidObjectType.OBJECT_CUSTOM))
					{
						CustomExtensibleObject eo = new CustomExtensibleObject(change.getObject(),
								server);
						CustomExtensibleObject old = new CustomExtensibleObject(previousGroup,
								server);
						executeTriggers(postUpdateTrigger.get(SoffidObjectType.OBJECT_CUSTOM), 
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
			log.info("Group information: "+change.getObject().toString());
			log.info("Exception: "+e.toString());
			result.append ("Error uploading change ")
				.append(change.getId().toString())
				.append(":");
			StringWriter sw = new StringWriter();
			e.printStackTrace (new PrintWriter(sw));
			result.append(sw.getBuffer())
				.append ("\n");
			result.append("Object information: ").append(change.getObject()).append("\n");
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

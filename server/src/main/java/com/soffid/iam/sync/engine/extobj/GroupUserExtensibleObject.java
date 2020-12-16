/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Map;

import com.soffid.iam.api.Group;
import com.soffid.iam.api.GroupUser;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownUserException;

/**
 * @author bubu
 *
 */
public class GroupUserExtensibleObject extends es.caib.seycon.ng.sync.intf.ExtensibleObject
{
	GroupUser grup;
	ServerService serverService;
	private String dispatcher;
	public GroupUserExtensibleObject (GroupUser grup, String dispatcherName, ServerService serverService)
	{
		super();
		this.grup = grup;
		this.serverService = serverService;
		this.dispatcher = dispatcherName;
		setObjectType(SoffidObjectType.OBJECT_GRANTED_GROUP.getValue());
	}
	
	
	@Override
	public Object getAttribute (String attribute)
	{
		Object obj = super.getAttribute(attribute);
		try
		{
    		if (obj != null)
    			return obj;
    		
    		if ("id".equals(attribute))
    			obj = grup.getId();
    		else if ("userName".equals(attribute))
    			obj = grup.getUser();
    		else if ("groupName".equals(attribute))
    			obj = grup.getGroup();
    		else if ("user".equals(attribute)) {
    			if (grup.getUser() == null)
    				obj = null;
    			else {
	    			User user = serverService.getUserInfo(grup.getUser(), dispatcher);
	    			if (user == null)
	    				obj = null;
	    			else {
		    			Map<String, Object> atts = serverService.getUserAttributes(user.getId());
		    			obj = new UserExtensibleObject(user, atts, serverService);
	    			}
    			}
    		}
    		else if ("group".equals(attribute)) {
    			if (grup.getGroup() == null)
    				obj = null;
    			else {
	    			Group group = serverService.getGroupInfo(grup.getGroup(), dispatcher);
	    			if (group == null)
	    				obj = null;
	    			else {
		    			obj = new GroupExtensibleObject(group, dispatcher, serverService);
	    			}
    			}
    		}
    		else if ("attributes".equals(attribute))
    			obj = grup.getAttributes();
    		else
    			return null;
   			put (attribute, obj);
   			return obj;
		}
		catch (InternalErrorException e)
		{
			throw new RuntimeException (e);
		}
		catch (UnknownUserException e)
		{
			throw new RuntimeException (e);
		}
		catch (UnknownGroupException e)
		{
			throw new RuntimeException (e);
		}
	}


}

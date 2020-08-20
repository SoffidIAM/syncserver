package com.soffid.iam.sync.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.GroupUser;
import com.soffid.iam.api.User;
import com.soffid.iam.service.GroupService;
import com.soffid.iam.service.UserService;
import com.soffid.iam.sync.intf.AuthoritativeChange;

import es.caib.seycon.ng.exception.InternalErrorException;

public class ChangeDetector {
	UserService userService = ServiceLocator.instance().getUserService();
	GroupService groupService = ServiceLocator.instance().getGroupService();
	
	public boolean anyChange(AuthoritativeChange change) throws InternalErrorException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		if (change.getUser() != null && change.getUser().getUserName() != null && ! change.getUser().getUserName().trim().isEmpty())
		{
			if (anyUserChange(change))
				return true;
			if (change.getAttributes() != null)
				if (anyAttributesChange (change))
					return true;
						
			if (change.getGroups() != null)
				if (anyGroupChange (change))
					return true;
			return false;
		}
		else
			return true;
	}

	private boolean anyGroupChange(AuthoritativeChange change) throws InternalErrorException {
		Collection<GroupUser> grups = groupService.findUsersGroupByUserName(change.getUser().getUserName());
		
		Set<String> actualGroups = change.getGroups();
		
		// First remove
		for (Iterator<GroupUser> it = grups.iterator(); it.hasNext(); ) {
            GroupUser ug = it.next();
            if (actualGroups.contains(ug.getGroup())) {
                actualGroups.remove(ug.getGroup());
            } else {
            	return true;
            }
        }
		
		return ! actualGroups.isEmpty();
	}

	private boolean anyAttributesChange(AuthoritativeChange change) throws InternalErrorException {
		ObjectComparator comparator = new ObjectComparator();
		Map<String, Object> currentAtts = userService.findUserAttributes(change.getUser().getUserName());
		for (String att: change.getAttributes().keySet()) {
			Object o1 = change.getAttributes().get(att);
			Object o2 = currentAtts.get(att);
			if (o1 != null && o2 != null) {
				if (o1 == null || o2 == null)
					return true;
				if (o1 instanceof Collection && o2 instanceof Collection) {
					List<Object> l1 = new LinkedList<Object> ((Collection)o1);
					List<Object> l2 = new LinkedList<Object> ((Collection)o2);
					if (l1.size() != l2.size())
						return true;
					Collections.sort(l1, comparator);
					Collections.sort(l2, comparator);
					Iterator<Object> it1 = l1.iterator();
					Iterator<Object> it2 = l2.iterator();
					while (it1.hasNext() && it2.hasNext()) {
						if (comparator.compare(it1.next(), it2.next()) != 0)
							return true;
					}
				}
				else
				{
					if (comparator.compare(o1, o2) != 0)
						return true;
				}
			}
		}
		return false;
	}

	private boolean anyUserChange(AuthoritativeChange change) throws InternalErrorException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		User user = change.getUser();
		User oldUser = userService.findUserByUserName(user.getUserName());
		if (oldUser == null)
		{
			return true;
		} else {
			return compareUsers(user, oldUser);
		}
	}


	private boolean compareUsers(User user, User oldUser) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		boolean anyChange = false;
		for (String att : new String[]{"Active", "MailAlias", "PrimaryGroup", "Comments", "MailDomain", "MultiSession", 
				"NationalID", "LastName", "ShortName", "FirstName", "MiddleName", "MailServer", "ProfileServer", 
				"HomeServer", "PhoneNumber", "UserType"}) {
            Method getter = User.class.getMethod("get" + att);
            Method setter = User.class.getMethod("set" + att, getter.getReturnType());
            Object value = getter.invoke(user);
            if ("".equals(value)) value = null;
            if (value != null) {
                Object oldValue = getter.invoke(oldUser);
                if ("".equals(oldValue)) oldValue = null;
                if (oldValue == null || !oldValue.equals(value)) {
                    return true;
                }
            }
        }
		return false;
	}

	class ObjectComparator implements Comparator<Object> {
		public int compare(Object o1, Object o2) {
			if (o1 == null && o2 == null)
				return 0;
			if (o1 == null)
				return -1;
			if (o2 == null)
				return +1;
			if (o1 instanceof Calendar && o2 instanceof Calendar)
				return ((Calendar)o1).compareTo((Calendar)o2);
			if (o1 instanceof Date && o2 instanceof Date)
				return ((Date)o1).compareTo((Date)o2);
			return o1.toString().compareTo(o2.toString());
		}
		
	}
}


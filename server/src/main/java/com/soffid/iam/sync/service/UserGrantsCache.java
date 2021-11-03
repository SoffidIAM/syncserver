package com.soffid.iam.sync.service;

import java.util.Collection;

import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;

public class UserGrantsCache {
	User user;
	Collection<RoleGrant> grants;
	
	public UserGrantsCache(User user, Collection<RoleGrant> grants) {
		super();
		this.user = user;
		this.grants = grants;
	}
	static ThreadLocal<UserGrantsCache> local = new ThreadLocal<UserGrantsCache>();
	public static UserGrantsCache getGrantsCache() {
		return local.get();
		
	}
	public static void setGrantsCache(UserGrantsCache grants) {
		local.set(grants);
	}
	
	public static void clearGrantsCache() {
		local.remove();
	}
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	public void setGrants(Collection<RoleGrant> grants) {
		this.grants = grants;
	}
	public Collection<RoleGrant> getGrants() {
		return grants;
	}
}

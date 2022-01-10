package com.soffid.iam.sync.jetty;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;

import org.eclipse.jetty.server.UserIdentity;

public class SimpleIdentity implements UserIdentity {
    private String name;
	private String[] roles;

    public SimpleIdentity(String name, String[] roles) {
		this.name = name;
		this.roles = roles;
	}

	public String getName() {
        return name;
    }

	public Subject getSubject() {
		Set<Principal> principals = new HashSet<Principal>();
		principals.add(getUserPrincipal());
		Subject s = new Subject(true, principals , null, null);
		return s;
	}

	public Principal getUserPrincipal() {
		return new SimplePrincipal(name);
	}

	public boolean isUserInRole(String role, Scope scope) {
		for (String rr: roles) {
			if (rr.equals(role))
				return true;
		}
		return false;
	}

}

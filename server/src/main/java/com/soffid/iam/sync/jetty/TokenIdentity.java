package com.soffid.iam.sync.jetty;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;

import org.eclipse.jetty.server.UserIdentity;

public class TokenIdentity implements UserIdentity {
    private String name;

	public TokenIdentity() {
        super();
        this.name="SEU";
    }

    public TokenIdentity(String name) {
		this.name = name;
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
		return "SEU_CONSOLE".equals(role);
	}

}

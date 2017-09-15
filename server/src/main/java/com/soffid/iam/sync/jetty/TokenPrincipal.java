package com.soffid.iam.sync.jetty;

import java.security.Principal;

public class TokenPrincipal implements Principal {
    private String name;

	public TokenPrincipal() {
        super();
        this.name="SEU";
    }

    public TokenPrincipal(String name) {
		this.name = name;
	}

	public String getName() {
        return name;
    }

}

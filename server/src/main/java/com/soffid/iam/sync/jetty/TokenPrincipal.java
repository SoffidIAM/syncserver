package com.soffid.iam.sync.jetty;

import java.security.Principal;

public class TokenPrincipal implements Principal {
    public TokenPrincipal() {
        super();
    }

    public String getName() {
        return "SEU";
    }

}

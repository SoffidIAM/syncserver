package es.caib.seycon.ng.sync.jetty;

import java.security.Principal;

public class SimplePrincipal implements Principal {
    String name;
    
    public SimplePrincipal(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return name;
    }

}

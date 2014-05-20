package es.caib.seycon.ng.sync.engine.kerberos;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import es.caib.seycon.ng.comu.Password;

public class KerberosCallbackHandler implements CallbackHandler {

    private String user;
    private Password password;

    public KerberosCallbackHandler(String user, Password p) {
        this.user = user;
        this.password = p;
    }

    public void handle(Callback[] callbacks) throws IOException,
            UnsupportedCallbackException {
        for (int i = 0; i < callbacks.length; i++)
        {
              if (callbacks[i] instanceof NameCallback) {
                NameCallback nc = (NameCallback)callbacks[i];
                nc.setName(user);
              } else if (callbacks[i] instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback)callbacks[i];
                pc.setPassword(password.getPassword().toCharArray());
              } else {
                throw new UnsupportedCallbackException
                  (callbacks[i], "Unrecognized Callback");
              }
        }

    }

}

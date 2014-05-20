package es.caib.seycon.ng.sync.engine.session;

import es.caib.seycon.ng.comu.Sessio;

public class DuplicateSessionNotification extends Sessio {
    String message;
    boolean closeSession;
    public DuplicateSessionNotification(Sessio s) {
		super(s);
	}
    
	public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public boolean isCloseSession() {
        return closeSession;
    }
    public void setCloseSession(boolean closeSession) {
        this.closeSession = closeSession;
    }
}

package com.soffid.iam.sync.engine.session;

import com.soffid.iam.api.Session;

public class DuplicateSessionNotification extends Session {
    String message;
    boolean closeSession;
    public DuplicateSessionNotification(Session s) {
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

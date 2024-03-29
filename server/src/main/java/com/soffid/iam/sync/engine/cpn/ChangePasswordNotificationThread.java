package com.soffid.iam.sync.engine.cpn;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.ChangePasswordNotification;

import es.caib.seycon.ng.exception.InternalErrorException;
import com.soffid.iam.sync.service.ChangePasswordNotificationQueue;

public class ChangePasswordNotificationThread extends Thread {
    ChangePasswordNotificationQueue queue;

    public ChangePasswordNotificationThread() {
        super();
        this.queue = ServerServiceLocator.instance().getChangePasswordNotificationQueue();
    }

    @Override
    public void run() {
        ChangePasswordNotification n;
        try {
            try {
                n = queue.peekNotification();
                while ( n != null)
                {
                    queue.sendNotification(n);
                    n = queue.peekNotification();
                }
            } finally {
                queue.endNotificationThread();
            }
        } catch (InternalErrorException e) {
            Logger log = Log.getLogger("ChangePasswordNotificationThread");
            log.warn("Error notifying ", e);
        }
    }
    
    

}

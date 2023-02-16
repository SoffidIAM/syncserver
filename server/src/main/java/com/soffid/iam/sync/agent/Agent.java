/*
 * Agent.java
 *
 * Created on May 8, 2000, 10:44 AM
 */

package com.soffid.iam.sync.agent;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.CustomObject;
import com.soffid.iam.api.Group;
import com.soffid.iam.api.Role;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.engine.InterfaceWrapper;
import com.soffid.iam.sync.engine.extobj.AccountExtensibleObject;
import com.soffid.iam.sync.engine.extobj.CustomExtensibleObject;
import com.soffid.iam.sync.engine.extobj.ExtensibleObjectFatory;
import com.soffid.iam.sync.engine.extobj.ExtensibleObjectFinder;
import com.soffid.iam.sync.engine.extobj.GroupExtensibleObject;
import com.soffid.iam.sync.engine.extobj.RoleExtensibleObject;
import com.soffid.iam.sync.engine.extobj.UserExtensibleObject;
import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;

public abstract class Agent implements AgentInterface {
    transient private ServerService server;
    String agentVersion;
    String serverName;
    boolean debug = false;
    
    public String getServerName ()
	{
		return serverName;
	}

	public void setServerName (String serverName)
	{
		this.serverName = serverName;
	}

	public ServerService getServer() {
        return server;
    }

    public void init () throws Exception {
        
    }
    
        
    public String getAgentVersion ()
	{
		return agentVersion;
	}

	public void setAgentVersion (String agentVersion)
	{
		this.agentVersion = agentVersion;
	}

	public void setServer(ServerService server) {
        this.server = server;
    }

    public org.slf4j.Logger log = null;
    private JettyServer jettyServer;
    com.soffid.iam.api.System dispatcher;
	private Runnable onClose = null;

    public com.soffid.iam.api.System getSystem() {
        return dispatcher;
    }


    public void setSystem(com.soffid.iam.api.System dispatcher) {
        this.dispatcher = dispatcher;
    }


    public JettyServer getJettyServer() {
        return jettyServer;
    }
    

    public void setJettyServer(JettyServer jettyServer) {
        this.jettyServer = jettyServer;
    }

    /**
     * Constructor. Sólo puede ser creador por el AgentMgrImpl.
     * 
     * @exception java.rmi.RemoteException
     *                    Error de comunicaciones
     * @see AgentMgrImpl
     * @see AgentMgr
     */
    protected Agent() {
        log = LoggerFactory.getLogger(getClass());
    }


    public String getAgentName ()
    {
    	return getSystem().getName();
    }
    /**
     * Obtener los grupos y roles relativos al agente a los que pertenece el
     * usuario. Este método está para facilitar la implementación de los
     * agentes. En caso de ser un agente basado en roles y no disponer el
     * usuario de ningún role, el método retornara null.<BR>
     * En caso de ser un usuario de tipo "Externo", el sistema hará la misma
     * consideración, es decir, si no tiene roles asignados retornara null Enb
     * caso contrario, el sistema retornará un vector con los códigos de grupo y
     * los códigos de rol que dicho usuario tiene en el agente.
     * 
     * @param user
     *                Código del usuario
     * @return Vector con los códigos de grupo y role
     */
    public String[] getUserGroupsAndRoles(String account, User user)
            throws InternalErrorException, RemoteException,
            UnknownUserException {
        Collection<Group> groups = getServer().getUserGroups(user.getUserName(), null);
        Collection<RoleGrant> roles = getServer().getAccountRoles(account, getAgentName());

        String concat[] = new String[groups.size() + roles.size()];
        int i = 0;
        for (Iterator<Group> itGroup = groups.iterator(); itGroup.hasNext(); ) {
            concat[i++] = itGroup.next().getName();
        }
        for (Iterator<RoleGrant> itRole = roles.iterator(); itRole.hasNext(); ) {
            concat[i++] = itRole.next().getRoleName();
        }
        return concat;
    }
    
	public ExtensibleObject getExtensibleObject (SoffidObjectType type, String object1, String object2) throws InternalErrorException
	{
		ExtensibleObjectFatory eof = new ExtensibleObjectFatory();
		eof.setAgentName(getSystem().getName());
		eof.setServer( server);
		return eof.getExtensibleObject(type, object1, object2);
	}

	public boolean supportsRename () {
		return false;
	}

	public boolean isSingleton() {
		return false;
	}
	
	public void startCaptureLog() {
		log = new CaptureLogger();
	}
	
	public String endCaptureLog () {
		String r = null;
		if ( log instanceof CaptureLogger)
		{
			r = log.toString();
		}
        log = LoggerFactory.getLogger(getClass());
        return r;
	}

	public String getCapturedLog () {
		String r = null;
		if ( log instanceof CaptureLogger)
		{
			r = log.toString();
		}
        return r;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public Collection<Map<String, Object>> invoke(String verb, String command,
				Map<String, Object> params) throws RemoteException, InternalErrorException 
	{
		throw new InternalErrorException ("Not implemented on agent "+getAgentName());
	}
	
	public void close () {
		if (onClose != null &&  ! isSingleton())
			onClose.run();
	}

	public Runnable getOnClose() {
		return onClose ;
	}

	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}

	public List<String[]> getAccountChangesToApply (Account account) throws RemoteException, InternalErrorException {
		return null;
	}

	public List<String[]> getRoleChangesToApply (Role role) throws RemoteException, InternalErrorException {
		return null;
	}

	public void checkConnectivity() throws InternalErrorException {}

}

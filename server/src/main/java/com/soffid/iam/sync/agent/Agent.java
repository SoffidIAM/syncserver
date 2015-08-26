/*
 * Agent.java
 *
 * Created on May 8, 2000, 10:44 AM
 */

package com.soffid.iam.sync.agent;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;

import org.slf4j.LoggerFactory;

import com.soffid.iam.api.Group;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.User;
import com.soffid.iam.sync.jetty.JettyServer;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public abstract class Agent implements AgentInterface {
    transient private ServerService server;
    String agentVersion;
    String serverName;
    
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
    
}

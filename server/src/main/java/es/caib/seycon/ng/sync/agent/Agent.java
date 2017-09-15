/*
 * Agent.java
 *
 * Created on May 8, 2000, 10:44 AM
 */

package es.caib.seycon.ng.sync.agent;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;

import org.slf4j.LoggerFactory;

import com.soffid.iam.api.SoffidObjectType;
import com.soffid.iam.sync.engine.InterfaceWrapper;
import com.soffid.iam.sync.engine.extobj.ExtensibleObjectFatory;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.jetty.JettyServer;

import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.sync.intf.AgentMgr;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * Clase base para todo agente seycon. Cualquier agente debe derivarse de Agent.
 * Su ciclo de vida es: <BR>
 * <LI>Construccion </LI>
 * <LI>setName ( ... )</LI>
 * <LI>setRoleBased ( ... ) </LI>
 * <LI>init ()</LI>
 * <LI>invocacion 1 ( ... )</LI>
 * <LI>invocacion 2 ( ... )</LI>
 * <LI>invocacion ...</LI>
 * 
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.2 $
 */

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
    Dispatcher dispatcher;

    public Dispatcher getDispatcher() {
        return dispatcher;
    }


    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
        log = LoggerFactory.getLogger(dispatcher.getCodi());
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


    public String getCodi ()
    {
    	return getDispatcher().getCodi();
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
    public String[] getUserGroupsAndRoles(String account, Usuari user)
            throws InternalErrorException, RemoteException,
            UnknownUserException {
        Collection<Grup> groups = getServer().getUserGroups(user.getCodi(), null);
        Collection<RolGrant> roles = getServer().getAccountRoles(account, getCodi());

        String concat[] = new String[groups.size() + roles.size()];
        int i = 0;
        for (Iterator<Grup> itGroup = groups.iterator(); itGroup.hasNext(); ) {
            concat[i++] = itGroup.next().getCodi();
        }
        for (Iterator<RolGrant> itRole = roles.iterator(); itRole.hasNext(); ) {
            concat[i++] = itRole.next().getRolName();
        }
        return concat;
    }
 
	public ExtensibleObject getExtensibleObject (SoffidObjectType type, String object1, String object2) throws InternalErrorException
	{
		ExtensibleObjectFatory eof = new ExtensibleObjectFatory();
		eof.setAgentName(getDispatcher().getCodi());
		eof.setServer( InterfaceWrapper.getServerService(server));
		com.soffid.iam.sync.intf.ExtensibleObject eo = eof.getExtensibleObject(type, object1, object2);
		return ExtensibleObject.toExtensibleObject(eo);
	}

}

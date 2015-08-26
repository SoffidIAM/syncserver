/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.api.Group;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class GroupExtensibleObject extends com.soffid.iam.sync.engine.extobj.GroupExtensibleObject
{
	public GroupExtensibleObject (Grup grup, String dispatcher, ServerService serverService)
	{
		super(Group.toGroup(grup), dispatcher, InterfaceWrapper.getServerService(serverService));
	}
}

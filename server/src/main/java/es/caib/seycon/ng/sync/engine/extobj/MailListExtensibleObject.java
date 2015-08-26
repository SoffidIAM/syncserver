/**
 * 
 */
package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.api.MailList;
import com.soffid.iam.sync.engine.InterfaceWrapper;

import es.caib.seycon.ng.comu.LlistaCorreu;
import es.caib.seycon.ng.sync.servei.ServerService;

/**
 * @author bubu
 *
 */
public class MailListExtensibleObject extends com.soffid.iam.sync.engine.extobj.MailListExtensibleObject
{
	public MailListExtensibleObject (LlistaCorreu mailList, ServerService serverService)
	{
		super(MailList.toMailList(mailList), InterfaceWrapper.getServerService(serverService));
	}



}

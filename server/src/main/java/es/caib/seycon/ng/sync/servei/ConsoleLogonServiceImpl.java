package es.caib.seycon.ng.sync.servei;

import es.caib.seycon.ng.comu.PasswordValidation;

public class ConsoleLogonServiceImpl extends ConsoleLogonServiceBase {

	@Override
	protected PasswordValidation handleValidatePassword(String user,
			String passwordDomain, String password) throws Exception {
		return getLogonService().validatePassword(user, passwordDomain, password);
	}

}

package com.soffid.iam.sync.service;

import com.soffid.iam.api.PasswordValidation;

public class ConsoleLogonServiceImpl extends ConsoleLogonServiceBase {

	@Override
	protected PasswordValidation handleValidatePassword(String user,
			String passwordDomain, String password) throws Exception {
		return getLogonService().validatePassword(user, passwordDomain, password);
	}

}

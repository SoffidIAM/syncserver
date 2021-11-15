package com.soffid.iam.sync.service;

import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.ibm.icu.impl.ValidIdentifiers.Datatype;
import com.ibm.icu.text.SimpleDateFormat;
import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.AccountStatus;
import com.soffid.iam.api.AttributeVisibilityEnum;
import com.soffid.iam.api.Challenge;
import com.soffid.iam.api.DataType;
import com.soffid.iam.api.MetadataScope;
import com.soffid.iam.api.NewPamSession;
import com.soffid.iam.api.OtpChallengeProxy;
import com.soffid.iam.api.PamSecurityCheck;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordValidation;
import com.soffid.iam.api.System;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.bpm.api.ProcessDefinition;
import com.soffid.iam.bpm.api.ProcessInstance;
import com.soffid.iam.bpm.api.TaskInstance;
import com.soffid.iam.common.security.SoffidPrincipal;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.Parameter;
import com.soffid.iam.model.UserEntity;
import com.soffid.iam.security.SoffidPrincipalImpl;
import com.soffid.iam.utils.Security;

import es.caib.seycon.ng.comu.AccountAccessLevelEnum;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.TipusSessio;
import es.caib.seycon.ng.comu.TypeEnumeration;
import es.caib.seycon.ng.exception.InternalErrorException;

public class PamProxySessionServiceImpl extends PamProxySessionServiceBase {

	@Override
	protected boolean handleValidatePin(OtpChallengeProxy challenge, String value) throws Exception {
		Challenge ch = new Challenge();
		ch.setUser(challenge.getUser());
		ch.setAccount(challenge.getAccount());
		ch.setCardNumber(challenge.getCardNumber());
		ch.setCell(challenge.getCell());
		ch.setOtpHandler(challenge.getOtpHandler());
		ch.setValue(challenge.getValue());
		return getOTPValidationService().validatePin(ch, value);
	}

	SoffidPrincipal getUserPrincipal(String user) throws InternalErrorException {
		com.soffid.iam.api.System system = getDispatcherService().findSoffidDispatcher(); 
		Account acc = getAccountService().findAccount(user, system.getName());
		if (acc == null)
			return null;
		if (acc.getType() == AccountType.SHARED)
			return new SoffidPrincipalImpl(user, null, null, null, Collections.singletonList(Security.AUTO_AUTHORIZATION_ALL), 
					null, null);
		else {
			String userName = acc.getOwnerUsers().iterator().next();
			User u = getUserService().findUserByUserName(userName);
			return new SoffidPrincipalImpl(user, u.getUserName(), u.getFullName(), null, Collections.singletonList(Security.AUTO_AUTHORIZATION_ALL), 
					null, null);
		}
	}
	
	@Override
	protected NewPamSession handleOpenSession(String userName, Account account,
			String sourceIp,
			TipusSessio type,
			String info,
			Map<String, Map<String, String>> obligations) throws Exception {
		SoffidPrincipal p = getUserPrincipal(userName);
		Security.nestedLogin(p);
		try {
			for (String obligation: obligations.keySet()) {
				p.setObligation(obligation.toString(), obligations.get(obligation), java.lang.System.currentTimeMillis() + 3600000L);
			}

			
			getPamSecurityHandlerService().checkPermission(getAccountEntityDao().load(account.getId()), "launch"); 

			NewPamSession session = getPamSessionService().createCustomJumpServerSession(account, sourceIp, type, info);
			return session;
		} finally {
			Security.nestedLogoff();
		}
	}

	@Override
	protected ProcessInstance handleStartWorkflow(String workflow, String userName, Account account, int houers, String comments) throws Exception {
		SoffidPrincipal p = getUserPrincipal(userName);
		Security.nestedLogin(p);
		try {
			for (ProcessDefinition def: getBpmEngine().findProcessDefinitions(workflow, true)) {
				TaskInstance ti = getBpmEngine().createDummyTask(def.getId());
				ti.getVariables().put("requester", p.getUserName());
				ti.getVariables().put("account", account.getName());
				ti.getVariables().put("systemName", account.getSystem());
				ti.getVariables().put("loginName", account.getLoginName());
				ti.getVariables().put("server", account.getServerName());
				Calendar c = Calendar.getInstance();
				c.add(Calendar.HOUR, houers);
				Date date = c.getTime();
				ti.getVariables().put("until", date);
				getBpmEngine().update(ti);
				if (comments != null && !comments.trim().isEmpty()) {
					getBpmEngine().addComment(ti, comments);
				}
				TaskInstance t = getBpmEngine().executeTask(ti, ti.getTransitions()[0]);
				ProcessInstance pi = getBpmEngine().getProcess(t.getProcessId());
				return pi;
			}
			return null;
		} finally {
			Security.nestedLogoff();
		}
	}

	@Override
	protected List<Account> handleFindAccounts(String userName, String url, String accountName) throws Exception {
		String serverName = url;
		try {
			serverName = new URI(url).getHost();
		} catch (Exception e) {
			
		}
		
		
		HashSet<AccountEntity> l = new HashSet<>( );
		
		for (AccountEntity ae: getAccountEntityDao().query("select a from com.soffid.iam.model.AccountEntity as a "
				+ "where a.loginName = :accountName and a.serverName = :server and a.folder is not null",
				new Parameter[] {
						new Parameter("accountName", accountName),
						new Parameter("server", serverName)
				}))
		{
			if (canUse(ae, userName))
				l.add(ae);
		}
		AccountEntity t = getAccountEntityDao().findByNameAndSystem(accountName, serverName);
		if (t != null && canUse(t, userName))
			l.add(t);
		
		return getAccountEntityDao().toAccountList(l);
		
		
	}

	private boolean canUse(AccountEntity t, String userName) throws InternalErrorException {
		if (t.getSecrets() == null || t.getSecrets().isEmpty())
			return false; // no password available
		AccountAccessLevelEnum al = getAccountEntityDao().getAccessLevel(t, userName);
		return al == AccountAccessLevelEnum.ACCESS_OWNER || al == AccountAccessLevelEnum.ACCESS_MANAGER || al == AccountAccessLevelEnum.ACCESS_USER;
	}

	@Override
	protected PasswordValidation handleValidatePassword(String user, Password password) throws Exception {
		System s = getDispatcherService().findSoffidDispatcher();
		Account acc = getAccountService().findAccount(user, s.getName());
		if (acc == null)
			return null;
		if (acc.getType() == AccountType.SHARED)
			return null;
		if (getPasswordService().checkPassword(user, s.getName(), password, true, false)) {
			return PasswordValidation.PASSWORD_GOOD;
		}
		if (getPasswordService().checkPassword(user, s.getName(), password, false, true)) {
			return PasswordValidation.PASSWORD_GOOD_EXPIRED;
		}
		return PasswordValidation.PASSWORD_WRONG;
	}

	@Override
	protected boolean handleValidateSshKey(String user, String sshKey) throws Exception {
		System soffid = getDispatcherService().findSoffidDispatcher();
		Account acc = getAccountService().findAccount(user, soffid.getName());
		if (acc == null)
			return false;
		Object keys = null;
		if (acc.getType() == AccountType.USER) {
			// Confirm ssh-key attribute exists
			if (getAdditionalDataService().findDataTypesByObjectTypeAndName("com.soffid.iam.api.User", "ssh_key").isEmpty()) {
				DataType dt = new DataType();
				dt.setAdminVisibility(AttributeVisibilityEnum.READONLY);
				dt.setBuiltin(false);
				dt.setType(TypeEnumeration.STRING_TYPE);
				dt.setLabel("SSH Public key");
				dt.setMultiValued(true);
				dt.setName("ssh-key");
				dt.setOrder(9999L);
				dt.setScope(MetadataScope.USER);
				dt.setUnique(false);
				dt.setUserVisibility(AttributeVisibilityEnum.EDITABLE);
				dt.setOperatorVisibility(AttributeVisibilityEnum.READONLY);
				getAdditionalDataService().create(dt);
			}
			String userName = acc.getOwnerUsers().iterator().next();
			User u = getUserService().findUserByUserName(userName);
			keys = u.getAttributes().get("ssh_key");
		} else {
			keys = acc.getAttributes().get("ssh_key");
		}
		if (keys == null)
			return false;
		
		if (keys instanceof Collection<?>) {
			for (Object key: (Collection)keys) 
				if (key != null && 
					(key.toString().trim().equals(sshKey) ||
					 key.toString().startsWith(sshKey+" ")))
					return true;
		} else {
			if (keys != null && keys.toString().trim().equals(sshKey))
				return true;
			
		}
		return false;
	}

	@Override
	protected OtpChallengeProxy handleGenerateOtp(String user) throws Exception {
		Challenge ch = new Challenge();
		System soffid = getDispatcherService().findSoffidDispatcher();
		Account acc = getAccountService().findAccount(user, soffid.getName());
		if (acc == null)
			return null;
		ch.setAccount(acc);
		if (acc.getType() == AccountType.USER) {
			String userName = acc.getOwnerUsers().iterator().next();
			User u = getUserService().findUserByUserName(userName);
			ch.setUser(u);
		}
		ch = getOTPValidationService().selectToken(ch);
		OtpChallengeProxy challenge = new OtpChallengeProxy();
		challenge.setUser(ch.getUser());
		challenge.setAccount(ch.getAccount());
		challenge.setCardNumber(ch.getCardNumber());
		challenge.setCell(ch.getCell());
		challenge.setOtpHandler(ch.getOtpHandler());
		challenge.setValue(ch.getValue());
		return challenge;
	}

	@Override
	protected void handleSendEmailNotification(Map<String, String> obligationDetails) throws Exception {
		String account = obligationDetails.get("account");
		String systemName = obligationDetails.get("systemName");
		
		Account acc = ServiceLocator.instance().getAccountService().findAccount(account, systemName);
		if (acc != null) {
			String subject = "Service account usage notification";
			if (obligationDetails.get("subject") != null)
				subject = obligationDetails.get("subject");
			subject += " "+ acc.getLoginName() + " @ "+ acc.getServerName() ;
			
			
			
			String body = String.format("The user %s has accessed to:\nAccount name: %s\nSystem name :%s\nLogin name  :%s\nServer      : %s\nAction      : %s\n",
					Security.getSoffidPrincipal().getFullName(),
					account,
					systemName,
					acc.getLoginName(),
					acc.getServerName(),
					obligationDetails.get("action"));
					
			if (obligationDetails.get("sendTo") != null)
				ServiceLocator.instance().getMailService().sendTextMail(obligationDetails.get("sendTo"), subject, body);
			else {
				List<String> target = new LinkedList<>();
				if (acc.getOwnerUsers() != null)
					target.addAll(acc.getOwnerUsers());
				if (acc.getOwnerRoles() != null)
					target.addAll(acc.getOwnerRoles());
				if (acc.getOwnerGroups() != null)
					target.addAll(acc.getOwnerGroups());
				ServiceLocator.instance().getMailService().sendTextMailToActors(
						target.toArray(new String[target.size()]), 
						subject, 
						body);
			}
		}
	}

}

package es.caib.seycon.ng.sync.servei;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Auditoria;
import es.caib.seycon.ng.comu.Challenge;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.EstatContrasenya;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.comu.PolicyCheckResult;
import es.caib.seycon.ng.comu.Sessio;
import es.caib.seycon.ng.comu.UserAccount;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.lang.MessageFactory;
import es.caib.seycon.ng.exception.BadPasswordException;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.LogonDeniedException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.model.AccountEntity;
import es.caib.seycon.ng.model.AuditoriaEntity;
import es.caib.seycon.ng.model.ContrasenyaEntity;
import es.caib.seycon.ng.model.ContrasenyaEntityDao;
import es.caib.seycon.ng.model.DadaUsuariEntity;
import es.caib.seycon.ng.model.DominiContrasenyaEntity;
import es.caib.seycon.ng.model.MaquinaEntity;
import es.caib.seycon.ng.model.MaquinaEntityDao;
import es.caib.seycon.ng.model.Parameter;
import es.caib.seycon.ng.model.ScContar;
import es.caib.seycon.ng.model.ScContarDao;
import es.caib.seycon.ng.model.ScTarget;
import es.caib.seycon.ng.model.TasqueEntity;
import es.caib.seycon.ng.model.UserAccountEntity;
import es.caib.seycon.ng.model.UsuariEntity;
import es.caib.seycon.ng.model.XarxaEntity;
import es.caib.seycon.ng.model.XarxaEntityDao;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.sync.engine.TaskHandler;
import es.caib.seycon.ng.sync.engine.challenge.ChallengeStore;
import es.caib.seycon.ng.sync.engine.kerberos.KerberosManager;
import es.caib.seycon.ng.sync.engine.session.SessionManager;
import es.caib.seycon.ng.sync.jetty.Invoker;
import es.caib.seycon.ng.utils.Security;

public class LogonServiceImpl extends LogonServiceBase {
    private static final int MIN_PAIN = 2000;
    private static final int MAX_PAIN = 600000;
    private Logger log = Log.getLogger("LogonServer"); //$NON-NLS-1$
    private String remoteHost;

    public String getRemoteHost() {
        if (Invoker.getInvoker() != null)
            return Invoker.getInvoker().getAddr().getHostAddress();
        else
            return null;
    }

    private void auditAccountPassword(AccountEntity account) throws Exception {
        Auditoria auditoria = new Auditoria();
        auditoria.setAccio("p"); //$NON-NLS-1$
        auditoria.setAccount(account.getName());
        auditoria.setBbdd(account.getDispatcher().getCodi());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy kk:mm:ss"); //$NON-NLS-1$
        auditoria.setData(dateFormat.format(Calendar.getInstance().getTime()));
        auditoria.setObjecte("SC_ACCOUN"); //$NON-NLS-1$

        AuditoriaEntity auditoriaEntity = getAuditoriaEntityDao().auditoriaToEntity(auditoria);
        getAuditoriaEntityDao().create(auditoriaEntity);
    }

    private void auditUserPassword (UsuariEntity account, DominiContrasenyaEntity dominiContrasenyaEntity ) throws Exception {

        Auditoria auditoria = new Auditoria();
        auditoria.setAccio("p"); //$NON-NLS-1$
        auditoria.setUsuari(account.getCodi());
        auditoria.setPasswordDomain(dominiContrasenyaEntity.getCodi());
        auditoria.setAutor(null);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy kk:mm:ss"); //$NON-NLS-1$
        auditoria.setData(dateFormat.format(Calendar.getInstance().getTime()));
        auditoria.setObjecte("SC_USUARI"); //$NON-NLS-1$

        AuditoriaEntity auditoriaEntity = getAuditoriaEntityDao().auditoriaToEntity(auditoria);
        getAuditoriaEntityDao().create(auditoriaEntity);
    }

    @Override
    protected void handleChangePassword(String user, String domain, String oldPassword,
            String newPassword) throws Exception {
        Resolver r = new Resolver(user, domain);
        Password p = new Password(oldPassword);
        Password p2 = new Password(newPassword);
        InternalPasswordService ips = getInternalPasswordService();
        if (domain != null)
        {
	        if (ips.checkAccountPassword(r.getAccountEntity(),
	                p, true, true) != PasswordValidation.PASSWORD_WRONG) {
	        	PolicyCheckResult policyCheck = ips.checkAccountPolicy(r.getAccountEntity(), p2);
	        	if (!policyCheck.isValid())
	        		throw new BadPasswordException(policyCheck.getReason());
	            ips.storeAndForwardAccountPassword(r.getAccountEntity(),
	                    p2, false, null);
	            auditAccountPassword(r.getAccountEntity());
	        }
        } else {
	        if (ips.checkPassword(r.getUserEntity(),
	    	                r.getDominiContrasenyaEntity(), p, true, true) != PasswordValidation.PASSWORD_WRONG) {
	        	PolicyCheckResult policyCheck = ips.checkPolicy(r.getUserEntity(),r.getDominiContrasenyaEntity(), p2);
	        	if (!policyCheck.isValid())
	        		throw new BadPasswordException(policyCheck.getReason());
   	            getInternalPasswordService().storeAndForwardPassword(r.getUserEntity(),
	    	                    r.getDominiContrasenyaEntity(), p2, false);
	            auditUserPassword(r.getUserEntity(), r.getDominiContrasenyaEntity());
	        }
        }
    }

    @Override
    protected void handlePropagatePassword(String user, String domain, String password)
            throws Exception {
        Resolver r = new Resolver(user, domain);

        TasqueEntity tasque = getTasqueEntityDao().newTasqueEntity();
        tasque.setData(new Timestamp(System.currentTimeMillis()));
    	InternalPasswordService ips = getInternalPasswordService();
    	
    	Password p = new Password(password);
       	if (ips.checkAccountPassword(r.getAccountEntity(), p, false, true) != 
       		PasswordValidation.PASSWORD_WRONG)
        {
    		// Password is already known
    		return;
        }
       	
       	if (ips.isOldAccountPassword(r.getAccountEntity(), p))
       	{
       		// Password is an ancient one
       		return;
        }

       	if (domain == null)
        {
        	tasque.setTransa(TaskHandler.PROPAGATE_PASSWORD);
            tasque.setUsuari(r.getUserEntity().getCodi());
        }
        else
        {
        	tasque.setTransa(TaskHandler.PROPAGATE_ACCOUNT_PASSWORD);
            tasque.setUsuari(user);
            tasque.setCoddis(domain);
        }
        
        tasque.setContra(new Password(password).toString());
        getTaskQueue().addTask(tasque);
    }

    @Override
    protected boolean handleMustChangePassword(String user, String domain) throws Exception {
        Resolver r = new Resolver(user, domain);
        if (domain == null)
        {
	        ContrasenyaEntityDao dao = getContrasenyaEntityDao();
	        ContrasenyaEntity contra = dao.findLastByUsuariDomini(r.getUserEntity(), r.getDominiContrasenyaEntity());
	        if (contra == null || contra.getDataCaducitat().before(new Date()))
	            return true;
	        else
	            return false;
        }
        else
        {
        	EstatContrasenya status = getInternalPasswordService().getAccountPasswordsStatus(r.getAccountEntity());
        	return status == null || status.getCaducada().booleanValue();
        }
    }

    @Override
    protected Challenge handleRequestChallenge(int type, String user, String domain, String host,
            String clientHost, int cardSupport) throws Exception {
        if (getRemoteHost() == null)
            return requestChallengeInternal(type, user, domain, host, clientHost, cardSupport,
                    cardSupport);
        else
            return requestChallengeInternal(type, user, domain, getRemoteHost(), clientHost,
                    cardSupport, cardSupport);
    }

    @Override
    protected Sessio handleResponseChallenge(Challenge challenge) throws Exception {
        try { // Capturar errores RMI
            ChallengeStore store = ChallengeStore.getInstance();
            Challenge ch = store.getChallenge(challenge.getChallengeId());
            ServerService server;

            if (ch == null) {
                log.debug("Wrong Challenge {} for user {}", challenge.getChallengeId(), //$NON-NLS-1$
                        challenge.getUser());
				throw new InternalErrorException(String.format(Messages
								.getString("LogonServiceImpl.UnknownChallengeMsg"), //$NON-NLS-1$
								challenge.getChallengeId()));
            }

            if (!ch.getUser().getId().equals(challenge.getUser().getId())
                    || !ch.getUser().getCodi().equals(challenge.getUser().getCodi())
                    || !ch.getTimeStamp().equals(challenge.getTimeStamp())
                    || !ch.getCardNumber().equals(challenge.getCardNumber())
                    || !ch.getCell().equals(challenge.getCell())) {
                log.debug("Wrong Challenge {} for user {}", challenge.getChallengeId(), //$NON-NLS-1$
                        challenge.getUser());
                throw new InternalErrorException(String.format(Messages.getString("LogonServiceImpl.WrongChallengeMsg"), //$NON-NLS-1$
                				challenge.getChallengeId()));
            }

            if (getRemoteHost() != null) {
                Maquina newHost = findMaquina(getRemoteHost());
                if (!newHost.getNom().equals(ch.getHost().getNom())) {
                    log.warn("Ticket spoofing detected from {}", getRemoteHost(), null); //$NON-NLS-1$
                    throw new InternalErrorException(String.format(Messages.getString("LogonServiceImpl.InvalidTokenMsg"), //$NON-NLS-1$
                    				challenge.getChallengeId()));
                }
            }

            if (!ch.getCardNumber().equals("")) { //$NON-NLS-1$
                String value = ""; //$NON-NLS-1$
                // Localizar tarjeta y celda
                List<ScContar> contar = getScContarDao().query(
                        "from es.caib.seycon.ng.model.ScConTar contar " //$NON-NLS-1$
                                + "join contar.targeta as targeta " //$NON-NLS-1$
                                + "where targeta.codi=:codi and contar.filcol=:filcol", //$NON-NLS-1$
                        new Parameter[] { new Parameter("codi", ch.getCardNumber()), //$NON-NLS-1$
                                new Parameter("codi", ch.getCell()) }); //$NON-NLS-1$
                if (contar.size() == 1 && contar.get(0).getValor().equals(challenge.getValue())) {
                    ScContar contarEntity = contar.get(0);
                    contarEntity.setDadaUs(new Date());
                    getScContarDao().update(contarEntity);
                } else {
                    log.debug("Denied Challenge {} for user {}", challenge.getChallengeId(), //$NON-NLS-1$
                            challenge.getUser());
                    throw new LogonDeniedException(Messages.getString("LogonServiceImpl.IncorredCardMsg")); //$NON-NLS-1$
                }
            }

            Sessio sessio = null;
            if (ch.getCentinelPort() <= 0)
                ch.setCentinelPort(challenge.getCentinelPort());
            if (ch.getCentinelPort() > 0) {
            	sessio = SessionManager.getSessionManager().addSession(
            					ch.getHost(),
            					ch.getCentinelPort(),
            					ch.getUser().getCodi(),
            					ch.getPassword(),
            					ch.getClientHost() ,
            					ch.getChallengeId(),
            					ch.isCloseOldSessions(), ch.isSilent());
            }
            if (ch.getPassword() != null)
                propagatePassword(ch.getUserKey(), ch.getDomain(), ch.getPassword().getPassword());
            if (ch.getType() != Challenge.TYPE_KERBEROS && ch.getType() != Challenge.TYPE_PASSWORD)
                store.removeChallenge(ch);
            log.debug("Successful challenge {} for user {}", challenge.getChallengeId(), //$NON-NLS-1$
                    challenge.getUser());
            return sessio;
        } catch (java.rmi.RemoteException e) {
            throw new es.caib.seycon.ng.exception.InternalErrorException(e.toString());
        }
    }

    @Override
    protected PasswordValidation handleValidatePassword(String user, String passwordDomain,
            String password) throws Exception {
    	Resolver r;
    	try {
    		r = new Resolver(user, passwordDomain);
    	} catch (UnknownUserException e) {
    		return PasswordValidation.PASSWORD_WRONG;
    	}
        PasswordValidation v = getInternalPasswordService().checkPassword(r.getUserEntity(),
                r.getDominiContrasenyaEntity(), new Password(password), false, true);
        if (v == PasswordValidation.PASSWORD_WRONG)
        {
        	v = getInternalPasswordService().checkPassword(r.getUserEntity(),
        	                r.getDominiContrasenyaEntity(), new Password(password), true, true);
            if (v == PasswordValidation.PASSWORD_WRONG)
            	punish();
            else
            	propagatePassword(user, passwordDomain, password);
        }
        return v;
    }

    static long lastFail = -1;
    static long nextPain = 0;

    @Override
    protected boolean handleValidatePIN(String user, String pin) throws Exception {
        UsuariEntity userEntity = getUsuariEntityDao().findByCodi(user);
        for (Iterator<DadaUsuariEntity> it = userEntity.getDadaUsuari().iterator(); it.hasNext();) {
            DadaUsuariEntity dada = it.next();
            if (dada.getTipusDada().getCodi().equals("PIN")) { //$NON-NLS-1$
                if (pin.equals(dada.getValorDada()))
                    return true;
                else {
                    punish();
                    return false;
                }
            }
        }
        return false;
    }

    private void punish() throws InterruptedException {
        long pain = 0;
        if (lastFail > System.currentTimeMillis()) {
            pain = nextPain;
            if (nextPain < MAX_PAIN) // 10 minuts
                nextPain += nextPain;
            else
                nextPain = MAX_PAIN;
        } else {
            pain = nextPain = MIN_PAIN;
        }
        lastFail = System.currentTimeMillis() + pain + pain;
        Thread.sleep(pain);
    }

	public Challenge requestChallengeInternal (int type, String user,
		String domain, final String hostIp, final String clientIp,
		final int cardSupport, int version) throws LogonDeniedException,
		InternalErrorException, UnknownUserException, UnknownHostException
	{
		if (type == Challenge.TYPE_KERBEROS && domain == null && user.contains("@"))
		{
			KerberosManager km;
			try {
				km = new KerberosManager();
			} catch (IOException e) {
				throw new UnknownUserException("Unknown realm for "+user);
			}
			int i = user.lastIndexOf('@');
			Dispatcher dispatcher = km.getDispatcherForRealm(user.substring(i+1));
			if (dispatcher == null)
				throw new UnknownUserException("Unknown realm for "+user);
			domain = dispatcher.getCodi();
			user = user.substring(0, i);
		}
		Resolver resolver = new Resolver(user, domain);

		final UsuariEntity userEntity = resolver.getUserEntity();

		// Check shared account login
		if ((resolver.getAccountEntity() != null) &&
			(resolver.getAccountEntity().getType() == AccountType.SHARED))
		{
			throw new UnknownUserException(String.format(
							Messages.getString("LogonServiceImpl.UnableStartSharedMsg"), //$NON-NLS-1$
							user));
		}
		
		if (userEntity == null)
		{
			throw new LogonDeniedException(String.format(
				Messages.getString("LogonServiceImpl.UknownUserMsg"), user)); //$NON-NLS-1$
		}
        
        final Usuari usuari = getUsuariEntityDao().toUsuari(userEntity);
        log.debug("Received requestChallenge for user {} on {}", userEntity.getCodi(), clientIp); //$NON-NLS-1$

        boolean able = false;
        boolean needed = false;

        ChallengeStore store = ChallengeStore.getInstance();
        Challenge ch = store.newChallenge(version);
        ch.setUser(usuari);
        ch.setDomain(domain);
        ch.setUserKey(user);
        ch.setType(type);

        Maquina clientMaquina = null;

        if (clientIp != null && clientIp.length() > 0) {
            try {
            	clientMaquina = findMaquina(clientIp); 
                ch.setClientHost(clientMaquina);
            } catch (UnknownHostException e) {
                Maquina virtualClient = new Maquina();
                virtualClient.setNom(clientIp);
                virtualClient.setAdreca(clientIp);
                ch.setClientHost(virtualClient);
            }
        }
        ch.setHost(findMaquina(hostIp));
        List<ScTarget> targetes = getScTargetDao()
                .query("select targeta from es.caib.seycon.ng.model.UsuariEntity as usuari " //$NON-NLS-1$
                        + "join usuari.targetesExtranet as targeta " //$NON-NLS-1$
                        + "with targeta.actiu='S' and targeta.dataCaducitat >= :now and targeta.dataEmissio < :now " //$NON-NLS-1$
                        + "where usuari.codi = :codi " + "order by targeta.dataEmissio desc", //$NON-NLS-1$ //$NON-NLS-2$
                        new Parameter[] { new Parameter("now", new Date()), //$NON-NLS-1$
                                new Parameter("codi", user) }); //$NON-NLS-1$

        if (targetes.size() > 0) {
            ScTarget targeta = targetes.get(0);
            // Localizar una casilla al azar
            java.util.Random r = new java.util.Random();
            ch.setCardNumber(targeta.getCodi());
            ScContarDao contarDao = getScContarDao();
            List<ScContar> cells = contarDao.query("from es.caib.seycon.ng.model.ScContar contar " //$NON-NLS-1$
                    + "where contar.targeta=:targeta " + "order by contar.dadaUs", //$NON-NLS-1$ //$NON-NLS-2$
                    new Parameter[] { new Parameter("targeta", targeta) }); //$NON-NLS-1$

            int l = r.nextInt() % 50;
            if (cells.size() < l)
                throw new InternalErrorException(Messages.getString("LogonServiceImpl.ErrorReadCarMsg")); //$NON-NLS-1$
            else {
                ch.setCell(cells.get(l).getFilcol());
            }
        } else {
            ch.setCardNumber(""); //$NON-NLS-1$
            ch.setCell(""); //$NON-NLS-1$
        } // end - if

        able = !ch.getCardNumber().equals(""); //$NON-NLS-1$
        if (ch.getClientHost() != null && ch.getClientHost().getId() == null)
            needed = true;

        // Determinar el host cliente
        if (!needed && !clientIp.equals("")) { //$NON-NLS-1$
            if (clientMaquina == null)
                needed = true;
        }
        StringBuffer hostDesc = new StringBuffer();
        if ( ch.getClientHost() != null)
        {
        	hostDesc.append(ch.getClientHost().getNom());
       		hostDesc.append (" ("). //$NON-NLS-1$
        			append(ch.getClientHost().getAdreca()).
        			append(")"); //$NON-NLS-1$
        	hostDesc.append(" via "); //$NON-NLS-1$
        }
    	hostDesc.append(ch.getHost().getNom()).
   				append (" ("). //$NON-NLS-1$
    			append(ch.getHost().getAdreca()).
    			append(")"); //$NON-NLS-1$
    	
        // Verificar si el soporte de tarjetas es adecuado
        switch (cardSupport) {
        case Challenge.CARD_DISABLED:
            // es.caib.seycon.ServerApplication.out.println ("Card Disabled");
            if (needed) {
                log.debug("CardRequired for user {}", user, clientIp); //$NON-NLS-1$
                throw new LogonDeniedException(String.format(Messages.getString("LogonServiceImpl.CardRequieredMsg"), //$NON-NLS-1$
                				hostDesc.toString()));
            } 
            break;
        case Challenge.CARD_IFNEEDED:
            // es.caib.seycon.ServerApplication.out.println ("Card If Needed");
            if (!needed) {
                ch.setCardNumber(""); //$NON-NLS-1$
                ch.setCell(""); //$NON-NLS-1$
            }
            break;
        case Challenge.CARD_IFABLE:
            // es.caib.seycon.ServerApplication.out.println ("Card If Able");
            if (needed && !able) {
                log.debug("CardRequired for user {}", user, clientIp); //$NON-NLS-1$
                throw new LogonDeniedException(String.format(Messages.getString("LogonServiceImpl.CardRequieredMsg"), //$NON-NLS-1$
                				hostDesc.toString()));
            }
            break;
        case Challenge.CARD_REQUIRED:
            // es.caib.seycon.ServerApplication.out.println ("Card Required");
            if (!able) {
                log.debug("CardRequired for user {}", user, clientIp); //$NON-NLS-1$
                throw new LogonDeniedException(String.format(Messages.getString("LogonServiceImpl.CardRequieredMsg"), //$NON-NLS-1$
                				hostDesc.toString()));
            }
            break;
        default:
            throw new LogonDeniedException(
				String.format(Messages.getString("LogonServiceImpl.InvalidCardSupportMsg"),  //$NON-NLS-1$
					Integer.toString(cardSupport)));
        }
        return ch;
    }

    private Maquina findMaquina(String maquina) throws UnknownHostException, InternalErrorException {
        if (maquina == null)
            return null;

        String ip = null;
        String hostName = maquina;
        byte[] binaryAddress = null;
        try {
            InetAddress inet = InetAddress.getByName(maquina);
            hostName = inet.getHostName();
            binaryAddress = inet.getAddress();
        } catch (java.net.UnknownHostException e) {
            ip = null;
            hostName = maquina;
        }

        MaquinaEntityDao dao = getMaquinaEntityDao();
        MaquinaEntity m = dao.findByAdreca(maquina);
        if (m == null) {
        	m = dao.findByNom(hostName);
        	if (m == null) {
                int firstDot = hostName.indexOf("."); //$NON-NLS-1$
                if (firstDot > 0) {
                    m = dao.findByNom(hostName.substring(0, firstDot));
                }
            }
        }
        if (m == null) {
            throw new UnknownHostException(
				String.format(Messages.getString("LogonServiceImpl.HostNotFoundMsg"), hostName, ip)); //$NON-NLS-1$
        }
        return dao.toMaquina(m);
    }

    class Resolver {
        UsuariEntity userEntity;
        AccountEntity accountEntity;
		DominiContrasenyaEntity dc;

		public AccountEntity getAccountEntity ()
		{
			return accountEntity;
		}

        public UsuariEntity getUserEntity() {
            return userEntity;
        }

        public DominiContrasenyaEntity getDominiContrasenyaEntity() {
            return dc;
        }

		public Resolver (String user, String domain)
			throws InternalErrorException, UnknownUserException
		{
			if (domain == null)
			{
				domain = getPasswordService().getDefaultDispatcher();
			}
			
			accountEntity = getAccountEntityDao()
				.findByNameAndDispatcher(user, domain);
			
			if (accountEntity == null)
				throw new UnknownUserException(String.format(
					Messages.getString("LogonServiceImpl.UnknownUserOnDomainMsg"), user, domain)); //$NON-NLS-1$

			dc = accountEntity.getDispatcher().getDomini();
			for (UserAccountEntity ua : accountEntity.getUsers())
			{
				userEntity = ua.getUser();
				return;
			}
		}
    }

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.LogonServiceBase#handleGetPolicyDescription(java.lang.String, java.lang.String, java.util.Locale)
	 */
	@Override
	protected String handleGetPasswordPolicy (String account, String dispatcher) throws Exception
	{
   		return getPasswordService().getPolicyDescription(account, dispatcher);
	}
}

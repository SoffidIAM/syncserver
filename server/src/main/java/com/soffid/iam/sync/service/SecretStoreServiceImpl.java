package com.soffid.iam.sync.service;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Account;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.RoleGrant;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.model.AccountAccessEntity;
import com.soffid.iam.model.AccountAttributeEntity;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.GroupEntity;
import com.soffid.iam.model.Parameter;
import com.soffid.iam.model.PasswordDomainEntity;
import com.soffid.iam.model.RoleEntity;
import com.soffid.iam.model.RoleEntityDao;
import com.soffid.iam.model.SecretEntity;
import com.soffid.iam.model.UserAccountEntity;
import com.soffid.iam.model.UserEntity;
import com.soffid.iam.model.UserGroupEntity;
import com.soffid.iam.model.criteria.CriteriaSearchConfiguration;
import com.soffid.iam.service.impl.SshKeyGenerator;
import com.soffid.iam.sync.service.SecretStoreServiceBase;
import com.soffid.iam.utils.ConfigurationCache;

import es.caib.seycon.ng.comu.AccountAccessLevelEnum;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.bouncycastle.crypto.RuntimeCryptoException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

public class SecretStoreServiceImpl extends SecretStoreServiceBase {
    Logger log = Log.getLogger("SecretStore");
    static HashMap<String, Object> locks = new HashMap<String, Object>();

    @Override
    protected List<Secret> handleGetSecrets(User user) throws Exception {
        Server server = getSecretConfigurationService().getCurrentServer();
        SecretEntity secret = getSecretEntityDao()
                .findByUserAndServer(user.getId(), server.getId());
        try {
            if (secret != null) {
                byte b[] = secret.getSecrets();

                byte[] r = decrypt(b);
                List<Secret> m = decode(r);
                return m;
            } else {
                return new LinkedList<Secret>();
            }
        } catch (RuntimeCryptoException e) {
            return new LinkedList<Secret>();
        } catch (BadPaddingException e) {
            return new LinkedList<Secret>();
        }
    }

	private byte[] decrypt(byte[] b) throws NoSuchAlgorithmException,
			NoSuchPaddingException, InvalidKeyException,
			InternalErrorException, IllegalBlockSizeException,
			BadPaddingException, IOException, NoSuchProviderException {
		
		int bits = 0;
		PrivateKey pk = getSecretConfigurationService().getPrivateKey();
		if (pk instanceof RSAPrivateKey) 
			bits = ((RSAPrivateKey) pk).getModulus().bitLength();
		
		Cipher c;
		if (bits > 2048)
			c = Cipher.getInstance("RSA/None/OAEPWithSHA256AndMGF1Padding", "BC");
		else
			c = Cipher.getInstance("RSA/NONE/PKCS1Padding");
		c.init(Cipher.DECRYPT_MODE, getSecretConfigurationService().getPrivateKey());
		int bs = c.getBlockSize();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		for (int i = 0; i < b.length; i += bs) {
		    byte r[] = c.doFinal(b, i, b.length - i < bs ? b.length - i : bs);
		    bout.write(r);
		}
		byte r[] = bout.toByteArray();
		return r;
	}

    @Override
    protected Password handleGetSecret(User user, String secret) throws Exception {
    	List<Secret> secrets = getSecrets(user);
        return searchSecret(secrets, secret);
    }

	private Password searchSecret (List<Secret> secrets, String secret)
	{
		for (Iterator<Secret> it = secrets.iterator(); it.hasNext();) {
            Secret s = it.next();
            if (s.getName().equals(secret))
                return s.getValue();
        }
        return null;
	}

    @Override
    protected void handlePutSecret(User user, String secret, Password value) throws Exception {
        List<Secret> secrets = getSecrets(user);
        for (Iterator<Secret> it = secrets.iterator(); it.hasNext();) {
            Secret s = it.next();
            if (s.getName().equals(secret)) {
            	if (s.getValue().getPassword().equals(value.getPassword()))
            		// No change to do
            		return;
            	else
	                it.remove();
                break;
            }
        }
        Secret s = new Secret();
        s.setName(secret);
        s.setValue(value);
        secrets.add(s);

        storeSecrets(user, secrets);
    }

    private void storeSecrets(User user, List<Secret> secrets) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, IOException, IllegalBlockSizeException,
            BadPaddingException, InternalErrorException, NoSuchProviderException {
        Secret s;
        
        UserEntity userEntity = getUserEntityDao().load(user.getId());
        // Afegir secrets amb codi d'usuari
        for ( Iterator<Secret> it = secrets.iterator(); it.hasNext(); ) {
            s = it.next();
            if (s.getName().equals ("user") || s.getName().startsWith("user."))
                it.remove();
        }
        s = new Secret();
        s.setName("user");
        s.setValue(new Password(user.getUserName()));
        secrets.add(s);
        for (Iterator<Server> it = getSecretConfigurationService().getAllServers().iterator(); it.hasNext(); ) {
            Server server = it.next();
            SecretEntity secretEntity = getSecretEntityDao().findByUserAndServer(user.getId(), server.getId());
            boolean create = false;
            if (secretEntity == null) {
                create = true;
                secretEntity = getSecretEntityDao().newSecretEntity();
                secretEntity.setUser(userEntity);
                secretEntity.setServer(getServerEntityDao().load(server.getId()));
            }
            if (server.getPublicKey() != null || true) {
                byte[] b = encode(secrets);
                byte[] r = encrypt(server, b);
                secretEntity.setSecrets(r);
                if (create) getSecretEntityDao().create(secretEntity); else getSecretEntityDao().update(secretEntity);
            }
        }
    }

	private byte[] encrypt(Server server, byte[] b)
			throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, IOException, NoSuchProviderException {
		int bits = 0;
		PublicKey pk = server.getPublicKey();
		if (pk instanceof RSAPublicKey) 
			bits = ((RSAPublicKey) pk).getModulus().bitLength();

		Cipher c;
		if (bits > 2048)
			c = Cipher.getInstance("RSA/None/OAEPWithSHA256AndMGF1Padding", "BC");
		else
			c = Cipher.getInstance("RSA/NONE/PKCS1Padding");
		c.init(Cipher.ENCRYPT_MODE, server.getPublicKey());
		int bs = c.getBlockSize();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		for (int i = 0; i < b.length; i += bs) {
		    byte r[] = c.doFinal(b, i, b.length - i < bs ? b.length - i : bs);
		    bout.write(r);
		}
		byte r[] = bout.toByteArray();
		return r;
	}

    @Override
    protected void handleReencode(User user) throws Exception {
         storeSecrets(user, getSecrets(user));
    }

    private byte[] encode(List<Secret> secrets) throws IOException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Charset ch = Charset.forName("UTF-8");
        out.write(99);

        for (Iterator<Secret> it = secrets.iterator(); it.hasNext();) {
            Secret s = it.next();
            out.write(s.getName().getBytes(ch));
            out.write(0);
            out.write(s.getValue().getPassword().getBytes(ch));
            out.write(0);
        }
        byte b[] = out.toByteArray();
        return b;
    }

    private List<Secret> decode(byte[] b) throws IOException, ClassNotFoundException {
        if (b[0] == 99) {
            LinkedList<Secret> secrets = new LinkedList<Secret>();
            Decoder d = new Decoder(b);
            String name = d.readString();
            while (name != null) {
                String value = d.readString();
                Secret secret = new Secret();
                secret.setName(name);
                secret.setValue(new Password(value));
                secrets.addLast(secret);
                name = d.readString();
            }
            return secrets;
        } else {
            return (List<Secret>) new ObjectInputStream(new ByteArrayInputStream(b)).readObject();
        }
    }

    protected Collection<User> handleGetUsersWithSecrets() {
		SessionFactory sessionFactory;
		sessionFactory = (SessionFactory) ServiceLocator.instance().getService("sessionFactory");
		Session session = sessionFactory.getCurrentSession();
		
		LinkedList<User> l = new LinkedList<User>();
    	int i = 0;
		CriteriaSearchConfiguration criteria = new CriteriaSearchConfiguration();
		do {
			log.info("Loading batch {}",  i,  null);
			criteria.setFirstResult(i);
			criteria.setMaximumResultSize(10000);
			final List<UserEntity> users = getUserEntityDao().query(
					"select distinct usuari from com.soffid.iam.model.UserEntity as usuari "
							+ "join usuari.secrets as secret", null, criteria );
			if (users.isEmpty())
				break;
			for (UserEntity usuari: users) {
				User u = new User();
				u.setId(usuari.getId());
				u.setUserName(usuari.getUserName());
				i++;
			}
			session.flush();
			session.clear();
		} while (true);
        return l;
    }

	@Override
	protected void handleRemoveSecret(User user, String secret)
			throws Exception {
        List<Secret> secrets = getSecrets(user);
        for (Iterator<Secret> it = secrets.iterator(); it.hasNext();) {
            Secret s = it.next();
            if (s.getName().equals(secret)) {
                it.remove();
                storeSecrets(user, secrets);
                break;
            }
        }
	}

	@Override
	protected void handleSetPassword(long accountId, Password value)
			throws Exception {
		AccountEntity acc = getAccountEntityDao().load(accountId);
		if (acc == null)
			throw new InternalErrorException(String.format("Invalid account id %d", accountId));

		StringBuffer b = new StringBuffer();

		if (acc.getSecrets() != null && ! acc.getSecrets().trim().isEmpty()) {
			for (String part: acc.getSecrets().split(","))
			{
				if (part.startsWith("ssh.")) {
					if (b.length() > 0) b.append(",");
					b.append(part);
				}
			}
		}
		
		byte p [] = value.getPassword().getBytes("UTF-8");
		for (Server server: getSecretConfigurationService().getAllServers())
		{
			if (b.length() > 0)
				b.append(',');
			b.append(server.getId());
			b.append('=');
			byte encoded[] = encrypt(server, p);
			b.append (Base64.encodeBytes(encoded, Base64.DONT_BREAK_LINES));
		}
		for (int i=0; i < p.length; i++)
			p[i] = '\0';
		acc.setSecrets(b.toString());
		getAccountEntityDao().update(acc, "x");
	}

	@Override
	protected Password handleGetPassword(long accountId) throws Exception {
        Server server = getSecretConfigurationService().getCurrentServer();
		AccountEntity acc = getAccountEntityDao().load(accountId);
		if (acc == null)
			return null;
		String secrets = acc.getSecrets();
		if (secrets == null)
			return null;
		String id = server.getId()+"=";
		for (String secret: secrets.split(","))
		{
			if (secret.startsWith(id))
			{
				String value = secret.substring(id.length());
				byte b[] = Base64.decode(value);
				try {
					b = decrypt(b);
					return new Password(new String(b, "UTF-8"));
				} catch (BadPaddingException e) {
					// Ignore
				} catch (RuntimeCryptoException e) {
					// Ignore
				}
			}
		}
		return null;
	}

	@Override
	protected Collection<Account> handleGetAccountsWithPassword()
			throws Exception {
		SessionFactory sessionFactory;
		sessionFactory = (SessionFactory) ServiceLocator.instance().getService("sessionFactory");
		
		Session session = SessionFactoryUtils.getSession(sessionFactory, false) ;

		LinkedList<Account> l = new LinkedList<Account>();
    	int i = 0;
		CriteriaSearchConfiguration criteria = new CriteriaSearchConfiguration();
		do {
			log.info("Loading account batch {}",  i,  null);
			criteria.setFirstResult(i);
			criteria.setMaximumResultSize(10000);
			List<AccountEntity> accounts = getAccountEntityDao().query(
					"select distinct account " +
							"from com.soffid.iam.model.AccountEntity as account "
							+ "where account.secrets is not null", null, criteria);
			if (accounts.isEmpty())
				break;
			for (AccountEntity account: accounts) {
				Account u = new Account();
				u.setId(account.getId());
				u.setName(account.getName());
				u.setSystem(account.getSystem().getName());
				i++;
			}
			session.flush();
			session.clear();
		} while (true);
        return l;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SecretStoreService#getAllSecrets(es.caib.seycon.ng.comu.User)
	 */
	public List<Secret> handleGetAllSecrets (User user) throws InternalErrorException, UnsupportedEncodingException
	{
		List<Secret> secrets = getSecrets(user);
		for (AccountEntity account: getUserAccounts(user.getId()))
		{
			generateAccountSecrets(user, secrets, account);
		}

		for (Iterator<Secret> it = secrets.iterator(); it.hasNext();)
		{
			Secret secret = it.next();
			if (secret.getName().startsWith("dompass/"))
				it.remove();
		}
		Secret secret = new Secret();
		secret.setName("user");
		secret.setValue(new Password(user.getUserName()));
		secrets.add(secret);
		return secrets;
	}

	private void generateAccountSecrets (User user, List<Secret> secrets, AccountEntity account)
					throws InternalErrorException, UnsupportedEncodingException
	{
		boolean visible = false;

		AccountEntity acc = getAccountEntityDao().load(account.getId());

		String ssoSystem = ConfigurationCache.getProperty("AutoSSOSystem");
		if (ssoSystem == null)
			ssoSystem = "SSO";
				
		if (account.getType().equals(AccountType.USER) ||
						account.getType().equals(AccountType.SHARED) ||
						account.getSystem().getName().equals(ssoSystem))
			visible = true;
		else if (account.getType().equals(AccountType.PRIVILEGED))
		{
			Date now = new Date();
			for (UserAccountEntity ua: acc.getUsers())
			{
				if (ua.getUser().getId().equals(user.getId()) && 
					ua.getUntilDate() != null && now.before(ua.getUntilDate()))
				{
					visible = true;
				}
			}
		}
		
		if (visible)
		{
			Password p = getPassword(account.getId());
			if (p == null)
			{
				if (account.getType() == AccountType.USER)
				{
					PasswordDomainEntity dce = account.getSystem().getPasswordDomain();
					p = searchSecret(secrets, "dompass/"+dce.getId());
				}
			}
			if (p != null)
			{
				Secret secret = new Secret();
				secret.setName("account."+account.getSystem().getName());
				secret.setValue(new Password (account.getName()));
				secrets.add(secret);
				if (! account.getType().equals(AccountType.USER))
				{
					secret = new Secret();
					secret.setName("accdesc."+account.getSystem().getName()+"."+account.getName());
					secret.setValue(new Password (account.getDescription()));
					secrets.add(secret);
				}
				secret = new Secret ();
				secret.setName("pass."+account.getSystem().getName()+"."+account.getName());
				secret.setValue(p);
				secrets.add(secret);
			}
			
			boolean foundUrl = false;
			boolean foundSso0 = false;
			boolean foundServer = false;
			for (AccountAttributeEntity data: account.getAttributes())
			{
				Object v = data.getObjectValue();
				String name = data.getSystemMetadata() == null ? data.getMetadata().getName() :
					data.getSystemMetadata().getName();
				if (name.startsWith("SSO:") &&  v != null && v.toString().length() > 0)
				{
					if (name.equals("SSO:URL")) foundUrl = true;
					if (name.equalsIgnoreCase("SSO:Server")) foundServer = true;
					if (name.equals("SSO:0")) foundSso0 = true;
					Secret secret = new Secret ();
					secret.setName("sso."+account.getSystem().getName()+"."+account.getName()+"."+name.substring(4));
					secret.setValue( new Password ( v.toString() ) );
					secrets.add (secret);
				}
			}
			if (!foundServer && account.getServerName() != null) {
				Secret secret = new Secret ();
				secret.setName("sso."+account.getSystem().getName()+"."+account.getName()+".Server");
				secret.setValue( new Password ( account.getServerName() ) );
				secrets.add (secret);
			}
			
			if (!foundSso0 && account.getLoginName() != null) {
				Secret secret = new Secret ();
				secret.setName("sso."+account.getSystem().getName()+"."+account.getName()+".0");
				secret.setValue( new Password ( "_="+URLEncoder.encode(account.getLoginName(), "UTF-8") ) ) ;
				secrets.add (secret);
			}
			if (!foundUrl && account.getLoginUrl() != null) {
				Secret secret = new Secret ();
				secret.setName("sso."+account.getSystem().getName()+"."+account.getName()+".URL");
				secret.setValue( new Password ( account.getLoginUrl() ) ) ;
				secrets.add (secret);
			}
		}
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SecretStoreService#setPassword(es.caib.seycon.ng.comu.User, java.lang.String)
	 */
	public void handleSetPassword (User user, String passwordDomain, Password p)
					throws Exception
	{
		PasswordDomainEntity domini = getPasswordDomainEntityDao().findByName(passwordDomain);
		if (domini != null)
			handlePutSecret(user, "dompass/"+domini.getId(), p);
	}

	@Override
	protected void handleSetPasswordAndUpdateAccount(long accountId,
			Password value, boolean mustChange, Date expirationDate)
			throws Exception {
		handleSetPassword(accountId, value);
		
		Account acc = getAccountService().load(accountId);
		if (acc == null)
			throw new InternalErrorException ("Account "+accountId+" not found");
        if (!mustChange)
        {
        	Long time = null;
        	if (expirationDate != null)
        	{
	    		getAccountService().updateAccountPasswordDate2(acc, expirationDate);
        	} else {
        		com.soffid.iam.api.System dispatcher = getDispatcherService().findDispatcherByName(acc.getSystem());
        		if (dispatcher == null)
        			throw new InternalErrorException("Dispatcher "+acc.getSystem()+" not found");
            	PasswordPolicy politica = getUserDomainService().findPolicyByTypeAndPasswordDomain(
            			acc.getPasswordPolicy(), dispatcher.getPasswordsDomain());
            	Long l = getPasswordTerm(politica);

            	getAccountService().updateAccountPasswordDate(acc, l);
        	}
        }
        else
        {
        	getAccountService().updateAccountPasswordDate(acc, new Long(0));
        }
	}
	
	private Long getPasswordTerm (PasswordPolicy politica)
	{
		Long l = null;
		
		if (politica != null && politica.getMaximumPeriod() != null && politica.getType().equals("M"))
		    l = politica.getMaximumPeriod();
		else if (politica != null && politica.getRenewalTime() != null && politica.getType().equals("A"))
			l = politica.getRenewalTime();
		else
			l = new Long(3650);
		return l;
	}

	
	private Collection<AccountEntity> getUserAccounts (Long userId) throws InternalErrorException {
		Collection<RoleGrant> grants = ServiceLocator.instance().getApplicationService().findEffectiveRoleGrantByUser(userId);
		RoleEntityDao roleEntityDao = (RoleEntityDao) ServiceLocator.instance().getService("roleEntityDao");
		Set<AccountEntity> accounts = new HashSet<AccountEntity>();
		for (RoleGrant rg : grants) {
            RoleEntity r = roleEntityDao.load(rg.getRoleId());
            for (AccountAccessEntity aae : r.getAccountAccess()) {
                if (! Boolean.TRUE.equals(aae.getDisabled()) ) 
                	accounts.add(aae.getAccount());
            }
        }
		UserEntity ue = getUserEntityDao().load(userId);
		addGrantedAccounts(ue.getPrimaryGroup(), accounts);
		for (UserGroupEntity ug : ue.getSecondaryGroups()) {
			if (! Boolean.TRUE.equals(ug.getDisabled()))
				addGrantedAccounts(ug.getGroup(), accounts);
        }
		
		for (AccountAccessEntity aae: ue.getAccountAccess())
		{
//			log.info("Checking account {} / {}", aae.getAccount().getName() , aae.getAccount().getSystem().getName());
			if (! Boolean.TRUE.equals(aae.getDisabled())) {
//				log.info("Added", null, null);
				accounts.add(aae.getAccount());
			} else {
//				log.info("Rejected", null, null);
			}
		}
		
		for (UserAccountEntity uae: ue.getAccounts())
		{
			if (uae.getAccount().getType().equals (AccountType.USER))
				accounts.add(uae.getAccount());
		}
		
		for (Iterator<AccountEntity> it = accounts.iterator(); it.hasNext() ;) {
			AccountEntity account = it.next();
			if (account.isDisabled()) {
//				log.info("Discardinf by disabled", account.getName(), account.getSystem().getName());
				it.remove();
			}
		}
		return accounts;
	}

	private void addGrantedAccounts(GroupEntity grup, Set<AccountEntity> accounts) {
		for (AccountAccessEntity aae: grup.getAccountAccess())
		{
			if (!Boolean.TRUE.equals(aae.getDisabled()))
			{
				accounts.add(aae.getAccount());
			}
		}
		if (grup.getParent() != null)
			addGrantedAccounts(grup.getParent(), accounts);
	}

	@Override
	protected Password handleGetSshPrivateKey(long accountId) throws Exception {
        Server server = getSecretConfigurationService().getCurrentServer();
		AccountEntity acc = getAccountEntityDao().load(accountId);
		if (acc == null)
			return null;
		String secrets = acc.getSecrets();
		if (secrets == null)
			return null;
		String id = "ssh."+server.getId()+"=";
		for (String secret: secrets.split(","))
		{
			if (secret.startsWith(id))
			{
				String value = secret.substring(id.length());
				byte b[] = Base64.decode(value);
				try {
					b = decrypt(b);
					return new Password(new String(b, "UTF-8"));
				} catch (BadPaddingException e) {
					// Ignore
				} catch (RuntimeCryptoException e) {
					// Ignore
				}
			}
		}
		return null;
	}

	@Override
	protected void handleSetSshPrivateKey(long accountId, Password privateKey) throws Exception {
		SshKeyGenerator g = new SshKeyGenerator();
		g.loadKey(privateKey.getPassword());

		AccountEntity acc = getAccountEntityDao().load(accountId);
		if (acc == null)
			throw new InternalErrorException(String.format("Invalid account id %d", accountId));

		StringBuffer b = new StringBuffer();

		if (acc.getSecrets() != null && ! acc.getSecrets().trim().isEmpty()) {
			for (String part: acc.getSecrets().split(","))
			{
				if (! part.startsWith("ssh.")) {
					if (b.length() > 0) b.append(",");
					b.append(part);
				}
			}
		}
		
		byte p [] = privateKey.getPassword().getBytes("UTF-8");
		for (Server server: getSecretConfigurationService().getAllServers())
		{
			if (b.length() > 0)
				b.append(',');
			b.append("ssh.")
				.append(server.getId())
				.append('=');
			byte encoded[] = encrypt(server, p);
			b.append (Base64.encodeBytes(encoded, Base64.DONT_BREAK_LINES));
		}
		for (int i=0; i < p.length; i++)
			p[i] = '\0';
		acc.setSecrets(b.toString());
		acc.setSshPublicKey(g.getPublicKeyString(acc.getLoginName()+"@"+acc.getSystem())); //$NON-NLS-1$
		getAccountEntityDao().update(acc, "x");
	}
}

class Decoder {
    Decoder(byte[] b) {
        buffer = b;
        position = 1;
        ch = Charset.forName("UTF-8");
    }

    byte buffer[];
    int position;
    Charset ch;

    String readString() throws UnsupportedEncodingException {
        if (position >= buffer.length) {
            return null;
        }
        int first = position;
        while (buffer[position] != 0) {
            position++;
        }
        byte stringBytes[] = new byte[position - first];
        System.arraycopy(buffer, first, stringBytes, 0, position - first);
        position++;
        return new String(stringBytes, ch);
    }
}

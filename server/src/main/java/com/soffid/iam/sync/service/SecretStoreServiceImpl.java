package com.soffid.iam.sync.service;

import com.soffid.iam.api.Account;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.PasswordPolicy;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.User;
import com.soffid.iam.api.UserAccount;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.model.AccountAttributeEntity;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.PasswordDomainEntity;
import com.soffid.iam.model.SecretEntity;
import com.soffid.iam.model.UserAccountEntity;
import com.soffid.iam.model.UserEntity;
import com.soffid.iam.sync.service.SecretStoreServiceBase;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.bouncycastle.crypto.RuntimeCryptoException;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

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
			BadPaddingException, IOException {
		Cipher c = Cipher.getInstance("RSA/NONE/PKCS1Padding");
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
            BadPaddingException, InternalErrorException {
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
			BadPaddingException, IOException {
		Cipher c = Cipher.getInstance("RSA/NONE/PKCS1Padding");
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
        List<UserEntity> usuaris = getUserEntityDao().query(
        		"select distinct usuari from com.soffid.iam.model.UserEntity as usuari "
        		+ "join usuari.secrets as secret", null);
        return getUserEntityDao().toUserList(usuaris);
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
        List<AccountEntity> accounts = getAccountEntityDao().query(
                "select distinct account " +
                "from com.soffid.iam.model.AccountEntity as account "
                        + "where account.secrets is not null", null);
        return getAccountEntityDao().toAccountList(accounts);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.SecretStoreService#getAllSecrets(es.caib.seycon.ng.comu.User)
	 */
	public List<Secret> handleGetAllSecrets (User user) throws InternalErrorException, UnsupportedEncodingException
	{
		List<Secret> secrets = getSecrets(user);
		for (Account account: getAccountService().getUserGrantedAccounts(user))
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

	private void generateAccountSecrets (User user, List<Secret> secrets, Account account)
					throws InternalErrorException, UnsupportedEncodingException
	{
		boolean visible = false;

		AccountEntity acc = getAccountEntityDao().load(account.getId());

		if (account.getType().equals(AccountType.USER) ||
						account.getType().equals(AccountType.SHARED))
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
				if (account instanceof UserAccount)
				{
					PasswordDomainEntity dce = getPasswordDomainEntityDao().findBySystem(account.getSystem());
					p = searchSecret(secrets, "dompass/"+dce.getId());
				}
			}
			if (p != null)
			{
				Secret secret = new Secret();
				secret.setName("account."+account.getSystem());
				secret.setValue(new Password (account.getName()));
				secrets.add(secret);
				if (! account.getType().equals(AccountType.USER))
				{
					secret = new Secret();
					secret.setName("accdesc."+account.getSystem()+"."+account.getName());
					secret.setValue(new Password (account.getDescription()));
					secrets.add(secret);
				}
				secret = new Secret ();
				secret.setName("pass."+account.getSystem()+"."+account.getName());
				secret.setValue(p);
				secrets.add(secret);
			}
			for (AccountAttributeEntity data: acc.getAttributes())
			{
				if (data.getMetadata().getName().startsWith("SSO:") && 
						data.getValue()!= null && data.getValue().length() > 0)
				{
					Secret secret = new Secret ();
					secret.setName("sso."+account.getSystem()+"."+account.getName()+"."+data.getMetadata().getName().substring(4));
					secret.setValue( new Password ( data.getValue() ) );
					secrets.add (secret);
				}
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

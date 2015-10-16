package es.caib.seycon.ng.sync.servei;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.AttributeTranslation;
import com.soffid.iam.service.CertificateValidationService;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.Account;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.ControlAcces;
import es.caib.seycon.ng.comu.DadaUsuari;
import es.caib.seycon.ng.comu.Dispatcher;
import es.caib.seycon.ng.comu.DispatcherAccessControl;
import es.caib.seycon.ng.comu.DominiContrasenya;
import es.caib.seycon.ng.comu.Grup;
import es.caib.seycon.ng.comu.Impressora;
import es.caib.seycon.ng.comu.LlistaCorreu;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.comu.PoliticaContrasenya;
import es.caib.seycon.ng.comu.Rol;
import es.caib.seycon.ng.comu.RolGrant;
import es.caib.seycon.ng.comu.RolsGrup;
import es.caib.seycon.ng.comu.Server;
import es.caib.seycon.ng.comu.ServerPlugin;
import es.caib.seycon.ng.comu.ServerPluginModule;
import es.caib.seycon.ng.comu.ServerPluginModuleType;
import es.caib.seycon.ng.comu.Tasca;
import es.caib.seycon.ng.comu.TipusDominiUsuariEnumeration;
import es.caib.seycon.ng.comu.UserAccount;
import es.caib.seycon.ng.comu.Usuari;
import es.caib.seycon.ng.comu.UsuariGrup;
import es.caib.seycon.ng.comu.UsuariImpressora;
import es.caib.seycon.ng.comu.Xarxa;
import es.caib.seycon.ng.comu.sso.Secret;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.ServerRedirectException;
import es.caib.seycon.ng.exception.UnknownGroupException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownMailListException;
import es.caib.seycon.ng.exception.UnknownNetworkException;
import es.caib.seycon.ng.exception.UnknownRoleException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.model.AccountEntity;
import es.caib.seycon.ng.model.AccountEntityDao;
import es.caib.seycon.ng.model.AgentDescriptorEntity;
import es.caib.seycon.ng.model.AutoritzacioRolEntity;
import es.caib.seycon.ng.model.AutoritzacioRolEntityDao;
import es.caib.seycon.ng.model.ConfiguracioEntity;
import es.caib.seycon.ng.model.ControlAccessEntity;
import es.caib.seycon.ng.model.ControlAccessEntityDao;
import es.caib.seycon.ng.model.CorreuExternEntity;
import es.caib.seycon.ng.model.DadaUsuariEntity;
import es.caib.seycon.ng.model.DadaUsuariEntityDao;
import es.caib.seycon.ng.model.DispatcherEntity;
import es.caib.seycon.ng.model.DispatcherEntityDao;
import es.caib.seycon.ng.model.DominiContrasenyaEntity;
import es.caib.seycon.ng.model.DominiUsuariEntity;
import es.caib.seycon.ng.model.DominiUsuariEntityDao;
import es.caib.seycon.ng.model.GrupDispatcherEntity;
import es.caib.seycon.ng.model.GrupEntity;
import es.caib.seycon.ng.model.GrupEntityDao;
import es.caib.seycon.ng.model.LlistaCorreuEntity;
import es.caib.seycon.ng.model.LlistaCorreuEntityDao;
import es.caib.seycon.ng.model.LlistaCorreuUsuariEntity;
import es.caib.seycon.ng.model.MaquinaEntity;
import es.caib.seycon.ng.model.Parameter;
import es.caib.seycon.ng.model.PoliticaContrasenyaEntity;
import es.caib.seycon.ng.model.PuntEntradaEntity;
import es.caib.seycon.ng.model.PuntEntradaEntityDao;
import es.caib.seycon.ng.model.RelacioLlistaCorreuEntity;
import es.caib.seycon.ng.model.RolAccountEntity;
import es.caib.seycon.ng.model.RolAssociacioRolEntity;
import es.caib.seycon.ng.model.RolAssociacioRolEntityDao;
import es.caib.seycon.ng.model.RolEntity;
import es.caib.seycon.ng.model.RolEntityDao;
import es.caib.seycon.ng.model.RolsGrupEntity;
import es.caib.seycon.ng.model.RolsGrupEntityDao;
import es.caib.seycon.ng.model.TipusUsuariDispatcherEntity;
import es.caib.seycon.ng.model.UserAccountEntity;
import es.caib.seycon.ng.model.UsuariEntity;
import es.caib.seycon.ng.model.UsuariEntityDao;
import es.caib.seycon.ng.model.UsuariGrupEntity;
import es.caib.seycon.ng.model.UsuariGrupEntityDao;
import es.caib.seycon.ng.model.UsuariImpressoraEntityDao;
import es.caib.seycon.ng.model.XarxaACEntity;
import es.caib.seycon.ng.model.XarxaEntity;
import es.caib.seycon.ng.model.XarxaEntityDao;
import es.caib.seycon.ng.servei.DispatcherService;
import es.caib.seycon.ng.servei.UsuariService;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.agent.Plugin;
import es.caib.seycon.ng.sync.bootstrap.ConfigurationManager;
import es.caib.seycon.ng.sync.bootstrap.JarExtractor;
import es.caib.seycon.ng.sync.engine.DispatcherHandler;
import es.caib.seycon.ng.sync.engine.TaskHandler;
import es.caib.seycon.ng.sync.jetty.Invoker;
import es.caib.seycon.ng.sync.servei.ServerServiceBase;
import es.caib.seycon.ng.sync.servei.server.Compile;
import es.caib.seycon.ng.sync.servei.server.Compile2;
import es.caib.seycon.ng.sync.servei.server.Compile3;
import es.caib.seycon.ng.utils.Security;

public class ServerServiceImpl extends ServerServiceBase {
    Logger log = Log.getLogger("ServerServiceImpl"); //$NON-NLS-1$

    @Override
    protected Usuari handleGetUserInfo(String user, String dispatcherId)
            throws Exception {
        DominiUsuariEntityDao duDao = getDominiUsuariEntityDao();
        UsuariEntityDao dao = getUsuariEntityDao();

        UsuariEntity entity = null;

        if (dispatcherId == null) {
            entity = dao.findByCodi(user);
            if (entity == null)
                throw new UnknownUserException(user);
        } else {
        	String codi = null;
            
        	AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(user, dispatcherId);
        	
        	if (acc == null)
        		throw new UnknownUserException();
        	
   			if (acc.getType().equals(AccountType.USER) )
   			{
   				for (UserAccountEntity uac: acc.getUsers())
   				{
   					entity = uac.getUser();
   				}
   			}
   			if (entity == null)
   				throw new UnknownUserException();
        }
		return getUsuariEntityDao().toUsuari(entity);
    }

    private Dispatcher getDispatcher(String dispatcherId) throws InternalErrorException {
    	DispatcherHandler handler = getTaskGenerator().getDispatcher(dispatcherId);
    	if (handler == null)
    		return null;
    	else
    		return handler.getDispatcher();
    	
	}



    @Override
    protected Collection<Usuari> handleGetGroupUsers(long groupId, boolean nomesUsuarisActius,
            String dispatcherId) throws Exception {
        Dispatcher dispatcher = null;
        
        if (dispatcherId != null) {
        	dispatcher = getDispatcher(dispatcherId);
        }

        GrupEntityDao daoGrup = getGrupEntityDao();
        UsuariEntityDao daoUsuari = getUsuariEntityDao();
        UsuariGrupEntityDao daoUsuariGrup = getUsuariGrupEntityDao();
        GrupEntity entity = daoGrup.load(groupId);
        if (entity == null)
            throw new UnknownGroupException(Long.toString(groupId));

        Collection<Usuari> result = new LinkedList<Usuari>();
        for (Iterator<UsuariEntity> it = daoUsuari.findByGrupPrimari(entity.getCodi()).iterator(); it
                .hasNext();) {
            UsuariEntity usuariEntity = it.next();
            if (!nomesUsuarisActius || "S".equals(usuariEntity.getActiu())) { //$NON-NLS-1$
                if (dispatcher == null || 
                	getDispatcherService().isUserAllowed(dispatcher, usuariEntity.getCodi()))
                		result.add(daoUsuari.toUsuari(usuariEntity));
            }
        }

        for (Iterator<UsuariGrupEntity> it = daoUsuariGrup.findByCodiGrup(entity.getCodi())
                .iterator(); it.hasNext();) {
            UsuariGrupEntity ugEntity = it.next();
            if (!nomesUsuarisActius || "S".equals(ugEntity.getUsuari().getActiu())) { //$NON-NLS-1$
                if (dispatcher == null || 
                    	getDispatcherService().isUserAllowed(dispatcher, ugEntity.getUsuari().getCodi()))
                    result.add(daoUsuari.toUsuari(ugEntity.getUsuari()));
            }
        }

        return result;
    }


    @Override
    protected Collection<Account> handleGetRoleAccounts(long roleId, String dispatcherId)
            throws Exception {
    	List<Account> acc = new LinkedList<Account>();
    	Collection<RolGrant> rgs = getAplicacioService().findEffectiveRolGrantsByRolId(roleId);
    	Date now = new Date();
    	for (RolGrant rg: rgs)
    	{
			if ( (rg.getStartDate() == null || now.after(rg.getStartDate())) &&
		    				(rg.getEndDate() == null || now.before(rg.getEndDate())))
   			{
        		AccountEntity account = getAccountEntityDao().
        				findByNameAndDispatcher(rg.getOwnerAccountName(), rg.getOwnerDispatcher());
        		if (account.getUsers().isEmpty())
        			acc.add(getAccountEntityDao().toAccount(account));
        		else
        			for (UserAccountEntity uae: account.getUsers())
        				acc.add(getUserAccountEntityDao().toUserAccount(uae));
   			}
    	}
    	return acc;
    }

    @Override
    protected Collection<Account> handleGetRoleActiveAccounts(long roleId, String dispatcherId)
            throws Exception {
    	List<Account> acc = new LinkedList<Account>();
    	Collection<RolGrant> rgs = getAplicacioService().findEffectiveRolGrantsByRolId(roleId);
    	for (RolGrant rg: rgs)
    	{
    		AccountEntity account = getAccountEntityDao().
    				findByNameAndDispatcher(rg.getOwnerAccountName(), rg.getOwnerDispatcher());
    		if (!account.isDisabled())
    		{
        		if (account.getUsers().isEmpty())
        			acc.add(getAccountEntityDao().toAccount(account));
        		else
        			for (UserAccountEntity uae: account.getUsers())
        				acc.add(getUserAccountEntityDao().toUserAccount(uae));
    		}
    	}
    	return acc;
    }

    @Override
    protected Collection<RolGrant> handleGetRoleExplicitRoles(long roleId) throws Exception {
        RolEntityDao rolDao = getRolEntityDao();
        RolAssociacioRolEntityDao rarDao = getRolAssociacioRolEntityDao();

        RolEntity rol = rolDao.load(roleId);
        if (rol == null)
            throw new UnknownRoleException();

        return rarDao.toRolGrantList(rol.getRolAssociacioRolSocContenidor());
    }

    @Override
    protected Collection<Grup> handleGetUserGroups(String accountName, String dispatcherId) throws Exception {
        HashMap<String, GrupEntity> grups = getUserGrupsMap(accountName, dispatcherId);
        return getGrupEntityDao().toGrupList(grups.values());
    }

	private HashMap<String, GrupEntity> getUserGrupsMap(String accountName,
			String dispatcherId) throws UnknownUserException,
			InternalErrorException {
		UsuariEntityDao dao = getUsuariEntityDao();
    	GrupEntityDao grupDao = getGrupEntityDao();
        HashMap<String, GrupEntity>grups = new HashMap<String, GrupEntity>();

        if (dispatcherId == null)
        {
        	UsuariEntity user = dao.findByCodi(accountName);
        	if (! grups.containsKey(user.getGrupPrimari().getCodi()))
        		grups.put(user.getGrupPrimari().getCodi(), user.getGrupPrimari());
	        for (Iterator<UsuariGrupEntity> it = user.getGrupsSecundaris().iterator(); it.hasNext();) {
	            UsuariGrupEntity uge = it.next();
	        	if (! grups.containsKey(uge.getGrup().getCodi()))
	        		grups.put(uge.getGrup().getCodi(), uge.getGrup());
	        }
        }
        else
        {
	        AccountEntity account = getAccountEntityDao().findByNameAndDispatcher(accountName, dispatcherId); 
	        if (account == null)
	            throw new UnknownUserException(accountName+"/"+dispatcherId); //$NON-NLS-1$
	
	        if (account.getType().equals (AccountType.USER))
	        {
	        	Dispatcher dispatcher = getDispatcher (dispatcherId);
		        for (UserAccountEntity ua: account.getUsers())
		        {
		        	UsuariEntity user = ua.getUser();
			        if (getDispatcherService().isGroupAllowed(dispatcher, user.getGrupPrimari().getCodi()))
			        {
			        	if (! grups.containsKey(user.getGrupPrimari().getCodi()))
			        		grups.put(user.getGrupPrimari().getCodi(),
				        		user.getGrupPrimari());
			        }
			        for (Iterator<UsuariGrupEntity> it = user.getGrupsSecundaris().iterator(); it.hasNext();) {
			            UsuariGrupEntity uge = it.next();
				        if (getDispatcherService().isGroupAllowed(dispatcher, uge.getGrup().getCodi()))
				        	if (! grups.containsKey(uge.getGrup().getCodi()))
				        		grups.put(uge.getGrup().getCodi(),
				        			uge.getGrup());
			        }
		        }
	        }
        }
		return grups;
	}

	private void testInclusion(Dispatcher dispatcher, UsuariEntity entity)
			throws InternalErrorException, UnknownUserException {
		if (dispatcher != null && !getDispatcherService().isUserAllowed(dispatcher, entity.getCodi()))
        	throw new UnknownUserException();
	}

    @Override
    protected Collection<Grup> handleGetUserGroupsHierarchy(String accountName, String dispatcherId)
            throws Exception {
        HashMap<String, GrupEntity> grups = getUserGrupsMap(accountName, dispatcherId);
        LinkedList<Grup> values = new LinkedList<Grup>();
        HashSet<String> keys = new HashSet<String>(grups.keySet());
        GrupEntityDao grupDao = getGrupEntityDao();
        for (GrupEntity grup: grups.values())
        {
        	while ( grup != null && ! keys.contains(grup.getCodi()))
        	{
        		keys.add(grup.getCodi());
        		values.add(grupDao.toGrup(grup));
        		grup = grup.getPare();
        	};
        }

        return values;
    }


    @Override
    protected Collection<RolGrant> handleGetUserExplicitRoles(long userId, String dispatcherid)
            throws Exception {
    	UsuariEntity user = getUsuariEntityDao().load(new Long(userId));
    	List<AccountEntity> accounts = null;
    	if (dispatcherid == null)
    	{
    		accounts = new LinkedList<AccountEntity>();
    		for (UserAccountEntity ua: user.getAccounts())
    		{
    			if (ua.getAccount().getType().equals(AccountType.USER))
    				accounts.add (ua.getAccount());
    		}
    	}
    	else
    		accounts = getAccountEntityDao().findByUsuariAndDispatcher(user.getCodi(), dispatcherid);
    	
    	Collection<RolGrant> grants = new LinkedList<RolGrant>();
		Date now = new Date();
    	for (AccountEntity account: accounts)
    	{
    		Collection<RolGrant> partialGrants = getAplicacioService().findRolGrantByAccount(account.getId());
    		for (RolGrant rg: partialGrants)
    		{
   				grants.add (rg);
    		}
    	}
        return grants;
    }

    @Override
    protected Collection<RolGrant> handleGetGroupExplicitRoles(long groupId) throws Exception {
        GrupEntityDao grupDao = getGrupEntityDao();
        GrupEntity grup = grupDao.load(groupId);
        RolsGrupEntityDao rgDao = getRolsGrupEntityDao();
        Collection<RolsGrupEntity> rols = grup.getRolsOtorgatsGrup();
        return rgDao.toRolGrantList(rols);
    }

    @Override
    protected Collection<RolGrant> handleGetUserRoles(long userId, String dispatcher)
            throws Exception {
    	Collection<RolGrant> rg = getAplicacioService().findEffectiveRolGrantByUser(userId);
    	if (dispatcher != null)
    	{
    		for (Iterator<RolGrant> it = rg.iterator(); it.hasNext();)
    		{
    			RolGrant grant = it.next();
    			if (! dispatcher.equals (grant.getDispatcher()))
    				it.remove();
    		}
    	}
        return rg;
    }

    @Override
    protected DadaUsuari handleGetUserData(long userId, String data) throws Exception {
        DadaUsuariEntityDao dao = getDadaUsuariEntityDao();

        UsuariEntity usuari = getUsuariEntityDao().load(userId);

        DadaUsuariEntity dataEntity = dao.findDadaByCodiTipusDada(usuari.getCodi(), data);
        if (dataEntity == null)
            return null;
        else
            return dao.toDadaUsuari(dataEntity);

    }

    @Override
    protected Collection<UsuariImpressora> handleGetUserPrinters(Long userId) throws Exception {
        UsuariEntity user = getUsuariEntityDao().load(userId);
        if (user == null)
            throw new UnknownUserException(userId.toString());
        UsuariImpressoraEntityDao dao = getUsuariImpressoraEntityDao();
        return dao.toUsuariImpressoraList(user.getImpressores());
    }

    @Override
    protected Maquina handleGetHostInfo(String hostName) throws Exception {
        MaquinaEntity host = getMaquinaEntityDao().findByNom(hostName);
        if (host == null)
            throw new UnknownHostException(hostName);
        return getMaquinaEntityDao().toMaquina(host);
    }

    @Override
    protected Maquina handleGetHostInfoByIP(String ip) throws Exception {
        MaquinaEntity host = getMaquinaEntityDao().findByAdreca(ip);
        if (host == null)
            throw new UnknownHostException(ip);
        return getMaquinaEntityDao().toMaquina(host);
    }

    @Override
    protected Xarxa handleGetNetworkInfo(String network) throws Exception {
        XarxaEntity xarxa = getXarxaEntityDao().findByCodi(network);
        if (xarxa == null)
            throw new UnknownNetworkException(network);
        return getXarxaEntityDao().toXarxa(xarxa);
    }

    @Override
    protected Collection<Grup> handleGetGroupChildren(long groupId, String dispatcherId)
            throws Exception {
        Dispatcher dispatcher = null;
        if (dispatcherId != null)
            dispatcher = getDispatcher(dispatcherId);
        
        DispatcherService disSvc = getDispatcherService();

        GrupEntityDao dao = getGrupEntityDao();
        GrupEntity entity = dao.load(groupId);
        if (entity == null)
            throw new UnknownGroupException(Long.toString(groupId));
        LinkedList<Grup> grups = new LinkedList<Grup>();
        for (Iterator<GrupEntity> it = entity.getFills().iterator(); it.hasNext();) {
            GrupEntity ge = it.next();
            if (dispatcher == null ||
            	disSvc.isGroupAllowed(dispatcher, ge.getCodi()))
                grups.add(dao.toGrup(ge));
        }
        return grups;
    }

    @Override
    protected Rol handleGetRoleInfo(String role, String bd) throws Exception {
        RolEntityDao dao = getRolEntityDao();
        RolEntity rolEntity = dao.findByNameAndDispatcher(role, bd);
        if (rolEntity != null)  
            return dao.toRol(rolEntity);
        else
            return null;
    }

    @Override
    protected Collection<Xarxa> handleGetNetworksList() throws Exception {
        XarxaEntityDao dao = getXarxaEntityDao();
        return dao.toXarxaList(dao.loadAll());
    }

    @Override
    protected void handleClientAgentStarted(String agentName) throws Exception {
        for (Iterator<DispatcherHandler> it = getTaskGenerator().getDispatchers().iterator(); it
                .hasNext();) {
            DispatcherHandler d = it.next();
            if (d != null)
            {
                try {
                	if ( ! d.isConnected())
                		d.reconfigure();
                	else
                	{
                    	URL url = new URL (d.getDispatcher().getUrl());
                    	if (url.getHost().equals(agentName))
                    	{
                    		d.reconfigure();
                    	}
                	}
                } catch (Exception e) 
                {
                	
                }
            }
        }
    }

    @Override
    protected Collection<Maquina> handleGetHostsFromNetwork(long networkId) throws Exception {
        XarxaEntity xarxa = getXarxaEntityDao().load(networkId);
        if (xarxa == null)
            throw new UnknownNetworkException(Long.toString(networkId));
        return getMaquinaEntityDao().toMaquinaList(xarxa.getMaquines());
    }

    @Override
    protected String handleGetConfig(String param) throws Exception {
        Invoker invoker = Invoker.getInvoker();
        XarxaEntity xarxa = null;

        if (invoker != null) {
            XarxaEntityDao dao = getXarxaEntityDao();
            InetAddress addr = invoker.getAddr();
            byte b[] = addr.getAddress();
            for (int bc = b.length - 1; xarxa == null && bc >= 0; bc--) {
                byte mascara = (byte) 255;
                for (int bits = 0; xarxa == null && bits < 8; bits++) {
                    mascara = (byte) (mascara << 1);
                    b[bc] = (byte) (b[bc] & mascara);
                    InetAddress addr2 = InetAddress.getByAddress(b);
                    String addrText = addr2.getHostAddress();
                    xarxa = dao.findByAdreca(addrText);
                }
            }
        }
        ConfiguracioEntity config;
        if (xarxa == null)
            config = getConfiguracioEntityDao().findByCodiAndCodiXarxa(param, null);
        else {
            config = getConfiguracioEntityDao().findByCodiAndCodiXarxa(param, xarxa.getCodi());
            if (config == null)
                config = getConfiguracioEntityDao().findByCodiAndCodiXarxa(param, null);
        }

        if (config == null)
            return null;
        else
            return config.getValor();

    }

    @Override
    protected LlistaCorreu handleGetMailList(String list, String domain) throws Exception {
        LlistaCorreuEntityDao dao = getLlistaCorreuEntityDao();
        LlistaCorreuEntity entity = dao.findByNomAndCodiDomini(list, domain);
        if (entity != null) {
            return dao.toLlistaCorreu(entity);
        }
        
            UsuariEntity usuari = getUserForMailList(list, domain);
            LlistaCorreu llista = new LlistaCorreu();
            llista.setId(null);
            llista.setCodiDomini(domain);
            llista.setNom(list);
            llista.setLlistaUsuaris(usuari.getCodi());
            llista.setDescripcio(usuari.getNom()+" "+usuari.getPrimerLlinatge()+" "+usuari.getSegonLlinatge()); //$NON-NLS-1$ //$NON-NLS-2$
            return llista;
    }

    private UsuariEntity getUserForMailList(String list, String domain)
            throws UnknownMailListException {
        List<UsuariEntity> usuaris;
        if (domain == null)
            usuaris = getUsuariEntityDao().query("select usuari from es.caib.seycon.ng.model.UsuariEntity as usuari " + //$NON-NLS-1$
        		"where usuari.nomCurt=:nomCurt and usuari.dominiCorreu is null", new Parameter[] { //$NON-NLS-1$
                new Parameter("nomCurt", list) //$NON-NLS-1$
            });
        else
            usuaris = getUsuariEntityDao().query("select usuari from es.caib.seycon.ng.model.UsuariEntity as usuari " + //$NON-NLS-1$
                    "join usuari.dominiCorreu as domini with domini.codi=:domini " + //$NON-NLS-1$
                    "where usuari.nomCurt=:nomCurt", new Parameter[] { //$NON-NLS-1$
                new Parameter("domini", domain), //$NON-NLS-1$
                new Parameter("nomCurt", list) //$NON-NLS-1$
            });
        if (usuaris == null || usuaris.isEmpty())
            throw new UnknownMailListException(list + "@" + domain); //$NON-NLS-1$
        UsuariEntity usuari = usuaris.get(0);
        return usuari;
    }

    @Override
    protected Collection<Object> handleGetMailListMembers(String list, String domain)
            throws Exception {
        LinkedList<Object> members = new LinkedList<Object>();
        LlistaCorreuEntityDao dao = getLlistaCorreuEntityDao();
        LlistaCorreuEntity entity = dao.findByNomAndCodiDomini(list, domain);
        if (entity != null)
        {
    
            for (Iterator<LlistaCorreuUsuariEntity> it = entity.getLlistaDeCorreuUsuari().iterator(); it
                    .hasNext();) {
                LlistaCorreuUsuariEntity lcu = it.next();
                members.add(getUsuariEntityDao().toUsuari(lcu.getUsuari()));
            }
    
            for (Iterator<CorreuExternEntity> it = entity.getExterns().iterator(); it.hasNext();) {
                CorreuExternEntity lcu = it.next();
                members.add(lcu.getAdreca());
            }
    
            for (Iterator<RelacioLlistaCorreuEntity> it = entity.getRelacioLlistaCorreuFromConte()
                    .iterator(); it.hasNext();) {
                RelacioLlistaCorreuEntity lcu = it.next();
                members.add(dao.toLlistaCorreu(lcu.getPertany()));
            }
            return members;
        }
        UsuariEntity usuari = getUserForMailList(list, domain);
        members.add (getUsuariEntityDao().toUsuari(usuari));
        return members;
    }

    @Override
    protected Dispatcher handleGetDispatcherInfo(String codi) throws Exception {
        DispatcherEntityDao dao = getDispatcherEntityDao();
        return dao.toDispatcher(dao.findByCodi(codi));
    }

    private boolean hasAuthorization(Collection<RolGrant> roles, String authorization)
            throws InternalErrorException, UnknownUserException {
        AutoritzacioRolEntityDao autDao = getAutoritzacioRolEntityDao();
        for (Iterator<AutoritzacioRolEntity> it = autDao.findByAutoritzacio(authorization)
                .iterator(); it.hasNext();) {
            AutoritzacioRolEntity aut = it.next();
            for (Iterator<RolGrant> itGrant = roles.iterator(); itGrant.hasNext();) {
                RolGrant grant = itGrant.next();
                if (grant.getIdRol().equals(aut.getRol().getId()))
                    return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleHasSupportAccessHost(long hostId, long userId) throws Exception {
        // Comprovar si to el roll de suport a totes les maquines
        Collection<RolGrant> roles = getUserRoles(userId, null);
        if (hasAuthorization(roles, Security.AUTO_HOST_ALL_SUPPORT_VNC))
            return true;

        // Comprovar si existeix una ACL per a ell
        boolean found = true;
        UsuariEntity usuariEntity = getUsuariEntityDao().load(userId);
        Collection<Grup> grups = getUserGroups(usuariEntity.getCodi(), null);
        grups.addAll(getUserGroupsHierarchy(usuariEntity.getCodi(), null));
        MaquinaEntity maq = getMaquinaEntityDao().load(hostId);
        XarxaEntity xarxa = maq.getXarxa();
        for (Iterator<XarxaACEntity> it = xarxa.getAutoritzacions().iterator(); !found
                && it.hasNext();) {
            XarxaACEntity ace = it.next();
            if (ace.getNivell() >= 1 && Pattern.matches(ace.getNomMaquines(), maq.getNom())) {
                if (ace.getRole() != null) {
                    for (Iterator<RolGrant> itGrant = roles.iterator(); !found && itGrant.hasNext();) {
                        RolGrant grant = itGrant.next();
                        if (grant.getIdRol().equals(ace.getRole().getId()))
                            found = true;
                    }
                }
                if (ace.getUsuari() != null && ace.getUsuari().getId().longValue() == userId)
                    found = true;
                if (ace.getGrup() != null) {
                    for (Iterator<Grup> itGrup = grups.iterator(); itGrup.hasNext();) {
                        Grup grup = itGrup.next();
                        if (grup.getId().equals(ace.getGrup().getId()))
                            found = true;
                    }
                }
            }
        }
        return found;
    }

    @Override
    protected byte[] handleGetPluginJar(String classname) throws Exception {
        AgentDescriptorEntity entity = getAgentDescriptorEntityDao().findByClass(classname);
        if (entity == null) throw new InternalErrorException(Messages.getString("ServerServiceImpl.pluginNotFound")+classname); //$NON-NLS-1$
        if (entity.getModule() == null && entity.getPlugin() != null)
        	return entity.getPlugin().getContent();
        else
        	return entity.getModule().getContents();
    }

    @Override
    protected Usuari handleGetUserInfo(X509Certificate certs[]) throws Exception {
        UsuariService usuariService = ServerServiceLocator.instance().getUsuariService();
        CertificateValidationService certificateService = ServerServiceLocator.instance().getCertificateValidationService();
        
        ArrayList<X509Certificate> certList = new ArrayList<X509Certificate>(certs.length);
        for (X509Certificate cert: certs) certList.add(cert);
        
        if (!certificateService.validateCertificate(certList))
        {
        	throw new InternalErrorException (String.format(Messages.getString("ServerServiceImpl.invalidCertificate"), certs[0].getSubjectX500Principal().getName())); //$NON-NLS-1$
        }
        
        Usuari usuari = certificateService.getCertificateUser(certList);
        if (usuari != null)
        	return usuari;
        
        String codi = usuariService.addUsuari(Arrays.asList(certs) , "E"); //$NON-NLS-1$

        UsuariEntity entity = getUsuariEntityDao().findByCodi(codi);

        return getUsuariEntityDao().toUsuari(entity);
    }

    @Override
    protected PasswordValidation handleValidatePassword(String account, String dispatcher, Password p)
            throws Exception {
        UsuariEntity userEntity;
        DominiContrasenyaEntity dc;
        if (dispatcher == null) {
        	userEntity = getUsuariEntityDao().findByCodi(account);
        	dc = getDispatcherEntityDao().
        		findByCodi(getDefaultDispatcher()).
        			getDomini();
            return getInternalPasswordService().checkPassword(userEntity, dc, p, true, true);
        } else {
        	AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(account, dispatcher);
        	if (acc == null)
        		return PasswordValidation.PASSWORD_WRONG;
        	
            return getInternalPasswordService().
            		checkAccountPassword(acc, p, true, true);
        }
    }

    @Override
    protected void handleChangePassword(String user, String dispatcher, Password p, boolean mustChange)
            throws Exception {
        UsuariEntity userEntity;
        DominiContrasenyaEntity dc;
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(user, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format("Uknown user %s/%s", user, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae: acc.getUsers())
        	{
            	getInternalPasswordService().storeAndForwardPassword(uae.getUser(), 
            			acc.getDispatcher().getDomini(), p, mustChange);
        	}
        } else {
            getInternalPasswordService().storeAndForwardAccountPassword(acc, p, mustChange, null);
        }
    }

    @Override
    protected void handleChangePasswordSync(String user, String dispatcher, Password p, boolean mustChange)
            throws Exception {
        UsuariEntity userEntity;
        DominiContrasenyaEntity dc;
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(user, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format("Uknown user %s/%s", user, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae: acc.getUsers())
        	{
            	getInternalPasswordService().storeAndSynchronizePassword(uae.getUser(), 
            			acc.getDispatcher().getDomini(), p, mustChange);
        	}
        } else {
            getInternalPasswordService().storeAndSynchronizeAccountPassword(acc, p, mustChange, null);
        }
    }

    @Override
    protected byte[] handleGetUserMazingerRules(long userId, String version) throws Exception {
        UsuariEntity user = getUsuariEntityDao().load(userId);
        if (user == null)
            throw new UnknownUserException(Long.toString(userId));

        String res = getUserPueXMLDescriptors(user);

        Compile c;
        if ("xml".equals(version)) //$NON-NLS-1$
            return res.getBytes("UTF-8"); //$NON-NLS-1$
        else if (version == null || "1".equals(version)) //$NON-NLS-1$
            c = new Compile();
        else if ("2".equals(version)) //$NON-NLS-1$
            c = new Compile2();
        else
            c = new Compile3();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            InputStream in = new ByteArrayInputStream(res.getBytes("UTF-8")); //$NON-NLS-1$
            c.parse(in, out, false);
        } catch (Throwable th) {
            log.warn(Messages.getString("ServerServiceImpl.compilationError"), th); //$NON-NLS-1$
            throw new InternalErrorException(th.getMessage());
        }
        return out.toByteArray();
    }

    public String getUserPueXMLDescriptors(UsuariEntity user) throws InternalErrorException,
            UnknownUserException {
        StringBuffer xmlPUE = new StringBuffer(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Mazinger>"); //$NON-NLS-1$
        HashSet<Long> duplicates = new HashSet<Long>();
        PuntEntradaEntityDao dao = getPuntEntradaEntityDao();
        // Punts d'entrada publics
        addPuntsEntrada(xmlPUE, duplicates, dao.query(
                "select punt from es.caib.seycon.ng.model.PuntEntradaEntity as punt " //$NON-NLS-1$
                        + "where punt.esPublic='S' and punt.xmlPUE is not null", //$NON-NLS-1$
                new Parameter[0]));

        // Punts d'entrada associats a l'usuari
        addPuntsEntrada(xmlPUE, duplicates, dao.query(
                "select punt from es.caib.seycon.ng.model.PuntEntradaEntity as punt " //$NON-NLS-1$
                        + "join punt.autoritzaUsuari as autoritzacio " //$NON-NLS-1$
                        + "where autoritzacio.user.id=:user and punt.xmlPUE is not null", //$NON-NLS-1$
                new Parameter[] { new Parameter("user", user.getId()) })); //$NON-NLS-1$

        // Punts d'entrada dels grups
        for (Iterator<Grup> it = getUserGroupsHierarchy(user.getCodi(), null).iterator(); it
                .hasNext();) {
            Grup grup = it.next();
            addPuntsEntrada(xmlPUE, duplicates, dao.query(
                    "select punt " + //$NON-NLS-1$
                    "from es.caib.seycon.ng.model.PuntEntradaEntity AS punt " //$NON-NLS-1$
                            + "join punt.autoritzaGrup AS autGrup " //$NON-NLS-1$
                            + "where autGrup.group.id = :grup and punt.xmlPUE is not null", //$NON-NLS-1$
                    new Parameter[] { new Parameter("grup", grup.getId()) })); //$NON-NLS-1$
        }

        // Punts d'entrada dels rols
        for (Iterator<RolGrant> it = getUserRoles(user.getId(), null).iterator(); it.hasNext();) {
            RolGrant grant = it.next();
            addPuntsEntrada(xmlPUE, duplicates, dao.query(
                    "select punt " + //$NON-NLS-1$
                    "from es.caib.seycon.ng.model.PuntEntradaEntity as punt " //$NON-NLS-1$
                            + "join punt.autoritzaRol as autRol " //$NON-NLS-1$
                            + "where autRol.role.id=:rol and punt.xmlPUE is not null", //$NON-NLS-1$
                    new Parameter[] { new Parameter("rol", grant.getIdRol()) })); //$NON-NLS-1$
        }


        Usuari usuari = getUsuariEntityDao().toUsuari(user);
		for (Account account: getAccountService().getUserGrantedAccounts(usuari))
		{
            addPuntsEntrada(xmlPUE, duplicates, dao.query(
                    "select punt " + //$NON-NLS-1$
                    "from es.caib.seycon.ng.model.PuntEntradaEntity as punt " //$NON-NLS-1$
                            + "join punt.authorizedAccounts as auth " //$NON-NLS-1$
                            + "where auth.account.id=:id and punt.xmlPUE is not null", //$NON-NLS-1$
                    new Parameter[] { new Parameter("id", account.getId()) })); //$NON-NLS-1$
        }


        xmlPUE.append("</Mazinger>");// finalitzem el document //$NON-NLS-1$

        return xmlPUE.toString();

    }

    private void addPuntsEntrada(StringBuffer xmlPUE, HashSet<Long> duplicates,
            List<PuntEntradaEntity> query) {
        for (Iterator<PuntEntradaEntity> it = query.iterator(); it.hasNext();) {
            PuntEntradaEntity punt = it.next();
            if (!duplicates.contains(punt.getId())) {
                duplicates.add(punt.getId());
                String xml = punt.getXmlPUE();
                String comentari = "<!-- " //$NON-NLS-1$
                        + (punt.getCodi() == null ? punt.getNom() : punt.getCodi() + " - " //$NON-NLS-1$
                                + punt.getNom()) + " -->"; //$NON-NLS-1$
                // Llevem la capçalera de l'aplicació
                // i afegim comentari
                // Podem tindre dues possibilitats:
                // - etiqueta buida <Mazinger/>: admet espais
                // [espais]<Mazinger[espais]/>
                // - Inici i fi <Mazinger[espais]> i
                // </Mazinger[espais]>[espais]
                final String regexInici = "<Mazinger[\\s]*>"; //$NON-NLS-1$
                final String regexFi = "</Mazinger[\\s]*>[\\s]*"; //$NON-NLS-1$
                final String regexBuit = "[\\s]*<[\\s]*Mazinger[\\s]*/[\\s]*>[\\s]*"; //$NON-NLS-1$

                if (xml == null || xml.length() == 0 || xml.matches(regexBuit)) {
                    // Si és "regexBuit", no afegim res
                } else {
                    try {
                        // Cerquem patró d'ini i fi
                        Pattern pi = Pattern.compile(regexInici);
                        Pattern pf = Pattern.compile(regexFi);
                        Matcher mi = pi.matcher(xml);
                        int inici = 0;
                        int fi = xml.length();
                        if (mi.find()) {
                            inici = mi.end();
                        }
                        Matcher mf = pf.matcher(xml);
                        while (mf.find()) {
                            fi = mf.start();
                        }
                        // Si esto no va bien causamos error
                        if ((inici < 0) || (fi < inici) || (fi < inici)) {
                            xmlPUE.append(comentari
                                    + Messages.getString("ServerServiceImpl.error1")); //$NON-NLS-1$
                        } else {
                            xmlPUE.append(comentari);
                            xmlPUE.append(xml, inici, fi);
                        }
                    } catch (Throwable th) {
                        xmlPUE.append(comentari
                                + Messages.getString("ServerServiceImpl.error2")); //$NON-NLS-1$
                    }

                }
            }
        }
    }

    @Override
    protected Collection<Secret> handleGetUserSecrets(long userId) throws Exception {
        UsuariEntity user = getUsuariEntityDao().load(userId);
        SecretStoreService sss = getSecretStoreService();
        Usuari usuari = getUsuariEntityDao().toUsuari(user);
        return sss.getAllSecrets(usuari);
    }

	@Override
    protected Grup handleGetGroupInfo(String codi, String dispatcherId) throws Exception {
        Dispatcher dispatcher = getDispatcher(dispatcherId);

        if (! getDispatcherService().isGroupAllowed(dispatcher, codi))
            throw new es.caib.seycon.ng.exception.UnknownGroupException(codi);
        GrupEntity grup = getGrupEntityDao().findByCodi(codi);
        if (grup == null)
            throw new es.caib.seycon.ng.exception.UnknownGroupException(codi);

        return getGrupEntityDao().toGrup(grup);
    }

    @Override
    protected Collection<DadaUsuari> handleGetUserData(long userId) throws Exception {
        DadaUsuariEntityDao dao = getDadaUsuariEntityDao();

        UsuariEntity usuari = getUsuariEntityDao().load(userId);
        return dao.toDadaUsuariList(usuari.getDadaUsuari());

    }

    @Override
    protected void handleCancelTask(long taskid) throws Exception {
        getTaskQueue().cancelTask(taskid);
    }

    @Override
    protected Password handleGenerateFakePassword(String account, String dispatcher) throws Exception {
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(account, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format(Messages.getString("ServerServiceImpl.unknownUser"), account, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae: acc.getUsers())
        	{
            	return getInternalPasswordService().generateFakePassword(uae.getUser(), 
            			acc.getDispatcher().getDomini());
        	}
            throw new InternalErrorException(String.format(Messages.getString("ServerServiceImpl.unknownUser"), account, dispatcher)); //$NON-NLS-1$
        } else {
            return getInternalPasswordService().generateFakeAccountPassword(acc);
        }

    }


    @Override
    protected DispatcherAccessControl handleGetDispatcherAccessControl(Long dispatcherId)
            throws Exception {
        DispatcherEntity dispatcher = getDispatcherEntityDao().load(dispatcherId.longValue());
        if (dispatcher == null)
            throw new InternalErrorException(Messages.getString("ServerServiceImpl.dispatcherNotFound")); //$NON-NLS-1$

        DispatcherAccessControl dispatcherInfo = new DispatcherAccessControl(dispatcher.getCodi());
        dispatcherInfo.setControlAccessActiu(new Boolean(Messages.getString("ServerServiceImpl.53").equals(dispatcher.getControlAcces()))); //$NON-NLS-1$

        Collection<ControlAccessEntity> acl = dispatcher.getControlAccess();
        ControlAccessEntityDao aclDao = getControlAccessEntityDao();
        dispatcherInfo.getControlAcces().addAll(aclDao.toControlAccesList(acl));

        return dispatcherInfo;
    }

    @Override
    protected Password handleGetAccountPassword(String userId, String dispatcherId) throws Exception {
    	AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(userId, dispatcherId);
    	if (acc == null)
    		return null;
    	
    	Password p = getSecretStoreService().getPassword(acc.getId());
    	if (p == null && acc.getType().equals(AccountType.USER))
    	{
    		for (UserAccountEntity uae: acc.getUsers())
    		{
    			Usuari usuari = getUsuariEntityDao().toUsuari(uae.getUser());
    			p = getSecretStoreService().getSecret(usuari, "dompass/"+acc.getDispatcher().getDomini().getId()); //$NON-NLS-1$
    		}
    	}
    		
    	return p;
    }

    @Override
    protected Password handleGenerateFakePassword(String passDomain) throws Exception {
        DominiContrasenyaEntity dc = getDominiContrasenyaEntityDao().findByCodi(passDomain);

        return getInternalPasswordService().generateFakePassword(null, dc);
    }

    @Override
    protected PoliticaContrasenya handleGetUserPolicy(String account, String dispatcher)
            throws Exception {
        if (dispatcher == null) 
        	dispatcher = getInternalPasswordService().getDefaultDispatcher();
        
        DispatcherEntity dispatcherEntity = getDispatcherEntityDao().findByCodi(dispatcher);

        AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(account, dispatcher);
        if (acc == null)
            throw new InternalErrorException(String.format(Messages.getString("ServerServiceImpl.unknownAccount"), account, dispatcher)); //$NON-NLS-1$
        
        if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uae: acc.getUsers())
        	{
                for (PoliticaContrasenyaEntity pc: dispatcherEntity.getDomini().getPoliticaContrasenyes()) {
                    if (pc.getTipusUsuariDomini().getCodi().equals(uae.getUser().getTipusUsuari().getCodi()))
                        return getPoliticaContrasenyaEntityDao().toPoliticaContrasenya(pc);
                }
        	}
        } else {
            for (PoliticaContrasenyaEntity pc: dispatcherEntity.getDomini().getPoliticaContrasenyes()) {
                if (pc.getTipusUsuariDomini().getCodi().equals(acc.getPasswordPolicy().getCodi()))
                    return getPoliticaContrasenyaEntityDao().toPoliticaContrasenya(pc);
            }
        }
        return null;
    }

	@Override
	protected Password handleGetOrGenerateUserPassword(String account,
			String dispatcherId) throws Exception {

		
        Password secret = getAccountPassword(account, dispatcherId);
        if (secret == null) {
            AccountEntity acc = getAccountEntityDao().findByNameAndDispatcher(account, dispatcherId);
            if (acc.getType().equals(AccountType.USER))
            {
            	for (UserAccountEntity uae: acc.getUsers())
            	{
            		DominiContrasenyaEntity dce = acc.getDispatcher().getDomini();
            		
               		secret = getInternalPasswordService().generateNewPassword(uae.getUser(), dce, false);
                	getInternalPasswordService().storePassword(uae.getUser(), dce, secret, true);
               	
                    String secretName =  "dompass/"+dce.getId(); //$NON-NLS-1$
            		Usuari u = getUsuariEntityDao().toUsuari(uae.getUser());
                	getSecretStoreService().putSecret(u, secretName, secret);
            	}
            } else {
           		secret = getInternalPasswordService().generateNewAccountPassword(acc, false);
            	// getInternalPasswordService().storeAccountPassword(acc, secret, true, null);
           	
            	getSecretStoreService().setPassword(acc.getId(), secret);
            }
        }
        return secret;
	}


	@Override
	protected Usuari handleGetUserInfo(long userId) throws Exception {
		return getUsuariEntityDao().toUsuari(getUsuariEntityDao().load(new Long(userId)));
	}

	@Override
	protected String handleGetDefaultDispatcher() throws Exception {
		return getInternalPasswordService().getDefaultDispatcher();
	}

	@Override
	protected Collection<RolGrant> handleGetAccountRoles(String account,
			String dispatcherId) throws Exception {
        AccountEntity accountEntity = getAccountEntityDao().findByNameAndDispatcher(account, dispatcherId);
        		

        return getAplicacioService().findEffectiveRolGrantByAccount(accountEntity.getId());
	}

	@Override
	protected Collection<RolGrant> handleGetAccountExplicitRoles(String account,
			String dispatcherId) throws Exception {
        AccountEntity accountEntity = getAccountEntityDao().findByNameAndDispatcher(account, dispatcherId);
		

        return getAplicacioService().findRolGrantByAccount(accountEntity.getId());
	}

	@Override
	protected Collection<UserAccount> handleGetUserAccounts(long userId,
			String dispatcherId) throws Exception {
		UsuariEntity usuari = getUsuariEntityDao().load(new Long(userId));
		Collection<UserAccount> accounts = new LinkedList<UserAccount>();
		List<AccountEntity> accountList = getAccountEntityDao().findByUsuariAndDispatcher(usuari.getCodi(), dispatcherId);
		for (AccountEntity ae: accountList)
		{
			if (ae.getType().equals (AccountType.USER))
				for (UserAccountEntity uae: ae.getUsers())
					accounts.add (getUserAccountEntityDao().toUserAccount(uae));
		}

		return accounts;
		
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetUserGroups(java.lang.Long)
	 */
	@Override
	protected Collection<Grup> handleGetUserGroups (Long userId) throws Exception
	{
    	UsuariEntity user = getUsuariEntityDao().load(userId);

    	HashMap<String, GrupEntity>grups = new HashMap<String, GrupEntity>();
    	
    	if (! grups.containsKey(user.getGrupPrimari().getCodi()))
    		grups.put(user.getGrupPrimari().getCodi(), user.getGrupPrimari());
        for (Iterator<UsuariGrupEntity> it = user.getGrupsSecundaris().iterator(); it.hasNext();) {
            UsuariGrupEntity uge = it.next();
        	if (! grups.containsKey(uge.getGrup().getCodi()))
        		grups.put(uge.getGrup().getCodi(), uge.getGrup());
        }
        
        return getGrupEntityDao().toGrupList(grups.values());
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetUserGroupsHierarchy(java.lang.Long)
	 */
	@Override
	protected Collection<Grup> handleGetUserGroupsHierarchy (Long userId)
					throws Exception
	{
    	UsuariEntity user = getUsuariEntityDao().load(userId);

    	HashMap<String, GrupEntity>grups = new HashMap<String, GrupEntity>();
    	
    	if (! grups.containsKey(user.getGrupPrimari().getCodi()))
    		grups.put(user.getGrupPrimari().getCodi(), user.getGrupPrimari());
        for (Iterator<UsuariGrupEntity> it = user.getGrupsSecundaris().iterator(); it.hasNext();) {
            UsuariGrupEntity uge = it.next();
        	if (! grups.containsKey(uge.getGrup().getCodi()))
        		grups.put(uge.getGrup().getCodi(), uge.getGrup());
        }

        LinkedList<Grup> values = new LinkedList<Grup>();
        HashSet<String> keys = new HashSet<String>(grups.keySet());
        GrupEntityDao grupDao = getGrupEntityDao();
        for (GrupEntity grup: grups.values())
        {
        	while ( grup != null && ! keys.contains(grup.getCodi()))
        	{
        		keys.add(grup.getCodi());
        		values.add(grupDao.toGrup(grup));
        		grup = grup.getPare();
        	};
        }

        return getGrupEntityDao().toGrupList(grups.values());
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetPlugin(java.lang.String)
	 */
	@Override
	protected Plugin handleGetPlugin (String className) throws Exception
	{
        AgentDescriptorEntity entity = getAgentDescriptorEntityDao().findByClass(className);
        if (entity == null) throw new InternalErrorException(Messages.getString("ServerServiceImpl.pluginNotFound")+className); //$NON-NLS-1$
        Plugin p = new Plugin();
        if (entity.getModule() == null && entity.getPlugin() != null)
        {
        	p.setContent(entity.getPlugin().getContent());
        	p.setName(entity.getPlugin().getName());
        	p.setVersion(entity.getPlugin().getVersion());
        }
        else
        {
        	p.setContent(entity.getModule().getContents());
        	p.setVersion(entity.getModule().getPlugin().getVersion());
        	p.setName(entity.getModule().getName());
        }
        return p;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handlePropagateOBUser(es.caib.seycon.ng.comu.Usuari)
	 */
	@Override
	protected Map handlePropagateOBUser (Usuari usuari) throws Exception
	{
		Tasca tasca = new Tasca();
		tasca.setTransa(TaskHandler.UPDATE_USER);
		tasca.setUsuari(usuari.getCodi());
		TaskHandler th = new TaskHandler();
		th.setTask(tasca);
		return getTaskQueue().processOBTask(th);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleUpdateExpiredPasswords(es.caib.seycon.ng.comu.Usuari, boolean)
	 */
	@Override
	protected boolean handleUpdateExpiredPasswords (Usuari usuari, boolean externalAuth)
					throws Exception
	{
		UsuariEntity usuariEntity = getUsuariEntityDao().load(usuari.getId());
		if (usuariEntity != null)
		{
			return getInternalPasswordService().updateExpiredPasswords(usuariEntity, externalAuth);
		}
		else
			return false;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetExpiredPasswordDomains(es.caib.seycon.ng.comu.Usuari)
	 */
	@Override
	protected Collection<DominiContrasenya> handleGetExpiredPasswordDomains (
					Usuari usuari) throws Exception
	{
		UsuariEntity usuariEntity = getUsuariEntityDao().load(usuari.getId());
		if (usuariEntity != null)
		{
			Collection<DominiContrasenyaEntity> list = getInternalPasswordService().enumExpiredPasswords(usuariEntity); 
			return getDominiContrasenyaEntityDao().toDominiContrasenyaList(list);
		}
		else
			return Collections.EMPTY_LIST;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetMyConfig()
	 */
	@Override
	protected Properties handleGetMyConfig () throws Exception
	{
		ConfigurationManager cfgManager = new ConfigurationManager();
		String account = Security.getCurrentAccount();
		return cfgManager.getProperties(account);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetMainJar()
	 */
	@Override
	protected void handleGetMainJar () throws Exception
	{
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetAddonList()
	 */
	@Override
	protected List<String> handleGetAddonList () throws Exception
	{
		return new JarExtractor().getActiveModules();
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.servei.ServerServiceBase#handleGetAddonJar(es.caib.seycon.ng.comu.ServerPluginModule)
	 */
	@Override
	protected byte[] handleGetAddonJar (String addon) throws Exception
	{
		throw new UnsupportedOperationException();
	}

	@Override
	protected String handleTranslate(String domain, String column1)
			throws Exception {
		Collection<AttributeTranslation> list = getAttributeTranslationService().findByColumn1(domain, column1);
		if (list.isEmpty())
			return null;
		else if (list.size() > 1)
			throw new IllegalArgumentException(String.format("More than one translation available for value %s on domain %s",
					column1, domain));
		else
			return list.iterator().next().getColumn2();
	}

	@Override
	protected String handleReverseTranslate(String domain, String column2)
			throws Exception {
		Collection<AttributeTranslation> list = getAttributeTranslationService().findByColumn2(domain, column2);
		if (list.isEmpty())
			return null;
		else if (list.size() > 1)
			throw new IllegalArgumentException(String.format("More than one translation available for value %s on domain %s",
					column2, domain));
		else
			return list.iterator().next().getColumn1();
	}

	@Override
	protected Collection<AttributeTranslation> handleTranslate2(String domain,
			String column1) throws Exception {
		return getAttributeTranslationService().findByColumn1(domain, column1);
	}

	@Override
	protected Collection<AttributeTranslation> handleReverseTranslate2(
			String domain, String column2) throws Exception {
		return getAttributeTranslationService().findByColumn2(domain, column2);
	}

	@Override
	protected Account handleGetAccountInfo(String accountName,
			String dispatcherId) throws Exception {
		return getAccountService().findAccount(accountName, dispatcherId);
	}
}

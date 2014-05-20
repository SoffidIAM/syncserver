package es.caib.seycon.ng.sync.servei;

import java.net.InetAddress;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import es.caib.seycon.ng.ServiceLocator;
import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Maquina;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownNetworkException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.model.AccountEntity;
import es.caib.seycon.ng.model.AccountEntityDao;
import es.caib.seycon.ng.model.MaquinaEntity;
import es.caib.seycon.ng.model.MaquinaEntityDao;
import es.caib.seycon.ng.model.RegistreAccesEntity;
import es.caib.seycon.ng.model.RegistreAccesEntityDao;
import es.caib.seycon.ng.model.ServeiEntity;
import es.caib.seycon.ng.model.ServeiEntityDao;
import es.caib.seycon.ng.model.UserAccountEntity;
import es.caib.seycon.ng.model.UsuariEntity;

public class LogCollectorServiceImpl extends LogCollectorServiceBase {

    @Override
    protected Date handleGetLastLogEntryDate(String dispatcher) throws Exception {
        RegistreAccesEntityDao dao = getRegistreAccesEntityDao();
        Date d = dao.findLastDateByDispatcher(dispatcher);
        return d;
    }

    protected ServeiEntity findServei(String codi) throws InternalErrorException {
        ServeiEntityDao dao = getServeiEntityDao();
        ServeiEntity result = dao.findByCodi(codi);
        if (result == null)
        {
        	result = dao.newServeiEntity();
        	result.setCodi(codi);
        	result.setDescripcio(codi);
        	dao.create(result);
        }
        return result;
    }

    protected UsuariEntity findUser(String codi, String dispatcher) throws UnknownUserException {
        AccountEntityDao dao = getAccountEntityDao();
        
        AccountEntity acc = dao.findByNameAndDispatcher(codi, dispatcher);
        if (acc == null)
            throw new UnknownUserException(String.format("Unknown user %s on domain %s", codi, dispatcher));
        else if (acc.getType().equals(AccountType.USER))
        {
        	for (UserAccountEntity uac: acc.getUsers())
        	{
        		return uac.getUser();
        	}
        	return null;
        }
        else
        	return null;
    }

    private MaquinaEntity findMaquina(String server) throws UnknownHostException, InternalErrorException {
        MaquinaEntityDao dao = getMaquinaEntityDao();
        MaquinaEntity maq = dao.findByNom(server);
        if (maq == null)
            maq = dao.findByAdreca(server);
        if (maq == null)
        {
        	try
			{
				InetAddress address = InetAddress.getByName(server);
				maq = dao.findByAdreca(address.getHostAddress());
				if (maq == null)
				{
					String serial = server + ":"+ address.getHostAddress();
					Maquina m = ServiceLocator.instance().getXarxaService().registerDynamicIP(server, address.getHostAddress(), serial);
					maq = dao.findById(m.getId());
				}
			}
			catch (java.net.UnknownHostException e)
			{
			}
			catch (UnknownNetworkException e)
			{
	            throw new UnknownHostException(server);
			}
        }
        if (maq == null)
            throw new UnknownHostException(server);
        return maq;
    }

    @Override
    protected void handleRegisterLogon(String dispatcher, String sessionId, Date date, String user,
            String server, String client, String protocol, String info) throws Exception {

        List<RegistreAccesEntity> result = getRegistreAccesEntityDao().findByAgentSessioIdStartDate(dispatcher, sessionId, date, findMaquina(server));
        if (result.isEmpty()) {
    
        	RegistreAccesEntity rac = getRegistreAccesEntityDao().newRegistreAccesEntity();
            MaquinaEntityDao dao = getMaquinaEntityDao();
            
            
            
            rac.setInformacio(info);
            rac.setCodeAge(dispatcher);
            rac.setDataFi(null);
            rac.setDataInici(date);
            rac.setIdSessio(sessionId);
            rac.setProtocol(findServei(protocol));
            rac.setTipusAcces("L");
            rac.setUsuari(findUser(user, dispatcher));
            assignHost(rac, server);
            assignClientHost(rac, client);
            
            getRegistreAccesEntityDao().create(rac);
        }
    }

	private void assignHost (RegistreAccesEntity rac, String server)
					throws InternalErrorException
	{
		try {
        	rac.setServidor(findMaquina(server));
        	rac.setHostName(rac.getServidor().getNom());
        } catch (UnknownHostException e) {
        	rac.setHostName(server);
        }
    	try
    	{
       		InetAddress addr = InetAddress.getByName(rac.getHostName());
       		rac.setHostAddress(addr.getHostAddress());
    	} catch (Exception e )
    	{
    	}
	}

	private void assignClientHost (RegistreAccesEntity rac, String client)
					throws InternalErrorException
	{
		if (client != null && client.length() > 0) {
            try {
                rac.setClient(findMaquina(client));
            	rac.setClientHostName(rac.getClient().getNom());
            } catch (UnknownHostException e) {
            	rac.setClientHostName(client);
            }
        	try
        	{
           		InetAddress addr = InetAddress.getByName(rac.getClientHostName());
           		rac.setClientAddress(addr.getHostAddress());
        	} catch (Exception e )
        	{
        	}
        }
	}

    @Override
    protected void handleRegisterFailedLogon(String dispatcher, String sessionId, Date date,
            String user, String server, String client, String protocol, String info) throws Exception {
        List<RegistreAccesEntity> result = getRegistreAccesEntityDao().findByAgentSessioIdStartDate(dispatcher, sessionId, date, findMaquina(server));
        if (result.isEmpty()) {
    
            RegistreAccesEntity rac = getRegistreAccesEntityDao().newRegistreAccesEntity();
            
            
            try {
                rac.setCodeAge(dispatcher);
                rac.setDataFi(null);
                rac.setDataInici(date);
                rac.setIdSessio(sessionId);
                rac.setInformacio(info);
                rac.setProtocol(findServei(protocol));
                rac.setTipusAcces("D");
                rac.setUsuari(findUser(user, dispatcher));
                assignHost(rac, server);
                assignClientHost(rac, client);
                
                getRegistreAccesEntityDao().create(rac);
            } catch (UnknownUserException e) {
            	LogFactory.getLog(getClass()).warn ( String.format("Unknown user %s loading logs: %s", user, e.getMessage()));
            }
        }
    }

    @Override
    protected void handleRegisterLogoff(String dispatcher, String sessionId, Date date,
            String user, String server, String client, String protocol, String info) throws Exception {
        
        List<RegistreAccesEntity> result = getRegistreAccesEntityDao().findByAgentSessioIdEndDate(dispatcher, sessionId, date, findMaquina(server));
        if (result.isEmpty()) {
    
            RegistreAccesEntity rac = getRegistreAccesEntityDao().newRegistreAccesEntity();
            rac.setCodeAge(dispatcher);
            rac.setDataFi(date);
            rac.setDataInici(date);
            rac.setIdSessio(sessionId);
            rac.setInformacio("Logoff without logon. "+info);
            rac.setProtocol(findServei(protocol));
            rac.setTipusAcces("L");
            rac.setUsuari(findUser(user, dispatcher));
            assignHost(rac, server);
            assignClientHost(rac, client);
            
            getRegistreAccesEntityDao().create(rac);
        } else {
            for (Iterator<RegistreAccesEntity> it = result.iterator(); it.hasNext(); ) {
                RegistreAccesEntity rac = it.next();
                if (rac.getDataFi() == null)
                {
                	rac.setDataFi(date);
                	getRegistreAccesEntityDao().update(rac);
                }
            }
        }
    }

}

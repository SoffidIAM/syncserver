package com.soffid.iam.sync.service;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Host;
import com.soffid.iam.model.AccessLogEntity;
import com.soffid.iam.model.AccessLogEntityDao;
import com.soffid.iam.model.AccountEntity;
import com.soffid.iam.model.AccountEntityDao;
import com.soffid.iam.model.HostEntity;
import com.soffid.iam.model.HostEntityDao;
import com.soffid.iam.model.ServiceEntity;
import com.soffid.iam.model.ServiceEntityDao;
import com.soffid.iam.model.UserAccountEntity;
import com.soffid.iam.model.UserEntity;
import com.soffid.iam.model.criteria.CriteriaSearchConfiguration;
import com.soffid.iam.sync.service.LogCollectorServiceBase;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownNetworkException;
import es.caib.seycon.ng.exception.UnknownUserException;

import java.net.InetAddress;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.util.InetAddressUtils;
import org.jfree.util.Log;

public class LogCollectorServiceImpl extends LogCollectorServiceBase {
	org.apache.commons.logging.Log log = LogFactory.getLog(getClass());
	
    @Override
    protected Date handleGetLastLogEntryDate(String dispatcher) throws Exception {
        AccessLogEntityDao dao = getAccessLogEntityDao();
        Date d = dao.findLastDateBySystem(dispatcher);
        return d;
    }

    protected ServiceEntity findServei(String codi) throws InternalErrorException {
        ServiceEntityDao dao = getServiceEntityDao();
        ServiceEntity result = dao.findByName(codi);
        if (result == null)
        {
        	result = dao.newServiceEntity();
        	result.setName(codi);
        	result.setDescription(codi);
        	dao.create(result);
        }
        return result;
    }

    protected UserEntity findUser(String codi, String dispatcher) throws UnknownUserException {
        AccountEntityDao dao = getAccountEntityDao();
        
        AccountEntity acc = dao.findByNameAndSystem(codi, dispatcher);
        if (acc == null) {
            log.info(String.format("Unknown user %s on domain %s", codi, dispatcher));
        	return null;
        }
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

    private HostEntity findMaquina(String server) throws InternalErrorException, UnknownHostException {
        HostEntityDao dao = getHostEntityDao();
        HostEntity maq = dao.findByName(server);
        CriteriaSearchConfiguration single = new CriteriaSearchConfiguration();
        single.setMaximumResultSize(1);
        if (maq == null)
        {
			for (HostEntity maq2: dao.findByIP(single , server))
        	{
        		maq = maq2;
        		break;
        	}
        }
        if (maq == null)
        {
        	try
			{
				InetAddress address = InetAddress.getByName(server);
				for (HostEntity maq2: dao.findByIP(single, address.getHostAddress()))
				{
					maq = maq2;
					break;
				}
				if (maq == null)
				{
					String serial = server + ":"+ address.getHostAddress();
					Host m = ServiceLocator.instance().getNetworkService().registerDynamicIP(server, address.getHostAddress(), serial);
					maq = dao.load(m.getId());
				}
			}
			catch (java.net.UnknownHostException e)
			{
			}
			catch (UnknownNetworkException e)
			{
				return null;
			}
        }
        return maq;
    }

    @Override
    protected void handleRegisterLogon(String dispatcher, String sessionId, Date date, String user,
            String server, String client, String protocol, String info) throws Exception {

    	HostEntity host = findMaquina(server);
        List<AccessLogEntity> result = host == null ?
        		getAccessLogEntityDao().findAccessLogBySessionIDAndStartDate2(dispatcher, sessionId, date,  server) :
        		getAccessLogEntityDao().findAccessLogBySessionIDAndStartDate(dispatcher, sessionId, date, host);
        if (result.isEmpty()) {
    
        	AccessLogEntity rac = getAccessLogEntityDao().newAccessLogEntity();
            HostEntityDao dao = getHostEntityDao();
            
            rac.setInformation(info);
            rac.setSystem(dispatcher);
            rac.setEndDate(null);
            rac.setStartDate(date);
            rac.setSessionId(sessionId);
            rac.setProtocol(findServei(protocol));
            rac.setAccessType("L");
            rac.setUser(findUser(user, dispatcher));
            if (rac.getUser() != null) {
            	assignHost(rac, host, server);
            	assignClientHost(rac, client);
            	
            	getAccessLogEntityDao().create(rac);

            }
        }
    }

	private void assignHost(AccessLogEntity rac, HostEntity host, String server) throws InternalErrorException {
		if (host == null) {
			rac.setServer(null);
			rac.setHostName(server);
		} else {
        	rac.setServer(host);
        	rac.setHostName(host.getName());
		}
    	try
    	{
       		InetAddress addr = InetAddress.getByName(rac.getHostName());
       		rac.setHostAddress(addr.getHostAddress());
    	} catch (Exception e )
    	{
    	}
	}

	private void assignClientHost(AccessLogEntity rac, String client) throws InternalErrorException, UnknownHostException {
		if (client != null && client.length() > 0) {
	    	String clientIp = null;
	    	if (client != null && client.contains(" ")) {
	    		clientIp = client.substring(client.indexOf(" ")+1);
	    		client = client.substring(0, client.indexOf(" "));
	    	}
	    	HostEntity host = findMaquina(client);
			if (host == null) {
				rac.setClient(null);
				rac.setClientHostName(client);
			} else {
				rac.setClient(findMaquina(client));
				rac.setClientHostName(rac.getClient().getName());
				rac.setClientAddress(host.getHostIP());
			}
        	try
        	{
        		if (InetAddressUtils.isIPv4Address(client) ||
        				InetAddressUtils.isIPv6Address(client))
        			rac.setClientAddress(client);
        		if (clientIp != null)
        			rac.setClientAddress(clientIp);
        		else {
        			InetAddress addr = InetAddress.getByName(rac.getClientHostName());
        			rac.setClientAddress(addr.getHostAddress());
        		}
        	} catch (Exception e )
        	{
        	}
        }
	}

    @Override
    protected void handleRegisterFailedLogon(String dispatcher, String sessionId, Date date,
            String user, String server, String client, String protocol, String info) throws Exception {
    	HostEntity host = findMaquina(server);
        List<AccessLogEntity> result = host == null ?
        		getAccessLogEntityDao().findAccessLogBySessionIDAndStartDate2(dispatcher, sessionId, date,  server) :
        		getAccessLogEntityDao().findAccessLogBySessionIDAndStartDate(dispatcher, sessionId, date, host);
        if (result.isEmpty()) {
    
            AccessLogEntity rac = getAccessLogEntityDao().newAccessLogEntity();
            
            
            try {
                rac.setSystem(dispatcher);
                rac.setEndDate(null);
                rac.setStartDate(date);
                rac.setSessionId(sessionId);
                rac.setInformation(info);
                rac.setProtocol(findServei(protocol));
                rac.setAccessType("D");
                rac.setUser(findUser(user, dispatcher));
                if (rac.getUser() != null) {
	                assignHost(rac, host, server);
	                assignClientHost(rac, client);
	                
	                getAccessLogEntityDao().create(rac);
                }
            } catch (UnknownUserException e) {
            	LogFactory.getLog(getClass()).warn ( String.format("Unknown user %s loading logs: %s", user, e.getMessage()));
            }
        }
    }

    @Override
    protected void handleRegisterLogoff(String dispatcher, String sessionId, Date date,
            String user, String server, String client, String protocol, String info) throws Exception {
        
    	HostEntity host = findMaquina(server);
        List<AccessLogEntity> result = host == null ?
        		getAccessLogEntityDao().findAccessLogByAgentAndSessionIDAndEndDate2(dispatcher, sessionId, date,  server) :
        		getAccessLogEntityDao().findAccessLogByAgentAndSessionIDAndEndDate(dispatcher, sessionId, date, host);
        if (result.isEmpty()) {
    
            AccessLogEntity rac = getAccessLogEntityDao().newAccessLogEntity();
            rac.setSystem(dispatcher);
            rac.setEndDate(date);
            rac.setStartDate(date);
            rac.setSessionId(sessionId);
            rac.setInformation("Logoff without logon. " + info);
            rac.setProtocol(findServei(protocol));
            rac.setAccessType("L");
            rac.setUser(findUser(user, dispatcher));
            if (rac.getUser() != null) {
	            assignHost(rac, host, server);
	            assignClientHost(rac, client);
	            
	            getAccessLogEntityDao().create(rac);
            }
        } else {
            for (Iterator<AccessLogEntity> it = result.iterator(); it.hasNext(); ) {
                AccessLogEntity rac = it.next();
                if (rac.getEndDate() == null) {
                    rac.setEndDate(date);
                    getAccessLogEntityDao().update(rac);
                }
            }
        }
    }

}

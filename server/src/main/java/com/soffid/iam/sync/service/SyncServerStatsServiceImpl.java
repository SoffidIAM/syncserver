package com.soffid.iam.sync.service;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import es.caib.seycon.ng.utils.Security;

import java.util.List;

public class SyncServerStatsServiceImpl extends SyncServerStatsServiceBase {
	static Map<String, Map<String, Map<String, LinkedList<Stats> > > > stats = new Hashtable<String, Map<String,Map<String,LinkedList<Stats>>>>();
	
	@Override
	protected Map<String, int[]> handleGetStats(String metric, int seconds, int step) throws Exception {
		HashMap<String, int[]> data = new HashMap<String, int[]>();
		String tenant = Security.getCurrentTenantName();
		Map<String,Map<String,LinkedList<Stats>>> tenantStats = stats.get(tenant);
		if (tenantStats == null) return data;
		Map<String, LinkedList<Stats>> metricStats = tenantStats.get(metric);
		if (metricStats == null) return data;
		int size = seconds / step;
		for (String submetric: new LinkedList<String> ( metricStats.keySet()))
		{
			long now = (long) (System.currentTimeMillis() / 1000L);
			now = now - (now % step);
			int serie[] = new int[ size ];
			LinkedList<Stats> statsList = metricStats.get(submetric);
			synchronized (statsList)
			{
				Iterator<Stats> iterator = statsList.iterator();
				while (iterator.hasNext())
				{
					Stats s = iterator.next();
					if (s.second <= now)
					{
						int position = (int) ( size - 1 - (now - s.second) / step ) ;
						if (position < 0)
							break;
						serie [position] += s.times;
					}
				}
			}
			data.put(submetric, serie);
		}
		return data;
	}

	@Override
	protected void handleRegister(String metric, String submetric, int value) throws Exception {
		String tenant = Security.getCurrentTenantName();
		Map<String,Map<String,LinkedList<Stats>>> tenantStats = stats.get(tenant);
		if (tenantStats == null)
		{
			tenantStats = new Hashtable<String, Map<String,LinkedList<Stats>>>();
			stats.put(tenant, tenantStats);
		}
		Map<String, LinkedList<Stats>> metricStats = tenantStats.get(metric);
		if (metricStats == null)
		{
			metricStats = new Hashtable<String, LinkedList<Stats>>();
			tenantStats.put(metric, metricStats);
		}
		LinkedList<Stats> submetricStats = metricStats.get(submetric);
		if (submetricStats == null)
		{
			submetricStats = new LinkedList<Stats>();
			metricStats.put(submetric, submetricStats);
		}
		long second = (System.currentTimeMillis()/1000L);
		Stats stat = null;
		synchronized (submetricStats)
		{
			if (! submetricStats.isEmpty())
			{
				stat = submetricStats.getFirst();
				if (stat.second != second) stat = null;
			}
			if (stat == null)
			{
				stat = new Stats();
				stat.second = second;
				stat.times = 0;
				submetricStats.push(stat);
			}
			stat.times += value;
			if (submetricStats.size() > 60 * 60) // 60 minutes maximum
				submetricStats.removeLast();
		}
	}

}

class Stats {
	long second;
	int times;
}
/**
 * 
 */
package com.soffid.iam.sync.engine.extobj;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.soffid.iam.sync.intf.ExtensibleObject;
import com.soffid.iam.sync.service.ServerService;

public class ExtensibleObjectVars implements Map<String,Object>
{
	private ExtensibleObject object;
	private ServerService serverService;
	private BSHAgentObject agent;
	Map<String, Object> externalMap = new HashMap<String, Object>();
	
	/**
	 * @param parent
	 * @param classManager
	 * @param name
	 * @param dades
	 * @param ead
	 * @param accountName
	 */
	@SuppressWarnings("unchecked")
	public ExtensibleObjectVars (
					ExtensibleObject object,
					ServerService serverService, BSHAgentObject bshAgentObject)
	{
		this.object = object;
		this.serverService = serverService;
		this.agent = bshAgentObject;
	}

	boolean inSetVariable = false;
	
	@Override
	public int size() {
		return object.size() + 3;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean containsKey(Object key) {
		if ("serverService".equals(key))
			return true;
		if ("dispatcherService".equals(key))
			return true;
		return true;
//		return object.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return object.containsValue(value);
	}

	@Override
	public Object get(Object key) {
		if ("serverService".equals(key))
			return serverService;
		if ("dispatcherService".equals(key))
			return agent;
		return object.get(key);
	}

	@Override
	public Object put(String key, Object value) {
		return object.put(key, value);
	}

	@Override
	public Object remove(Object key) {
		return object.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		object.putAll(m);
	}

	@Override
	public void clear() {
		object.clear();
	}

	@Override
	public Set<String> keySet() {
		HashSet<String> set = new HashSet<String>(object.keySet());
		set.add("serverService");
		set.add("dispatcherService");
		return set;
	}

	@Override
	public Collection<Object> values() {
		LinkedList<Object> l = new LinkedList<Object>(object.values());
		l.add(serverService);
		l.add(agent);
		return l;
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		HashSet<Entry<String, Object>> r = new HashSet<>();
		r.add(new Entry<String,Object>() {
			public String getKey() { return "serverService";}
			public Object getValue() {return serverService;}
			public Object setValue(Object value) {return serverService;}
		});
		r.add(new Entry<String,Object>() {
			public String getKey() { return "dispatcherService";}
			public Object getValue() {return agent;}
			public Object setValue(Object value) {return agent;}
		});
		return r;
	}

}

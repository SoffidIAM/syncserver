package com.soffid.iam.sync.agent;

import java.net.URL;
import java.util.Enumeration;

public class SimpleEnumeration<T> implements Enumeration<T> {

	private Enumeration<T>[] data;
	private int pos;

	public SimpleEnumeration(Enumeration<T>[] tmp) {
		data = tmp;
		pos = 0;
	}

	public boolean hasMoreElements() {
		while ( pos < data.length )
		{
			if ( data[pos].hasMoreElements())
				return true;
			else
				pos ++;
		}
		return false;
	}

	public T nextElement() {
		while ( pos < data.length )
		{
			if ( data[pos].hasMoreElements())
				return data[pos].nextElement();
			else
				pos ++;
		}
		return null;
	}

}

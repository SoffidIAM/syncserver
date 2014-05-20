/**
 * 
 */
package es.caib.seycon.ng.sync.bootstrap;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Soffid
 *
 */
public class NullSqlObjet implements Serializable
{
	int sqlType;
	
	public NullSqlObjet (int type)
	{
		sqlType = type;
	}
	
	/**
	 * @return the sqlType
	 */
	public int getSqlType ()
	{
		return sqlType;
	}

	@Override
	public String toString ()
	{
		return "null";
	}
	
}

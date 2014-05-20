/**
 * 
 */
package es.caib.seycon.ng.sync.replica;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;

import com.soffid.tools.db.persistence.XmlReader;
import com.soffid.tools.db.persistence.XmlWriter;
import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Database;
import com.soffid.tools.db.schema.Table;

/**
 * @author bubu
 *
 */
public class DatabaseRepository
{
	static Database db = null;
	static WeakReference<String> weakSchema = null;
	
	static Hashtable<String, Table> tables = new Hashtable<String, Table>();
	
	public DatabaseRepository() throws IOException, Exception 
	{
		if (db == null)
		{
	    	db = new Database();
	    	XmlReader reader = new XmlReader();
	    	parseResources(db, reader, "core-ddl.xml");
	    	parseResources(db, reader, "plugin-ddl.xml");
	    	for (Table t: db.tables)
	    	{
	    		tables.put(t.name, t);
	    	}
		}
	}

	/**
	 * @return
	 * @throws Exception 
	 * @throws IOException 
	 */
	public String getSchema () throws IOException, Exception
	{
	    String schema = null;	
		if (weakSchema != null)
			schema = weakSchema.get();
		
		if (schema == null)
		{
        	XmlWriter writer = new XmlWriter();
        	ByteArrayOutputStream out = new ByteArrayOutputStream();
        	writer.dump(db, out);
        	out.close ();
        	schema = out.toString("UTF-8");
        	weakSchema = new WeakReference<String>(schema);
		}
		return schema;
	}

	private void parseResources (Database db, XmlReader reader, String path)
					throws IOException, Exception
	{
		Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
						.getResources(path);
		while (resources.hasMoreElements())
		{
			reader.parse(db, resources.nextElement().openStream());
		}
	}
	
	
	public Table getTable (String name)
	{
		return tables.get(name);
	}
	
	public Database getDatabase ()
	{
		return db;
	}
}


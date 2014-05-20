/**
 * 
 */
package es.caib.seycon.ng.sync.replica;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Table;

import es.caib.seycon.ng.sync.bootstrap.NullSqlObjet;
import es.caib.seycon.ng.sync.bootstrap.QueryAction;
import es.caib.seycon.ng.sync.bootstrap.QueryHelper;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;

/**
 * @author bubu
 *
 */
public class TableDumpServlet extends HttpServlet
{

	static public final String PATH ="/tableDump";
	@Override
	protected void doGet (HttpServletRequest req, HttpServletResponse resp)
					throws ServletException, IOException
	{
		String tableName = req.getParameter("table");
		if (tableName != null && !tableName.equals("SC_TASQUE") && ! tableName.equals("SC_TASKLOG") && ! tableName.equals("SC_SEQUENCE"))
		{
			final ObjectOutputStream out = new ObjectOutputStream(resp.getOutputStream());
			Connection conn;
			try
			{
				conn = ConnectionPool.getPool().getPoolConnection();
				try {
					if (ConnectionPool.isThreadOffline())
						throw new ServletException("Master database is offline. Try again later");
					DatabaseRepository db = new DatabaseRepository();

					Table table = db.getTable(tableName);
					QueryHelper qh = new QueryHelper(conn);
					
					StringBuffer select = null;
					final ArrayList<String> cols = new ArrayList<String>();
					
					for (Column column: table.columns)
					{
						if (select == null)
							select = new StringBuffer ("SELECT ");
						else
							select.append(", ");
						select.append(column.name);
						cols.add(column.name);
					}

					if (select != null)
					{
						out.writeObject(cols);
					}
					
					select.append(" FROM ").append(table.name);
					qh.select(select.toString(), new Object[0],  new QueryAction() {
						public void perform (ResultSet rset) throws SQLException,
										IOException
						{
							ArrayList<Object> row = new ArrayList<Object>();
							for (int i = 1; i <= cols.size(); i++)
							{
								Object obj = rset.getObject(i);
								if (obj == null)
								{
									int type = rset.getMetaData().getColumnType(i);
									if (type == Types.BINARY ||
										type == Types.LONGVARBINARY ||
										type == Types.VARBINARY ||
										type == Types.BLOB ||
										type == Types.DATE ||
										type == Types.TIMESTAMP ||
										type == Types.TIME ||
										type == Types.BLOB)
											obj = new NullSqlObjet(type);
								}
								row.add(obj);
							}
							out.writeObject(row);
							out.flush();
						}
					});
					out.writeObject(null);
					out.flush();
					out.close();
				} finally {
					conn.close ();
				}
			}
			catch (Exception e)
			{
				throw new ServletException(e);
			}
		}
	}
	
}

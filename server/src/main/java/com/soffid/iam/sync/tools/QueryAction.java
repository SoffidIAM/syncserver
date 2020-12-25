package com.soffid.iam.sync.tools;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface QueryAction {
	public void perform (ResultSet rset) throws SQLException, IOException;
}

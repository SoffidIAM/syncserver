package es.caib.seycon.ng.sync.servei;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.jetty.Invoker;

public class QueryServiceImpl extends QueryServiceBase {

    @Override
    protected void handleQuery(String path, String contentType, Writer writer) throws Exception {
        SessionFactory sf = (SessionFactory) ServerServiceLocator.instance().getService(
                "sessionFactory");
        Session sessio = sf.getCurrentSession();

        Connection conn = ConnectionPool.getPool().getPoolConnection();
        PreparedStatement stmt = null;
        try {
            java.util.Vector<String> v = new Vector<String>(4);
            java.util.StringTokenizer st = new java.util.StringTokenizer(path, "/");
            while (st.hasMoreElements()) {
                v.addElement(st.nextToken());
            }
            if (v.elementAt(0).equals("user") && v.size() == 2) {
                stmt = conn
                        .prepareStatement("SELECT USU_NOM, USU_PRILLI, NVL(USU_SEGLLI,'') USU_SEGLLI, GRU_CODI, USU_NOMCUR, DCO_CODI, "
                                + "M1.MAQ_NOM MAQUSU_NOM, M2.MAQ_NOM MAQCOR_NOM, M3.MAQ_NOM MAQPRO_NOM, USU_ALCOAN, USU_UNOFES, USU_ACTIU "
                                + "FROM SC_USUARI, SC_MAQUIN M1, SC_MAQUIN M2, SC_MAQUIN M3, SC_DOMCOR, SC_GRUPS "
                                + "WHERE USU_IDDCO = DCO_ID(+) AND USU_IDMAQ = M1.MAQ_ID AND USU_IDMACO = M2.MAQ_ID AND USU_IDMAPR=M3.MAQ_ID AND "
                                + "USU_IDGRU = GRU_ID AND USU_CODI = ?");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user-v2") && v.size() == 2) {
                stmt = conn
                        .prepareStatement("SELECT USU_ID, USU_NOM, USU_PRILLI, NVL(USU_SEGLLI,'') USU_SEGLLI, GRU_CODI, USU_NOMCUR, DCO_CODI, "
                                + "M1.MAQ_NOM MAQUSU_NOM, M2.MAQ_NOM MAQCOR_NOM, M3.MAQ_NOM MAQPRO_NOM, USU_ALCOAN, USU_UNOFES, USU_ACTIU "
                                + "FROM SC_USUARI, SC_MAQUIN M1, SC_MAQUIN M2, SC_MAQUIN M3, SC_DOMCOR, SC_GRUPS "
                                + "WHERE USU_IDDCO = DCO_ID(+) AND USU_IDMAQ = M1.MAQ_ID AND USU_IDMACO = M2.MAQ_ID AND USU_IDMAPR=M3.MAQ_ID AND "
                                + "USU_IDGRU = GRU_ID AND USU_CODI = ?");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3
                    && v.elementAt(2).equals("groups")) {
                stmt = conn
                        .prepareStatement("SELECT GRU_CODI FROM SC_GRUPS, SC_USUARI "
                                + "WHERE GRU_ID = USU_IDGRU AND USU_CODI=? "
                                + "UNION "
                                + "SELECT GRU_CODI FROM SC_GRUPS, SC_USUGRU, SC_USUARI "
                                + "WHERE UGR_IDGRU = GRU_ID AND UGR_IDUSU = USU_ID AND USU_CODI=? ORDER BY GRU_CODI");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3
                    && v.elementAt(2).equals("drives")) {
                stmt = conn.prepareStatement("SELECT 'G:' GRU_UNIOFI, MAQ_NOM, GRU_CODI "
                        + "FROM SC_MAQUIN, SC_GRUPS, SC_USUARI " + "WHERE MAQ_ID=GRU_IDMAQ AND "
                        + "      GRU_ID = USU_IDGRU AND USU_CODI=? " + "UNION "
                        + "SELECT GRU_UNIOFI, MAQ_NOM, GRU_CODI "
                        + "FROM SC_MAQUIN, SC_GRUPS, SC_USUGRU, SC_USUARI "
                        + "WHERE MAQ_ID=GRU_IDMAQ AND GRU_UNIOFI IS NOT NULL AND "
                        + " UGR_IDGRU = GRU_ID AND UGR_IDUSU = USU_ID AND USU_CODI=? "
                        + "ORDER BY 1 DESC");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3
                    && v.elementAt(2).equals("roles")) {
                stmt = conn.prepareStatement("SELECT ROL_NOM, ROL_DESCRI, APL_CODI, APL_NOM, "
                        + "ROL_DEFECT, ROL_CONTRA "
                        + "FROM SC_APLICA, SC_ROLES, SC_ROLUSU, SC_USUARI "
                        + "WHERE APL_ID=ROL_IDAPL AND RLU_IDUSU = USU_ID AND USU_CODI=? AND "
                        + "ROL_ID=RLU_IDROL " + "ORDER BY ROL_NOM");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3
                    && v.elementAt(2).equals("rolesv2")) {
                stmt = conn.prepareStatement("SELECT ROL_NOM, ROL_DESCRI, APL_CODI, APL_NOM, "
                        + "ROL_DEFECT, ROL_CONTRA, DIS_CODI "
                        + "FROM SC_APLICA, SC_ROLES, SC_ROLUSU, SC_USUARI, SC_DISPAT  "
                        + "WHERE APL_ID=ROL_IDAPL AND RLU_IDUSU = USU_ID AND USU_CODI=? AND "
                        + "ROL_ID=RLU_IDROL AND DIS_ID=ROL_IDDISPAT ORDER BY ROL_NOM");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3
                    && v.elementAt(2).equals("printers")) {
                stmt = conn.prepareStatement("SELECT UIM_IDIMP, UIM_ORDRE "
                        + "FROM  SC_USUIMP, SC_USUARI " + "WHERE UIM_IDUSU=USU_ID AND USU_CODI =? "
                        + "UNION " + "SELECT GIM_IDIMP, GIM_ORDRE "
                        + "FROM SC_GRUIMP, SC_USUGRU, SC_USUARI "
                        + "WHERE GIM_IDGRU = UGR_IDGRU AND UGR_IDUSU = USU_ID AND USU_CODI=? "
                        + "UNION " + "SELECT GIM_IDIMP, GIM_ORDRE " + "FROM SC_GRUIMP, SC_USUARI "
                        + "WHERE GIM_IDGRU = USU_IDGRU AND USU_CODI=? " + "ORDER BY 2");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
                stmt.setString(3, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3
                    && v.elementAt(2).equals("printers-v2")) {
                stmt = conn
                        .prepareStatement("SELECT MAQ_NOM, IMP_CODI, UIM_ORDRE "
                                + "FROM  SC_MAQUIN, SC_IMPRES, SC_USUIMP, SC_USUARI "
                                + "WHERE MAQ_ID=IMP_IDMAQ AND IMP_ID=UIM_IDIMP AND UIM_IDUSU=USU_ID AND USU_CODI =? "
                                + "UNION "
                                + "SELECT MAQ_NOM, IMP_CODI, GIM_ORDRE "
                                + "FROM SC_MAQUIN, SC_IMPRES, SC_GRUIMP, SC_USUGRU, SC_USUARI "
                                + "WHERE MAQ_ID=IMP_IDMAQ AND IMP_ID=GIM_IDIMP AND GIM_IDGRU = UGR_IDGRU AND UGR_IDUSU = USU_ID AND USU_CODI=? "
                                + "UNION "
                                + "SELECT MAQ_NOM, IMP_CODI, GIM_ORDRE "
                                + "FROM SC_MAQUIN, SC_IMPRES, SC_GRUIMP, SC_USUARI "
                                + "WHERE MAQ_ID=IMP_IDMAQ AND IMP_ID=GIM_IDIMP AND GIM_IDGRU = USU_IDGRU AND USU_CODI=? "
                                + "ORDER BY 2");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
                stmt.setString(3, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("user") && v.size() == 3) {
                stmt = conn
                        .prepareStatement("SELECT DUS_VALOR FROM SC_TIPDAD, SC_DADUSU, SC_USUARI "
                                + "WHERE TDA_CODI=? AND DUS_TDAID=TDA_ID AND DUS_IDUSU = USU_ID AND USU_CODI=? ");
                stmt.setString(1, (String) v.elementAt(2));
                stmt.setString(2, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("host") && v.size() == 2) {
                // Eliminem àlies: emprar /host/NOMHOST/alias
                stmt = conn.prepareStatement("SELECT MAQ_NOM, MAQ_ADRIP, MAQ_DESCRI, MAQ_PARDHC, "
                        + "MAQ_SISOPE, XAR_CODI, MAQ_ADRMAC " + "FROM SC_MAQUIN, SC_XARXES "
                        + "WHERE XAR_ID=MAQ_IDXAR AND MAQ_NOM=?");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("host") // ÀLIES del host
                    && v.size() == 3 && v.elementAt(2).equals("alias")) {
                stmt = conn.prepareStatement("SELECT MAQ_NOM, MAL_ALIAS "
                        + "FROM SC_MAQUIN, SC_XARXES, SC_MAQUINALIAS "
                        + "WHERE XAR_ID=MAQ_IDXAR AND MAL_MAQID=MAQ_ID AND MAQ_NOM=?");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("host") && v.size() == 3
                    && v.elementAt(2).equals("printers")) {
                stmt = conn
                        .prepareStatement("SELECT IMP_ID, 1 ORDRE "
                                + "FROM  SC_MAQUIN, SC_IMPRES "
                                + "WHERE MAQ_ID=IMP_IDMAQ AND SUBSTR(LOWER(IMP_CODI),2,6) = ? AND IMP_LOCAL='N'");
                stmt.setString(1, ((String) v.elementAt(1)).substring(1, 7).toLowerCase());
            } else if (v.elementAt(0).equals("host") && v.size() == 3
                    && v.elementAt(2).equals("printers-v2")) {
                stmt = conn
                        .prepareStatement("SELECT MAQ_NOM, IMP_CODI, 1 ORDRE "
                                + "FROM  SC_MAQUIN, SC_IMPRES "
                                + "WHERE MAQ_ID=IMP_IDMAQ AND SUBSTR(LOWER(IMP_CODI),2,6) = ? AND IMP_LOCAL='N'");
                stmt.setString(1, ((String) v.elementAt(1)).substring(1, 7).toLowerCase());
            } else if (v.elementAt(0).equals("hosts") && v.size() == 1) {// Eliminem
                                                                         // el
                                                                         // àlies
                                                                         // (veure
                                                                         // seguent)
                stmt = conn.prepareStatement("SELECT MAQ_NOM, MAQ_ADRIP, MAQ_DESCRI, MAQ_PARDHC, "
                        + "MAQ_SISOPE, XAR_CODI, MAQ_ADRMAC " + "FROM SC_MAQUIN, SC_XARXES "
                        + "WHERE XAR_ID=MAQ_IDXAR AND MAQ_ADRIP IS NOT NULL");
            } else if (v.elementAt(0).equals("hosts") // Obtenim els ÀLIES dels
                                                      // hosts
                    && v.size() == 2 && v.elementAt(1).equals("alias")) {
                stmt = conn.prepareStatement("SELECT MAQ_NOM, MAL_ALIAS "
                        + "FROM SC_MAQUIN, SC_XARXES, SC_MAQUINALIAS "
                        + "WHERE XAR_ID=MAQ_IDXAR AND MAQ_ADRIP IS NOT NULL AND MAQ_ID=MAL_MAQID");
            } else if (v.elementAt(0).equals("maildomains") && v.size() == 1) {
                stmt = conn.prepareStatement("SELECT DCO_CODI, DCO_DESCRI FROM SC_DOMCOR");
            } else if (v.elementAt(0).equals("network") && v.size() == 2) {
                stmt = conn
                        .prepareStatement("SELECT XAR_CODI, XAR_ADRIP, XAR_DESCRI, XAR_MASIP, XAR_PARDHC, XAR_NORM "
                                + "FROM SC_XARXES " + "WHERE XAR_CODI=?");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("networks") && v.size() == 1) {
                stmt = conn
                        .prepareStatement("SELECT XAR_CODI, XAR_ADRIP, XAR_DESCRI, XAR_MASIP, XAR_PARDHC, XAR_NORM "
                                + "FROM SC_XARXES ");
            } else if (v.elementAt(0).equals("users") && v.size() == 1) {
                stmt = conn.prepareStatement("SELECT USU_CODI " + "FROM SC_USUARI "
                        + "WHERE USU_ACTIU='S' ");
            } else if (v.elementAt(0).equals("users") && v.size() == 2
                    && v.elementAt(1).equals("internal")) {
                stmt = conn.prepareStatement("SELECT USU_CODI " + "FROM SC_MAQUIN, SC_USUARI "
                        + "WHERE MAQ_NOM != 'nul' AND MAQ_ID=USU_IDMACO AND "
                        + "USU_ACTIU='S' AND USU_TIPUSU='I'");
            } else if (v.elementAt(0).equals("users") && v.size() == 2
                    && v.elementAt(1).equals("external")) {
                stmt = conn.prepareStatement("SELECT USU_CODI " + "FROM SC_USUARI "
                        + "WHERE USU_ACTIU='S' AND USU_TIPUSU='E'");
            } else if (v.elementAt(0).equals("group") && v.size() == 3
                    && v.elementAt(2).equals("users")) {
                stmt = conn.prepareStatement("SELECT USU_CODI " + "FROM SC_USUARI, SC_GRUPS "
                        + "WHERE USU_IDGRU = GRU_ID AND GRU_CODI=? " + "UNION "
                        + "SELECT USU_CODI " + "FROM  SC_USUARI, SC_USUGRU, SC_GRUPS  "
                        + "WHERE USU_ID = UGR_IDUSU AND UGR_IDGRU=GRU_ID AND GRU_CODI=? ");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("group") && v.size() == 2) {
                stmt = conn.prepareStatement("SELECT GRU_CODI, GRU_DESCRI, MAQ_NOM, GRU_UNIOFI "
                        + "FROM SC_GRUPS, SC_MAQUIN " + "WHERE MAQ_ID = GRU_IDMAQ AND GRU_CODI=? "
                        + "UNION " + "SELECT GRU_CODI, GRU_DESCRI, '', ''" + "FROM SC_GRUPS  "
                        + "WHERE GRU_IDMAQ IS NULL AND GRU_CODI=? ");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("printer") && v.size() == 2) {
                stmt = conn
                        .prepareStatement("SELECT IMP_MODEL, IMP_CODI, MAQ_NOM "
                                + "FROM SC_MAQUIN, SC_IMPRES "
                                + "WHERE MAQ_ID = IMP_IDMAQ AND IMP_ID = ? ");
                stmt.setString(1, (String) v.elementAt(1));
            } else if (v.elementAt(0).equals("config") && v.size() == 2) {
                stmt = conn.prepareStatement("SELECT 1 CON_ORDRE, CON_VALOR " + "FROM   SC_CONFIG "
                        + "WHERE  CON_IDXAR IS NULL AND CON_CODI=? " + "UNION "
                        + "SELECT 0 CON_ORDRE, CON_VALOR " + "FROM   SC_MAQUIN, SC_CONFIG "
                        + "WHERE  CON_IDXAR=MAQ_IDXAR AND CON_CODI=? AND MAQ_ADRIP=? "
                        + "ORDER  BY 1");
                stmt.setString(1, (String) v.elementAt(1));
                stmt.setString(2, (String) v.elementAt(1));
                Invoker invoker = Invoker.getInvoker();
                stmt.setString(3, invoker.getAddr().getHostAddress());
            } else {
                throw new InternalErrorException("Invalid path");
            }
            processStatement(stmt, writer, contentType);
        } finally {
        	if (stmt != null) { try {stmt.close(); } catch (Exception e ) {} };
            conn.close();
        }
    }

    public final void processStatement(final PreparedStatement stmt, final Writer writer,
            String format) throws SQLException, InternalErrorException, IOException {
        boolean xml = "text/xml".equals(format);
        @SuppressWarnings("unchecked")

        
        ResultSet rset = stmt.executeQuery();
        java.sql.ResultSetMetaData md = rset.getMetaData();
        StringBuffer s = new StringBuffer();
        if (!xml) {
            s .append( Integer.toString(md.getColumnCount()) );
            int i;
            for (i = 1; i <= md.getColumnCount(); i++) {
                s .append( "|" );
                s .append(md.getColumnName(i));
            }
        }
        if (writer != null) {
            if (xml)
                writer.write("<data>");
            else
                writer.write("OK|");
        }
        writer.write(s.toString());
        writer.flush();

        while (rset.next()) {
            s = new StringBuffer();
            if (xml) {
                s .append("<row>");
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    s.append( "<");
                    s.append (md.getColumnName(i));
                    s.append (">");
                    s.append(rset.getString(i));
                    s.append("</");
                    s.append(md.getColumnName(i));
                    s.append(">");
                }
                s.append( "</row>" );

            } else {
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    s.append( "|");
                    s.append( rset.getString(i) );
                }
           } while (rset.next());
            writer.write(s.toString());
            if (xml)
                writer.write("</data>");
            writer.flush();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected List handleQueryHql(String path) throws Exception {

        SessionFactory sf = (SessionFactory) ServerServiceLocator.instance().getService(
                "sessionFactory");
        Session sessio = sf.getCurrentSession();

        Query query = sessio.createQuery(path);

        LinkedList result = new LinkedList();
        for (Object row : query.list()) {
            LinkedList data = new LinkedList();
            if (row.getClass().isArray()) {
                for (int i = 0; i < java.lang.reflect.Array.getLength(row); i++) {
                    data.add(Array.get(row, i));
                }
            } else {
                data.add(row);
            }
            result.add(data);
        }

        return result;

    }

}

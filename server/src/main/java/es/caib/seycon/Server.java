/*
 * Server.java
 *
 * Created on May 8, 2000, 10:44 AM
 */

package es.caib.seycon;

import java.rmi.RemoteException;
import java.security.cert.X509Certificate;

import es.caib.seycon.net.NetworkAuthorization;

/**
 * Interfaz remoto (RMI) de acceso al servidor SEYCON
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.21 $
 * 
 */

// $Log: Server.java,v $
// Revision 1.21  2012-11-09 09:38:55  u07286
// Implantació RMI
//
// Revision 1.1.2.2  2012-05-30 08:43:20  u07286
// *** empty log message ***
//
// Revision 1.1.2.1  2012-05-16 10:33:38  u07286
// Reestructuració de paquets seycon antics
//
// Revision 1.19  2012-05-11 09:23:34  u07286
// Millores a per a SAML federation
//
// Revision 1.18  2012-05-11 09:15:18  u07286
// Millores a per a SAML federation
//
// Revision 1.17  2012-05-07 08:12:01  u88683
// Nou m�tode per obtindre llistes de distribuci� (per a google)
//
// Revision 1.16  2012-02-13 13:57:49  u07286
// Nou mètode d'instanciació
//
// Revision 1.15  2012-01-17 12:14:07  u88683
// Modificacions en els ACLs de m�quines: ara es mira per autoritzacions (a nivell de suport principalment). Per aix� es crea el m�tode: hasSupportAccessHost que aglutina diferents m�todes anteriors
//
// Revision 1.15 2012-01-17 10:35 u88683
// Eliminem getNivellAcces (només s'emprava per veure si un usuari té accés de suport)
// ho reemplacem per hasSupportAccessHost
//
// Revision 1.14  2011-11-29 07:23:30  u88683
// correct typo
//
// Revision 1.13  2011-05-18 09:22:56  u88683
// Afegim excepci� UnknownRoleException als m�todes GetRoleUsers i GetRoleUsersActiusInfo quan no existeix el rol.
// Nou m�tode getNivellAcces per obtindre les autoritzacions d'acc�s a una m�quina per a un usuari determinat
//
// Revision 1.12  2011-05-03 12:18:40  u88683
// Afegim m�todes per obtindre i per establir el secret d'un usuari
//
// Revision 1.11  2011-01-12 12:27:59  u88683
// Afegim nous m�todes per optimitzar la gesti� de rols des dels agents (her�ncia de rols)
//
// Revision 1.10  2010-08-11 12:51:27  u88683
// Gesti�n del ControlAcc�s
//
// Revision 1.9 2010-05-13 09:45:15 u88683
// A�adimos m�todo GetUserGroupsHierarchy para obtener los grupos de un usuario
// y la jerarqu�a de padres del mismo
//
// Revision 1.8 2010-03-15 10:23:31 u07286
// Movido a tag HEAD
//
// Revision 1.5.2.4 2009-09-28 10:47:48 u07286
// Depurada gestión de roles heredados
//
// Revision 1.5.2.3 2009-06-24 12:55:07 u91940
// -canvis relacionats amb la validació de password i PIN per a un usuari anònim
// -canvis relacionats amb el merge de les versions 3.0.15 i 3.1.0
//
// Revision 1.5.2.2 2009-06-16 11:23:01 u07286
// Merge a seycon-3.0.15
//
// Revision 1.5.2.1 2009-03-23 07:52:00 u89559
// *** empty log message ***
//
// Revision 1.6 2009-02-10 08:34:53 u89559
// *** empty log message ***
//
// Revision 1.5 2008-10-31 07:15:56 u89559
// *** empty log message ***
//
// Revision 1.4 2008-10-27 10:43:51 u89559
// *** empty log message ***
//
// Revision 1.3 2008-10-16 11:43:42 u07286
// Migrado de RMI a HTTP
//
// Revision 1.2 2008-08-12 09:58:10 u89559
// *** empty log message ***
//
// Revision 1.1 2007-09-06 12:51:10 u89559
// [T252]
//
// Revision 1.3 2004-03-15 12:08:05 u07286
// Conversion UTF-8
//
// Revision 1.2 2004/03/15 11:57:49 u07286
// Agregada documentacion JavaDoc
//
public interface Server extends java.rmi.Remote {

}


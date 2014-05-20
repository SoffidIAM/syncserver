// Copyright (c) 2000 Govern  de les Illes Balears
package es.caib.sso;

import es.caib.seycon.Password;

/**
 * Interfaz RMI de gesti�n de logons. Se localizar� a través del lookup del
 * registro RMI
 * <P>
 * 
 * @author $Author: u07286 $
 * @version $Revision: 1.7 $
 * @see LogonImpl
 */
public interface LogonMgr extends java.rmi.Remote {
        /**
         * Solicitud de propagaci�n de una contrase�a supuestamente buena. El
         * sistema la validar� contra las bases de datos fiables, y si es correcta
         * se aplicar� al resto de subsitemas
         * 
         * @param user
         *            c�digo del usuario
         * @param password
         *            contrase�a del usuario
         * @throws java.rmi.RemoteException
         *             error de comunicaciones
         * @throws es.caib.seycon.UnknownUserException
         *             usuario desconocido
         * @throws es.caib.seycon.InternalErrorException
         *             error interno
         */
        void propagatePassword(String user, es.caib.seycon.Password password)
                        throws java.rmi.RemoteException,
                        es.caib.seycon.UnknownUserException,
                        es.caib.seycon.InternalErrorException;

        /**
         * Validar una contrase�a. Em primera instance el sistema validar� la
         * contrase�a con sus tablas internas. Si no parece correcta, el sistema la
         * validar� contra las bases de datos fiables, y se determinar� as� si
         * correcta .
         * 
         * @param user
         *            c�digo del usuario
         * @param password
         *            contrase�a del usuario
         * @return true si la contrase�a es correcta
         * @throws java.rmi.RemoteException
         *             error de comunicaciones
         * @throws es.caib.seycon.UnknownUserException
         *             usuario desconocido
         * @throws es.caib.seycon.InternalErrorException
         *             error interno
         */
        boolean validatePassword(String user, es.caib.seycon.Password password)
                        throws java.rmi.RemoteException,
                        es.caib.seycon.UnknownUserException,
                        es.caib.seycon.InternalErrorException;

        /**
         * Validar el PIN de un usuario anonimo.
         * 
         * @param user
         *            c�digo del usuario
         * @param password
         *            contrase�a del usuario
         * @return true si la contrase�a es correcta
         * @throws java.rmi.RemoteException
         *             error de comunicaciones
         * @throws es.caib.seycon.UnknownUserException
         *             usuario desconocido
         * @throws es.caib.seycon.InternalErrorException
         *             error interno
         */
        boolean validatePIN(String user, es.caib.seycon.Password password)
                        throws java.rmi.RemoteException,
                        es.caib.seycon.UnknownUserException,
                        es.caib.seycon.InternalErrorException;
        
        /**
         * Cambiar la contrase�a. El sistema verificar� si la contrase�a antigua es
         * correta y si l anueva es v�lida. Si ambas verificaciones son
         * satisfactorias, se producir� el cambio de contrase�a en todos los
         * subsistemas
         * 
         * @param user
         *            c�digo del usuario
         * @param oldPass
         *            contrase�a actual
         * @param newPass
         *            contrase�a que se desea asignar
         * @throws java.rmi.RemoteException
         *             error de comunicaciones
         * @throws es.caib.seycon.UnknownUserException
         *             usuario desconocido
         * @throws es.caib.seycon.InternalErrorException
         *             error interno
         * @throws es.caib.seycon.BadPasswordException
         *             la contrase�a nueva no cumple los requisitos de seguridad
         * @throws es.caib.seycon.InvalidPasswordException
         *             la contrase�a antigua no es correcta
         */
        void changePassword(String user, es.caib.seycon.Password oldPass,
                        es.caib.seycon.Password newPass) throws java.rmi.RemoteException,
                        es.caib.seycon.UnknownUserException,
                        es.caib.seycon.InternalErrorException,
                        es.caib.seycon.BadPasswordException,
                        es.caib.seycon.InvalidPasswordException;

        /**
         * Consultar si la contrase�a del usuario ha caducado
         * 
         * @param user
         *            c�digo del usuario
         * @return true si la contrase�a ha caducado
         * @throws java.rmi.RemoteException
         *             error de comunicaciones
         * @throws es.caib.seycon.UnknownUserException
         *             usuario desconocido
         * @throws es.caib.seycon.InternalErrorException
         *             error interno
         */
        boolean mustChangePassword(String user) throws java.rmi.RemoteException,
                        es.caib.seycon.UnknownUserException,
                        es.caib.seycon.InternalErrorException;
}

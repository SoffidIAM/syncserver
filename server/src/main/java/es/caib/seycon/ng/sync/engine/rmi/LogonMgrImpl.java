package es.caib.seycon.ng.sync.engine.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import es.caib.seycon.BadPasswordException;
import es.caib.seycon.InternalErrorException;
import es.caib.seycon.InvalidPasswordException;
import es.caib.seycon.Password;
import es.caib.seycon.UnknownUserException;
import es.caib.seycon.ng.comu.PasswordValidation;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.sso.LogonMgr;

public class LogonMgrImpl extends UnicastRemoteObject implements LogonMgr {

    protected LogonMgrImpl() throws RemoteException {
        super();
    }

    private static final long serialVersionUID = 1L;

    public void propagatePassword(String user, Password password) throws RemoteException,
            UnknownUserException, InternalErrorException {
        try {
            ServerServiceLocator.instance().getLogonService()
                    .propagatePassword(user, null, password.getPassword());
        } catch (es.caib.seycon.ng.exception.InternalErrorException e) {
            throw new InternalErrorException(e.getMessage());
        }
    }

    public boolean validatePassword(String user, Password password) throws RemoteException,
            UnknownUserException, InternalErrorException {
        try {
            return ServerServiceLocator.instance().getLogonService()
                    .validatePassword(user, null, password.getPassword()) == PasswordValidation.PASSWORD_GOOD;
        } catch (es.caib.seycon.ng.exception.InternalErrorException e) {
            throw new InternalErrorException(e.getMessage());
        }
    }

    public boolean validatePIN(String user, Password password) throws RemoteException,
            UnknownUserException, InternalErrorException {
        try {
            return ServerServiceLocator.instance().getLogonService()
                    .validatePIN(user, password.getPassword());
        } catch (es.caib.seycon.ng.exception.InternalErrorException e) {
            throw new InternalErrorException(e.getMessage());
        }
    }

    public void changePassword(String user, Password oldPass, Password newPass)
            throws RemoteException, UnknownUserException, InternalErrorException,
            BadPasswordException, InvalidPasswordException {
        try {
            ServerServiceLocator.instance().getLogonService()
                    .changePassword(user, null, oldPass.getPassword(), newPass.getPassword());
        } catch (es.caib.seycon.ng.exception.InternalErrorException e) {
            throw new InternalErrorException(e.getMessage());
        } catch (es.caib.seycon.ng.exception.BadPasswordException e) {
            throw new BadPasswordException(e.getMessage());
        } catch (es.caib.seycon.ng.exception.InvalidPasswordException e) {
            throw new InvalidPasswordException(e.getMessage());
        }
    }

    public boolean mustChangePassword(String user) throws RemoteException, UnknownUserException,
            InternalErrorException {
        try {
            return ServerServiceLocator.instance().getLogonService()
                    .mustChangePassword(user, null);
        } catch (es.caib.seycon.ng.exception.InternalErrorException e) {
            throw new InternalErrorException(e.getMessage());
        }
    }

}

package es.caib.seycon;

public class InternalErrorException extends es.caib.seycon.ng.exception.InternalErrorException {

    @Deprecated
    public InternalErrorException() {
    }

    @Deprecated
    public InternalErrorException(String msg) {
        super(msg);
    }

    @Deprecated
    public InternalErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    @Deprecated
    public InternalErrorException(String message, Throwable cause, String filtre) {
        super(message, cause, filtre);
    }

}

package com.soffid.iam.sync.web;

import es.caib.seycon.ng.exception.InternalErrorException;

public class NameMismatchException extends InternalErrorException {

    public NameMismatchException() {
        super();
    }

    public NameMismatchException(String msg) {
        super(msg);
    }

}

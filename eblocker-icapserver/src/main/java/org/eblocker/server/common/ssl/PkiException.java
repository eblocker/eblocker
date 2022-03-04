package org.eblocker.server.common.ssl;

public class PkiException extends Exception {
    public PkiException(String message) {
        super(message);
    }

    PkiException(String message, Throwable cause) {
        super(message, cause);
    }
}

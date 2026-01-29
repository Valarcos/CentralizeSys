package com.centralizesys.exception;

public class LegacyImportException extends RuntimeException {
    public LegacyImportException(String message) {
        super(message);
    }

    public LegacyImportException(String message, Throwable cause) {
        super(message, cause);
    }
}

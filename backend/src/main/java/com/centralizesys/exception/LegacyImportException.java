package com.centralizesys.exception;

public class LegacyImportException extends RuntimeException {
    // TODO: the second method might have rendered the first one useless. Consider removing it if no use for it is found or even theorized.
    public LegacyImportException(String message) {
        super(message);
    }

    public LegacyImportException(String message, Throwable cause) {
        super(message, cause);
    }
}

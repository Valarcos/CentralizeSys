package com.centralizesys.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Returns 500
public class InfrastructureException extends RuntimeException {
    public InfrastructureException(String message) {
        super(message);
    }
}
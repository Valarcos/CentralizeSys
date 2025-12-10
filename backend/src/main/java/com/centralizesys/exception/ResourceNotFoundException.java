package com.centralizesys.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " with ID " + id + " not found");
    }
}

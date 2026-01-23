package com.centralizesys.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Handle BusinessRuleException: Returns 400 Bad Request")
    void handleBusinessRule() {
        BusinessRuleException ex = new BusinessRuleException("Regla de negocio violada");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleBusinessRule(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Regla de negocio violada", response.getBody().message());
        assertEquals(400, response.getBody().status());
    }

    @Test
    @DisplayName("Handle ResourceNotFoundException: Returns 404 Not Found")
    void handleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("No encontrado", 123L);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("No encontrado with ID 123 not found", response.getBody().message());
        assertEquals(404, response.getBody().status());
    }

    @Test
    @DisplayName("Handle DataIntegrityViolation: Returns 400 and extracts detail")
    void handleDataIntegrityViolation() {
        // Compose a wrapped exception (Spring style)
        // Root cause usually contains the SQL error detail
        Exception rootCause = new Exception("UNIQUE constraint failed: usuarios.email");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Spring Wrapper", rootCause);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        // Verify it extracted the root cause message
        assertTrue(response.getBody().message().contains("UNIQUE constraint failed"));
        assertEquals(400, response.getBody().status());
    }

    @Test
    @DisplayName("Handle Generic Exception: Returns 500 Internal Server Error")
    void handleGenericException() {
        Exception ex = new RuntimeException("Unexpected Crash");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Unexpected Crash"));
        assertEquals(500, response.getBody().status());
    }
}

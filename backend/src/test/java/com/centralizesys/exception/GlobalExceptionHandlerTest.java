package com.centralizesys.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GlobalExceptionHandler.
 * Verifies both:
 * 1. User-facing responses (Spanish, security-safe messages)
 * 2. Technical details are logged for debugging (via Logback ListAppender)
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // Logback test infrastructure
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setupLogCapture() {
        // Get the logger used by GlobalExceptionHandler
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // Create and attach a list appender to capture log events
        listAppender = new ListAppender<>();
        listAppender.start();
        handlerLogger.addAppender(listAppender);
    }

    @AfterEach
    void teardownLogCapture() {
        handlerLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @DisplayName("Handle BusinessRuleException: Returns 400 Bad Request")
    void handleBusinessRule() {
        BusinessRuleException ex = new BusinessRuleException("Regla de negocio violada");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleBusinessRule(ex);

        // Verify user-facing response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Regla de negocio violada", response.getBody().message());
        assertEquals(400, response.getBody().status());

        // Verify technical detail was logged
        List<ILoggingEvent> logs = listAppender.list;
        assertEquals(1, logs.size());
        assertEquals(Level.WARN, logs.getFirst().getLevel());
        assertTrue(logs.getFirst().getFormattedMessage().contains("Regla de negocio violada"));
    }

    @Test
    @DisplayName("Handle ResourceNotFoundException: Returns 404 Not Found")
    void handleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("No encontrado", 123L);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleResourceNotFound(ex);

        // Verify user-facing response
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("No encontrado with ID 123 not found", response.getBody().message());
        assertEquals(404, response.getBody().status());

        // Verify technical detail was logged
        List<ILoggingEvent> logs = listAppender.list;
        assertEquals(1, logs.size());
        assertEquals(Level.WARN, logs.getFirst().getLevel());
        assertTrue(logs.getFirst().getFormattedMessage().contains("No encontrado"));
    }

    @Test
    @DisplayName("Handle DataIntegrityViolation: Returns 400 with user-friendly message, logs technical detail")
    void handleDataIntegrityViolation() {
        // Compose a wrapped exception (Spring style)
        Exception rootCause = new Exception("UNIQUE constraint failed: usuarios.email");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Spring Wrapper", rootCause);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        // Verify user-facing response (Spanish, no technical leak)
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Error: Ya existe un registro con los mismos datos únicos.", response.getBody().message());
        assertEquals(400, response.getBody().status());

        // Verify technical detail was logged (for debugging)
        List<ILoggingEvent> logs = listAppender.list;
        assertEquals(1, logs.size());
        assertEquals(Level.ERROR, logs.getFirst().getLevel());
        assertTrue(logs.getFirst().getFormattedMessage().contains("UNIQUE constraint failed"),
                "Technical detail should be logged for debugging");
    }

    @Test
    @DisplayName("Handle Generic Exception: Returns 500 with safe message, logs technical detail")
    void handleGenericException() {
        Exception ex = new RuntimeException("Unexpected Crash");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        // Verify user-facing response (generic, no technical leak)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Ocurrió un error inesperado. Contacte al administrador.", response.getBody().message());
        assertEquals(500, response.getBody().status());

        // Verify technical detail was logged (for debugging)
        List<ILoggingEvent> logs = listAppender.list;
        assertEquals(1, logs.size());
        assertEquals(Level.ERROR, logs.getFirst().getLevel());
        assertTrue(logs.getFirst().getFormattedMessage().contains("Unexpected Crash"),
                "Technical exception message should be logged for debugging");
    }
}

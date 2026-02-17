package com.centralizesys.exception;

import com.centralizesys.security.SecurityUtils;
import com.centralizesys.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final AuditoriaService auditoriaService;

    private static final String RUTA = "Ruta: ";

    public GlobalExceptionHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // DOMAIN EXCEPTIONS (Business Logic)
    // ══════════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(), // Already user-friendly (e.g., "Producto no encontrado")
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        log.warn("Business rule violation: {}", ex.getMessage());

        // Audit Failure
        try {
            Long userId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
            String path = req.getRequestURI();
            auditoriaService.registrarAccion(userId, "FALLO_REGLA_NEGOCIO",
                    RUTA + path + " | Error: " + ex.getMessage());
        } catch (Exception e) {
            log.error("Failed to audit error", e);
        }

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(), // Already user-friendly
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // DATABASE EXCEPTIONS (Translated via sql-error-codes.xml)
    // ══════════════════════════════════════════════════════════════════════════════

    // Handles DB constraints: UNIQUE, FK, CHECK, NOT NULL (codes: 19, 275, 531,
    // 787, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                      HttpServletRequest req) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.error("DATA INTEGRITY VIOLATION: {}", detail, ex);

        // User-friendly message - technical detail goes to console
        String userMessage = "Error de datos: Los datos ingresados no son válidos o ya existen.";
        if (detail != null && detail.contains("UNIQUE")) {
            userMessage = "Error: Ya existe un registro con los mismos datos únicos.";
        } else if (detail != null && detail.contains("FOREIGN KEY")) {
            userMessage = "Error: No se puede realizar la operación porque hay registros relacionados.";
        } else if (detail != null && detail.contains("NOT NULL")) {
            userMessage = "Error: Faltan datos obligatorios.";
        }

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                userMessage,
                System.currentTimeMillis());

        // Audit Failure
        try {
            Long userId = SecurityUtils.getAuthenticatedUserId();
            String path = req.getRequestURI();
            auditoriaService.registrarAccion(userId, "FALLO_INTEGRIDAD_DATOS",
                    RUTA + path + " | Detalle: " + detail);
        } catch (Exception e) {
            log.error("Failed to audit error", e);
        }

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Handles SQL syntax errors (code: 1) - This is a developer bug, not user error
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<ErrorResponse> handleBadSqlGrammar(BadSqlGrammarException ex) {
        log.error("SQL SYNTAX ERROR - SQL: {}, Message: {}", ex.getSql(), ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error interno del servidor. Contacte al administrador.",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Handles database locking (codes: 5, 6) - Retry or wait
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseLock(CannotAcquireLockException ex) {
        log.warn("DATABASE LOCK: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "La base de datos está ocupada. Intente nuevamente en unos segundos.",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    // Handles I/O errors, disk full, corrupt database (codes: 10, 11, 13, 14, 26)
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> handleResourceFailure(DataAccessResourceFailureException ex) {
        log.error("DATABASE RESOURCE FAILURE: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Error de acceso a la base de datos. Verifique el estado del servidor.",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // Handles permission denied (codes: 3, 23)
    @ExceptionHandler(PermissionDeniedDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDbPermissionDenied(PermissionDeniedDataAccessException ex) {
        log.error("DATABASE PERMISSION DENIED: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Error de permisos en la base de datos. Contacte al administrador.",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SECURITY / HTTP EXCEPTIONS
    // ══════════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Response status exception: {} - {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getReason(),
                System.currentTimeMillis());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Acceso denegado: No tiene permiso para realizar esta acción.",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CATCH-ALL (Unexpected errors)
    // ══════════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("UNEXPECTED ERROR: {}", ex.getMessage(), ex);

        // Audit Failure
        try {
            Long userId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
            String path = req.getRequestURI();
            auditoriaService.registrarAccion(userId, "ERROR_SISTEMA",
                    RUTA + path + " | Excepción no controlada: " + ex.getMessage());
        } catch (Exception e) {
            // Swallow logging error to ensure response goes out
        }

        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocurrió un error inesperado. Contacte al administrador.",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // RESPONSE RECORD
    // ══════════════════════════════════════════════════════════════════════════════

    public record ErrorResponse(int status, String message, long timestamp) {
    }
}
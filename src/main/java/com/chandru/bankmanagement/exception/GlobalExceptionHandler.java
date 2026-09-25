package com.chandru.bankmanagement.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error response has the same shape:
 *   { timestamp, status, message, errors? }
 * "message" is always present and safe to show to the user.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 404 ────────────────────────────────────────────────────────────

    @ExceptionHandler({CustomerNotFoundException.class,
                       AccountNotFoundException.class,
                       ResourceNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ── 409 ────────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(
            DuplicateEmailException ex) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return errorResponse(HttpStatus.CONFLICT,
                "The request conflicts with existing data (duplicate or linked record)");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class,
                       PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleLocking(RuntimeException ex) {
        log.warn("Concurrent update conflict: {}", ex.getMessage());
        return errorResponse(HttpStatus.CONFLICT,
                "The account is busy with another transaction. Please try again.");
    }

    // ── 502 — Keycloak / email service problems ───────────────────────

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, Object>> handleExternal(ExternalServiceException ex) {
        log.warn("External service error: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    // ── 400 ────────────────────────────────────────────────────────────

    @ExceptionHandler({BusinessRuleException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBusinessRule(RuntimeException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            fieldErrors.putIfAbsent(field, err.getDefaultMessage());
        });

        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST);
        body.put("message", fieldErrors.isEmpty()
                ? "Invalid request"
                : String.join(" · ", fieldErrors.values()));
        body.put("errors", fieldErrors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            HttpMessageNotReadableException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Malformed request — please check the values entered");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Missing required parameter '" + ex.getParameterName() + "'");
    }

    // ── 403 — Spring Security AccessDeniedException (@PreAuthorize) ────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex) {
        return errorResponse(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    // ── 403 — application-level UnauthorizedAccessException ────────────

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            UnauthorizedAccessException ex) {
        return errorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ── 500 — never leak internals to the client ───────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(RuntimeException ex) {
        log.error("Unhandled error", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again later.");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status, String message) {
        Map<String, Object> body = baseBody(status);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }

    private Map<String, Object> baseBody(HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        return body;
    }
}

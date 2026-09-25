package com.chandru.bankmanagement.exception;

/**
 * An external system the request depends on (Keycloak, SMTP via Keycloak)
 * failed or is unreachable. Mapped to HTTP 502.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}

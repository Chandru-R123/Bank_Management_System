package com.chandru.bankmanagement.exception;

/**
 * Thrown when a request is well-formed but violates a banking rule
 * (insufficient funds, frozen account, limit exceeded, ...).
 * Mapped to HTTP 400 with the message shown to the user.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}

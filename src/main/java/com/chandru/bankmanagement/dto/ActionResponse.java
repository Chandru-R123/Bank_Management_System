package com.chandru.bankmanagement.dto;

/**
 * Result of an action that may also send an email.
 *
 * @param data       the affected resource (staff member, customer, …)
 * @param emailSent  whether a "set / reset password" email was sent
 * @param message    human-readable outcome shown to the admin
 */
public record ActionResponse<T>(T data, boolean emailSent, String message) {
}

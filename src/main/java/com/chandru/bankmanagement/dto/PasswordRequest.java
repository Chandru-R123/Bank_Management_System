package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A temporary password set by an ADMIN — the user must change it at next login. */
public record PasswordRequest(
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be 8–64 characters")
        String password
) {
}

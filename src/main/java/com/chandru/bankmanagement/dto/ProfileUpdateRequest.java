package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contact details a customer may change themselves.
 * Name and email are KYC fields and can only be changed by bank staff.
 */
public record ProfileUpdateRequest(

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9][0-9 -]{8,14}$",
                 message = "Phone must be a valid number (10–15 digits)")
        String phone,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String address
) {
}

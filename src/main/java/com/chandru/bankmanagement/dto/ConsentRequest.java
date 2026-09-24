package com.chandru.bankmanagement.dto;

import com.chandru.bankmanagement.entity.ConsentPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Created by a Third-Party Provider to ask a customer for data access.
 *
 * @param customerEmail  identifies the customer (their online-banking email)
 * @param validityDays   how long the consent lasts once created (1–365, default 90)
 * @param tppName        optional display name; defaults to the TPP's username
 */
public record ConsentRequest(

        @NotBlank(message = "Customer email is required")
        @Email(message = "Invalid customer email")
        String customerEmail,

        @NotEmpty(message = "Select at least one permission")
        Set<ConsentPermission> permissions,

        @NotBlank(message = "Purpose is required")
        @Size(max = 140, message = "Purpose must be at most 140 characters")
        String purpose,

        @Min(value = 1, message = "Validity must be at least 1 day")
        @Max(value = 365, message = "Validity can be at most 365 days")
        Integer validityDays,

        @Size(max = 80, message = "TPP name must be at most 80 characters")
        String tppName
) {
}

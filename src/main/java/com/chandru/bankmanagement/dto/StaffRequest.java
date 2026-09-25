package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * New staff login created by an ADMIN.
 *
 * @param roles     any of ADMIN, EMPLOYEE, MAKER, CHECKER
 * @param password  optional temporary password (must be changed at first login);
 *                  when omitted the user is emailed a link to set their own
 */
public record StaffRequest(

        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                 message = "Username: 3–50 letters, digits, '.', '_' or '-'")
        String username,

        @NotBlank(message = "First name is required")
        @Size(max = 60, message = "First name must be at most 60 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 60, message = "Last name must be at most 60 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email")
        String email,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9][0-9 -]{8,14}$",
                 message = "Phone must be a valid number (10–15 digits)")
        String phone,

        @Size(max = 255, message = "Branch / address must be at most 255 characters")
        String address,

        @NotEmpty(message = "Choose at least one role")
        Set<String> roles,

        @Size(min = 8, max = 64, message = "Temporary password must be 8–64 characters")
        String password
) {
}

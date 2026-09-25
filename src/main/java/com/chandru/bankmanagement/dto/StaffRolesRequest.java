package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record StaffRolesRequest(
        @NotEmpty(message = "Choose at least one role")
        Set<String> roles
) {
}

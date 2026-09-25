package com.chandru.bankmanagement.dto;

import java.util.List;

public record StaffResponse(
        String id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean enabled,
        List<String> roles,
        Long createdTimestamp
) {
}

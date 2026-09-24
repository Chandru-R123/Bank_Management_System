package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** The accounts the customer agrees to share with the TPP. */
public record ConsentApprovalRequest(
        @NotEmpty(message = "Select at least one account to share")
        List<Long> accountIds
) {
}

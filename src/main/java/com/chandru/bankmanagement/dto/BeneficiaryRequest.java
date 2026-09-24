package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param customerId  only used when staff add a beneficiary on a customer's
 *                    behalf; customers always add to their own list
 */
public record BeneficiaryRequest(

        @NotBlank(message = "Nickname is required")
        @Size(max = 60, message = "Nickname must be at most 60 characters")
        String nickname,

        @NotBlank(message = "Account number is required")
        @Size(max = 30, message = "Account number must be at most 30 characters")
        String accountNumber,

        Long customerId
) {
}

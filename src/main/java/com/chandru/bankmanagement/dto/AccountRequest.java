package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class AccountRequest {

    /** Optional on create — generated automatically when blank. Immutable on update. */
    @Size(max = 30, message = "Account number must be at most 30 characters")
    @Pattern(regexp = "^$|^[A-Za-z0-9-]+$",
             message = "Account number may only contain letters, digits and '-'")
    private String accountNumber;

    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "SAVINGS|CURRENT|FIXED_DEPOSIT",
             message = "Account type must be SAVINGS, CURRENT or FIXED_DEPOSIT")
    private String accountType;

    /** Opening balance on create. Ignored on update — balances only change via transactions. */
    @PositiveOrZero(message = "Balance cannot be negative")
    @Digits(integer = 13, fraction = 2, message = "Balance can have at most 2 decimal places")
    private BigDecimal balance;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    public AccountRequest() {
    }

    public AccountRequest(String accountNumber,
                          String accountType,
                          BigDecimal balance,
                          Long customerId) {
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.customerId = customerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
}

package com.chandru.bankmanagement.dto;

import java.time.LocalDateTime;

public class TransactionResponse {

    private Long transactionId;
    private String transactionType;
    private Double amount;
    private LocalDateTime transactionDate;
    private String accountNumber;

    public TransactionResponse() {
    }

    public TransactionResponse(Long transactionId,
                               String transactionType,
                               Double amount,
                               LocalDateTime transactionDate,
                               String accountNumber) {

        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.accountNumber = accountNumber;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}
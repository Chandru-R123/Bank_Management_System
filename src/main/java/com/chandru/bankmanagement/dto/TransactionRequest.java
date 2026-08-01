package com.chandru.bankmanagement.dto;

public class TransactionRequest {

    private Double amount;

    public TransactionRequest() {
    }

    public TransactionRequest(Double amount) {
        this.amount = amount;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }
}
package com.chandru.bankmanagement.entity;

import java.util.List;

/** Transaction type codes stored in transactions.transaction_type. */
public final class TransactionTypes {

    public static final String DEPOSIT         = "DEPOSIT";
    public static final String WITHDRAW        = "WITHDRAW";
    public static final String TRANSFER_IN     = "TRANSFER_IN";
    public static final String TRANSFER_OUT    = "TRANSFER_OUT";
    public static final String OPENING_DEPOSIT = "OPENING_DEPOSIT";
    public static final String CLOSURE_PAYOUT  = "CLOSURE_PAYOUT";

    /** Debits that count toward a customer's daily limit. */
    public static final List<String> CUSTOMER_DEBITS = List.of(WITHDRAW, TRANSFER_OUT);

    private TransactionTypes() {
    }
}

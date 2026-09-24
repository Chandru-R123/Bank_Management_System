package com.chandru.bankmanagement.entity;

/** What a third-party provider may read once the customer authorises the consent. */
public enum ConsentPermission {
    /** Account number, type and holder name. */
    READ_ACCOUNTS,
    /** Current balance of each consented account. */
    READ_BALANCES,
    /** Transaction history of each consented account. */
    READ_TRANSACTIONS
}

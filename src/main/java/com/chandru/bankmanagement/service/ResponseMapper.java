package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.entity.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Entity → DTO conversion shared by the services. */
final class ResponseMapper {

    private static final Locale INDIA = Locale.of("en", "IN");

    private ResponseMapper() {
    }

    static AccountResponse toResponse(Account a) {
        Customer c = a.getCustomer();
        return new AccountResponse(
                a.getAccountId(),
                a.getAccountNumber(),
                a.getAccountType(),
                money(a.getBalance()),
                c != null ? c.getCustomerId() : null,
                c != null ? c.getName() : null,
                a.getStatus().name(),
                a.getCreatedAt()
        );
    }

    static CustomerResponse toResponse(Customer c) {
        CustomerResponse r = new CustomerResponse(
                c.getCustomerId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress()
        );
        String sub = c.getKeycloakSub();
        r.setOnlineBanking(sub != null && !sub.isBlank() && !sub.endsWith("-placeholder"));
        return r;
    }

    static TransactionResponse toResponse(Transaction tx) {
        TransactionResponse r = new TransactionResponse();
        r.setTransactionId(tx.getTransactionId());
        r.setTransactionType(tx.getTransactionType());
        r.setAmount(money(tx.getAmount()));
        r.setTransactionDate(tx.getTransactionDate());
        if (tx.getAccount() != null) {
            r.setAccountId(tx.getAccount().getAccountId());
            r.setAccountNumber(tx.getAccount().getAccountNumber());
        }
        r.setBalanceAfter(tx.getBalanceAfter() != null ? money(tx.getBalanceAfter()) : null);
        r.setDescription(tx.getDescription());
        r.setCounterpartyAccountNumber(tx.getCounterpartyAccountNumber());
        r.setReferenceId(tx.getReferenceId());
        r.setPerformedBy(tx.getPerformedBy());
        return r;
    }

    /** Normalise to 2 decimal places (values read from legacy float columns may carry noise). */
    static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_EVEN);
    }

    /** ₹1,00,000.00 — for user-facing error messages. */
    static String rupees(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(INDIA).format(money(v));
    }
}

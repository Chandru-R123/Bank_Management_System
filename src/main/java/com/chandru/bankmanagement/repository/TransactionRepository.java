package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountAccountId(Long accountId);

    /** Newest first — the natural order for statements and activity feeds. */
    List<Transaction> findByAccountAccountIdOrderByTransactionDateDescTransactionIdDesc(Long accountId);

    List<Transaction> findAllByOrderByTransactionDateDescTransactionIdDesc();

    /** Every transaction on every account owned by a customer (by Keycloak sub). */
    List<Transaction> findByAccountCustomerKeycloakSubOrderByTransactionDateDescTransactionIdDesc(String keycloakSub);

    /** Sum of the given transaction types on an account since a point in time (daily limits); null when none. */
    @Query("""
            select sum(t.amount) from Transaction t
            where t.account.accountId = :accountId
              and t.transactionType in :types
              and t.transactionDate >= :since
            """)
    BigDecimal sumAmountSince(@Param("accountId") Long accountId,
                              @Param("types") Collection<String> types,
                              @Param("since") LocalDateTime since);
}

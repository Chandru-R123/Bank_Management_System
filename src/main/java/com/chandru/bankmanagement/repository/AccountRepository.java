package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumberIgnoreCase(String accountNumber);

    /**
     * Loads the account with a row-level write lock (SELECT ... FOR UPDATE).
     * Every balance change goes through this so two concurrent withdrawals
     * cannot both read the same balance and overdraw the account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    List<Account> findAllByOrderByAccountIdAsc();

    /** All accounts owned by a customer (by DB id). */
    List<Account> findByCustomerCustomerId(Long customerId);

    boolean existsByCustomerCustomerId(Long customerId);

    /** Ownership check — account belongs to a specific customer by DB id. */
    Optional<Account> findByAccountIdAndCustomerCustomerId(
            Long accountId, Long customerId);

    /** Ownership check — account belongs to a customer by Keycloak sub. */
    Optional<Account> findByAccountIdAndCustomerKeycloakSub(
            Long accountId, String keycloakSub);

    /** All accounts for a customer identified by Keycloak sub. */
    List<Account> findByCustomerKeycloakSubOrderByAccountIdAsc(String keycloakSub);
}

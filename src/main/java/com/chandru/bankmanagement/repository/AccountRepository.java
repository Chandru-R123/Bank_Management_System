package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumber(String accountNumber);

    /** All accounts owned by a customer (by DB id). */
    List<Account> findByCustomerCustomerId(Long customerId);

    /** Ownership check — account belongs to a specific customer by DB id. */
    Optional<Account> findByAccountIdAndCustomerCustomerId(
            Long accountId, Long customerId);

    /** Ownership check — account belongs to a customer by Keycloak sub. */
    Optional<Account> findByAccountIdAndCustomerKeycloakSub(
            Long accountId, String keycloakSub);

    /** All accounts for a customer identified by Keycloak sub. */
    List<Account> findByCustomerKeycloakSub(String keycloakSub);
}

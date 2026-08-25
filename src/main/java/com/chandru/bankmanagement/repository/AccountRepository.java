package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByAccountIdAndCustomerCustomerId(
            Long accountId,
            Long customerId
    );
}
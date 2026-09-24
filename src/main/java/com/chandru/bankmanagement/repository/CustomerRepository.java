package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Uniqueness check on update — another customer already uses this email. */
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, Long customerId);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByEmailIgnoreCase(String email);

    /** Resolve a Customer from the Keycloak JWT "sub" claim. */
    Optional<Customer> findByKeycloakSub(String keycloakSub);

    boolean existsByKeycloakSub(String keycloakSub);
}

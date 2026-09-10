package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    Optional<Customer> findByEmail(String email);

    /** Resolve a Customer from the Keycloak JWT "sub" claim. */
    Optional<Customer> findByKeycloakSub(String keycloakSub);

    boolean existsByKeycloakSub(String keycloakSub);
}

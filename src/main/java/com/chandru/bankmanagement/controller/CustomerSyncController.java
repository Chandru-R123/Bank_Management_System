package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.service.CustomerSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the frontend immediately after every successful Keycloak login.
 *
 * POST /api/auth/sync
 *   - Requires a valid CUSTOMER JWT (role = CUSTOMER)
 *   - Creates the Customer row in PostgreSQL if it doesn't exist yet
 *   - Returns the customer's profile (id, name, email, phone, address)
 *   - Idempotent — safe to call on every login
 *
 * This is how newly self-registered Keycloak users appear in the admin
 * customer list without any manual database intervention.
 */
@RestController
@RequestMapping("/api/auth")
public class CustomerSyncController {

    private final CustomerSyncService customerSyncService;

    public CustomerSyncController(CustomerSyncService customerSyncService) {
        this.customerSyncService = customerSyncService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/sync")
    public CustomerResponse sync(@AuthenticationPrincipal Jwt jwt) {
        return customerSyncService.syncCustomer(jwt);
    }
}

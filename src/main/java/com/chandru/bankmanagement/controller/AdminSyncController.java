package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.service.AdminSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/admin/sync-customers
 *
 * Called by the frontend when the admin opens the Customers page.
 * Pulls all CUSTOMER-role users from Keycloak and upserts them into
 * PostgreSQL — so users who registered but never logged in still appear.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSyncController {

    private final AdminSyncService adminSyncService;

    public AdminSyncController(AdminSyncService adminSyncService) {
        this.adminSyncService = adminSyncService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sync-customers")
    public Map<String, Object> syncCustomers() {
        int synced = adminSyncService.syncAllCustomers();
        return Map.of("synced", synced, "status", "ok");
    }
}

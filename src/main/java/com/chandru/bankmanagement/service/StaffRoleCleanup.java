package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.exception.ExternalServiceException;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Repairs staff logins that were created before StaffService started removing
 * the realm's default roles: they still carry CUSTOMER and therefore see
 * deposit / withdraw / transfer. See StaffService#removeCustomerAccess.
 *
 * Logins linked to a customer record are left alone (that person really is a
 * customer too). Runs in the background and waits for Keycloak, so it never
 * blocks startup.
 */
@Component
public class StaffRoleCleanup {

    private static final Logger log = LoggerFactory.getLogger(StaffRoleCleanup.class);

    private static final int MAX_ATTEMPTS = 40;
    private static final long RETRY_DELAY_MS = 15_000;

    private final KeycloakAdminClient keycloak;
    private final StaffService        staffService;
    private final CustomerRepository  customerRepository;

    public StaffRoleCleanup(KeycloakAdminClient keycloak,
                            StaffService staffService,
                            CustomerRepository customerRepository) {
        this.keycloak           = keycloak;
        this.staffService       = staffService;
        this.customerRepository = customerRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread worker = new Thread(this::runWhenKeycloakIsUp, "staff-role-cleanup");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWhenKeycloakIsUp() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                cleanup();
                return;
            } catch (ExternalServiceException e) {
                log.info("Staff role cleanup: Keycloak not ready yet (attempt {}/{})", attempt, MAX_ATTEMPTS);
            } catch (RuntimeException e) {
                log.warn("Staff role cleanup failed: {}", e.getMessage());
                return;
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Staff role cleanup: gave up waiting for Keycloak");
    }

    private void cleanup() {
        String token = keycloak.adminToken();
        Set<String> staffIds = new LinkedHashSet<>();
        for (String role : StaffService.STAFF_ROLES) {
            for (Map<String, Object> u : keycloak.roleUsersOrEmpty(token, role)) {
                staffIds.add(String.valueOf(u.get("id")));
            }
        }

        int fixed = 0;
        for (String id : staffIds) {
            if (customerRepository.existsByKeycloakSub(id)) continue;
            try {
                // Fresh token per user: master admin tokens only live 60 seconds
                String userToken = keycloak.adminToken();
                boolean hadCustomerAccess = keycloak.realmRoleNames(userToken, id).stream()
                        .anyMatch(StaffService::isCustomerAccessRole);
                if (hadCustomerAccess) {
                    staffService.removeCustomerAccess(userToken, id);
                    fixed++;
                }
            } catch (RuntimeException e) {
                log.warn("Staff role cleanup: user {} skipped: {}", id, e.getMessage());
            }
        }
        log.info("Staff role cleanup: {} staff logins checked, {} had customer access removed",
                staffIds.size(), fixed);
    }
}

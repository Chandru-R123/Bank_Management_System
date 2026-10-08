package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.exception.ExternalServiceException;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Makes sure every customer with a real email has a Keycloak login.
 *
 * Keycloak's "Forgot password" page only emails users that exist in Keycloak,
 * and shows the same "check your email" message when nobody matches. Branch
 * customers created without a login (or before logins were created
 * automatically), and the sample customers on a Keycloak volume that predates
 * the realm file, would therefore never get the reset email.
 *
 * On startup this links each such customer to an existing Keycloak login with
 * the same email, or creates one (username = email, no password — they set it
 * via "Forgot password"). The demo customers get their documented username and
 * password (Customer@1234). No emails are sent.
 *
 * Runs in the background and waits for Keycloak, so it never blocks startup.
 * Disable with BACKFILL_CUSTOMER_LOGINS=false.
 */
@Component
public class CustomerLoginBackfill {

    private static final Logger log = LoggerFactory.getLogger(CustomerLoginBackfill.class);

    private static final int MAX_ATTEMPTS = 40;
    private static final long RETRY_DELAY_MS = 15_000;

    private static final String DEMO_PASSWORD = "Customer@1234";

    /** Demo customers (DataInitializer + SampleDataInitializer): email → username in the realm file. */
    private static final Map<String, String> DEMO_USERNAMES = Map.of(
            "rahul.sharma@statebank.com", "rahul",
            "priya.venkat@statebank.com", "priya",
            "arjun.mehta@statebank.com",  "arjun",
            "meera.nair@example.com",     "meera",
            "karthik.s@example.com",      "karthik",
            "divya.krishnan@example.com", "divya",
            "vikram.reddy@example.com",   "vikram",
            "fatima.sheikh@example.com",  "fatima");

    private final CustomerRepository   customerRepository;
    private final KeycloakAdminClient  keycloak;
    private final OnlineBankingService onlineBanking;

    @Value("${app.backfill-customer-logins:true}")
    private boolean enabled;

    public CustomerLoginBackfill(CustomerRepository customerRepository,
                                 KeycloakAdminClient keycloak,
                                 OnlineBankingService onlineBanking) {
        this.customerRepository = customerRepository;
        this.keycloak           = keycloak;
        this.onlineBanking      = onlineBanking;
    }

    /** ApplicationReadyEvent fires after the CommandLineRunners, so the seed data exists. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        Thread worker = new Thread(this::runWhenKeycloakIsUp, "customer-login-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWhenKeycloakIsUp() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                keycloak.adminToken();
                backfill();
                return;
            } catch (ExternalServiceException e) {
                log.info("Login backfill: Keycloak not ready yet (attempt {}/{})", attempt, MAX_ATTEMPTS);
            } catch (RuntimeException e) {
                log.warn("Login backfill failed: {}", e.getMessage());
                return;
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Login backfill: gave up waiting for Keycloak");
    }

    private void backfill() {
        int created = 0, linked = 0, failed = 0;
        for (Customer c : customerRepository.findAll()) {
            if (ResponseMapper.hasOnlineLogin(c) || !OnlineBankingService.hasRealEmail(c)) continue;

            String email = c.getEmail().trim().toLowerCase(Locale.ROOT);
            String demoUsername = DEMO_USERNAMES.get(email);
            try {
                // Fresh token per customer: master admin tokens only live 60 seconds
                String token = keycloak.adminToken();
                var result = onlineBanking.provisionLogin(token, c, demoUsername,
                        demoUsername != null ? DEMO_PASSWORD : null);
                if (result.created()) created++; else linked++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Login backfill: customer id={} ({}) skipped: {}", c.getCustomerId(), email, e.getMessage());
            }
        }
        log.info("Login backfill: {} logins created, {} linked, {} skipped", created, linked, failed);
    }
}

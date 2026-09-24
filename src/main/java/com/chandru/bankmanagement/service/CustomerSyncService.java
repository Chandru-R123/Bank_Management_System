package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs a Keycloak user into the local customers table on first login.
 *
 * Called from CustomerSyncController after every successful Keycloak login.
 * It is fully idempotent — if the customer row already exists it returns it
 * unchanged, so calling it on every login is safe.
 *
 * Fields mapped from the Keycloak JWT:
 *   sub              → keycloak_sub (unique link between KC user and DB row)
 *   email            → email
 *   given_name       → first part of name
 *   family_name      → second part of name
 *   preferred_username → fallback name if given/family are missing
 *   phone            → phone   (Keycloak user-profile attribute, required at registration)
 *   postal_address   → address (Keycloak user-profile attribute, required at registration)
 */
@Service
public class CustomerSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerSyncService.class);

    private final CustomerRepository customerRepository;

    public CustomerSyncService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Ensures a Customer row exists for the authenticated Keycloak user.
     *
     * @param jwt  the validated Keycloak access token
     * @return the existing or newly created CustomerResponse
     */
    @Transactional
    public CustomerResponse syncCustomer(Jwt jwt) {

        String sub       = jwt.getSubject();
        String email     = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName  = jwt.getClaimAsString("family_name");
        String preferred = jwt.getClaimAsString("preferred_username");
        String phone     = trimToNull(jwt.getClaimAsString("phone"));
        String address   = trimToNull(jwt.getClaimAsString("postal_address"));

        String name = buildName(firstName, lastName, preferred, email);

        // ── 1. Already linked by sub — most common path ───────────────
        return customerRepository.findByKeycloakSub(sub)
                .map(existing -> {
                    boolean changed = fillContactDetails(existing, phone, address);
                    // Keep email in sync if it changed in Keycloak
                    if (email != null && !email.equalsIgnoreCase(existing.getEmail())
                            && !customerRepository.existsByEmailIgnoreCase(email)) {
                        existing.setEmail(email);
                        changed = true;
                    }
                    if (changed) customerRepository.save(existing);
                    return ResponseMapper.toResponse(existing);
                })
                .orElseGet(() -> {
                    // ── 2. Sub not matched — check by email ──────────────
                    //    This handles the case where the Customer row was
                    //    seeded/created before Keycloak was integrated, or
                    //    after a Keycloak volume wipe that issued new UUIDs.
                    if (email != null) {
                        var byEmail = customerRepository.findByEmailIgnoreCase(email);
                        if (byEmail.isPresent()) {
                            Customer existing = byEmail.get();
                            log.info("CustomerSync: linking sub={} to existing customer id={} by email",
                                    sub, existing.getCustomerId());
                            existing.setKeycloakSub(sub);
                            fillContactDetails(existing, phone, address);
                            customerRepository.save(existing);
                            return ResponseMapper.toResponse(existing);
                        }
                    }

                    // ── 3. Completely new user — create the Customer row ──
                    log.info("CustomerSync: creating new customer for sub={} email={}", sub, email);
                    Customer c = new Customer();
                    c.setKeycloakSub(sub);
                    c.setName(name);
                    c.setEmail(email != null ? email : sub + "@pending.local");
                    c.setPhone(phone != null ? phone : "");
                    c.setAddress(address != null ? address : "");
                    Customer saved = customerRepository.save(c);
                    log.info("CustomerSync: created customer id={} for sub={}", saved.getCustomerId(), sub);
                    return ResponseMapper.toResponse(saved);
                });
    }

    // ── helpers ────────────────────────────────────────────────────────

    /**
     * Copies phone/address from Keycloak only where the bank record is empty —
     * details edited later in online banking or by staff are never overwritten.
     */
    private boolean fillContactDetails(Customer c, String phone, String address) {
        boolean changed = false;
        if (phone != null && (c.getPhone() == null || c.getPhone().isBlank())) {
            c.setPhone(phone);
            changed = true;
        }
        if (address != null && (c.getAddress() == null || c.getAddress().isBlank())) {
            c.setAddress(address);
            changed = true;
        }
        return changed;
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String buildName(String firstName, String lastName,
                              String preferred, String email) {
        if (firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank()) {
            return firstName.trim() + " " + lastName.trim();
        }
        if (firstName != null && !firstName.isBlank()) {
            return firstName.trim();
        }
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Customer";
    }
}

package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.ActionResponse;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.ExternalServiceException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Online-banking logins for customers created at the branch.
 *
 * A customer created by staff is only a database record — Keycloak knows
 * nothing about them, so "Forgot password" has nobody to email. Enabling
 * online banking creates their Keycloak login (username = email, role
 * CUSTOMER), links it to the customer record and emails a link to set the
 * password. From then on the normal Keycloak "Forgot password" works too.
 */
@Service
public class OnlineBankingService {

    private static final Logger log = LoggerFactory.getLogger(OnlineBankingService.class);

    private final CustomerRepository  customerRepository;
    private final KeycloakAdminClient keycloak;

    public OnlineBankingService(CustomerRepository customerRepository,
                                KeycloakAdminClient keycloak) {
        this.customerRepository = customerRepository;
        this.keycloak           = keycloak;
    }

    @Transactional
    public ActionResponse<CustomerResponse> enable(Long customerId, Actor actor) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        if (ResponseMapper.hasOnlineLogin(c)) {
            throw new BusinessRuleException(
                    "This customer already has online banking. Use 'Send password reset email' instead.");
        }
        String token = keycloak.adminToken();
        Provisioned login = provisionLogin(token, c, null, null);
        String email = c.getEmail().trim().toLowerCase(Locale.ROOT);
        String outcome = login.created()
                ? "Online-banking login created (username: " + email + ")"
                : "Linked to the customer's existing online-banking login";
        String userId = login.userId();
        log.info("Online banking enabled for customer id={} (login {}) by {}",
                customerId, userId, actor.username());

        boolean emailSent = trySendPasswordEmail(token, userId);
        String message = outcome + (emailSent
                ? ". A link to set the password was emailed to " + email + "."
                : ". The email could not be sent — check Keycloak's email (SMTP) settings, "
                  + "then use 'Send password reset email'.");
        return new ActionResponse<>(ResponseMapper.toResponse(c), emailSent, message);
    }

    /** Emails a "reset your password" link to a customer who already has online banking. */
    public ActionResponse<CustomerResponse> sendPasswordEmail(Long customerId) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        if (!ResponseMapper.hasOnlineLogin(c)) {
            throw new BusinessRuleException("This customer has no online-banking login yet. Enable online banking first.");
        }
        String token = keycloak.adminToken();
        keycloak.sendActionsEmail(token, c.getKeycloakSub(), List.of("UPDATE_PASSWORD"));
        return new ActionResponse<>(ResponseMapper.toResponse(c), true,
                "A password reset link was emailed to " + c.getEmail() + ".");
    }

    /**
     * Changes the email of the customer's Keycloak login (if they have one).
     * Keycloak's "Forgot password" looks users up by that email.
     */
    void updateLoginEmail(Customer c, String newEmail) {
        if (!ResponseMapper.hasOnlineLogin(c)) return;
        String token = keycloak.adminToken();
        try {
            // Send the full representation back (a partial update can clear phone / address)
            Map<String, Object> user = new LinkedHashMap<>(keycloak.getUser(token, c.getKeycloakSub()));
            if (newEmail.equalsIgnoreCase(String.valueOf(user.get("email")))) return;
            user.put("email", newEmail);
            user.put("emailVerified", true);
            keycloak.updateUser(token, c.getKeycloakSub(), user);
            log.info("Login email of customer id={} changed to {}", c.getCustomerId(), newEmail);
        } catch (ResourceNotFoundException e) {
            log.warn("Customer id={} is linked to a login that no longer exists in Keycloak", c.getCustomerId());
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    /** Result of {@link #provisionLogin}: the Keycloak user id and whether it was newly created. */
    public record Provisioned(String userId, boolean created) { }

    /** True when the customer has an email Keycloak can send to (not a sync placeholder). */
    static boolean hasRealEmail(Customer c) {
        String email = c.getEmail() == null ? "" : c.getEmail().trim().toLowerCase(Locale.ROOT);
        return !email.isEmpty() && !email.endsWith("@pending.local");
    }

    /**
     * Gives the customer a Keycloak login and links it to the record. No email is sent.
     *
     * If a customer login with the same email already exists it is linked;
     * otherwise a new one is created (username = email unless {@code username}
     * is given, no password unless {@code password} is given). Keycloak's
     * "Forgot password" can only email customers that have a login.
     */
    Provisioned provisionLogin(String token, Customer c, String username, String password) {
        if (!hasRealEmail(c)) {
            throw new BusinessRuleException("Add a real email address to the customer before enabling online banking");
        }
        String email = c.getEmail().trim().toLowerCase(Locale.ROOT);

        String userId;
        boolean created;
        var existing = keycloak.findByEmail(token, email);
        if (existing.isPresent()) {
            // They already have a login with this email — just link it
            userId = String.valueOf(existing.get().get("id"));
            List<String> roles = keycloak.realmRoleNames(token, userId);
            if (roles.stream().anyMatch(StaffService.STAFF_ROLES::contains) || roles.contains("TPP")) {
                throw new BusinessRuleException("This email belongs to a staff or partner login, not a customer");
            }
            var linked = customerRepository.findByKeycloakSub(userId);
            if (linked.isPresent() && !linked.get().getCustomerId().equals(c.getCustomerId())) {
                throw new BusinessRuleException("That login is already linked to another customer record");
            }
            if (!roles.contains("CUSTOMER")) {
                keycloak.addRealmRoles(token, userId, List.of("CUSTOMER"));
            }
            created = false;
        } else {
            userId = keycloak.createUser(token, representation(c, email, username == null ? email : username));
            keycloak.addRealmRoles(token, userId, List.of("CUSTOMER"));
            if (password != null) keycloak.setPassword(token, userId, password, false);
            created = true;
        }

        c.setKeycloakSub(userId);
        customerRepository.save(c);
        return new Provisioned(userId, created);
    }

    private Map<String, Object> representation(Customer c, String email, String username) {
        String[] parts = c.getName() == null ? new String[0] : c.getName().trim().split("\\s+", 2);
        String first = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "Customer";
        String last = parts.length > 1 ? parts[1] : first;

        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (c.getPhone() != null && !c.getPhone().isBlank()) attributes.put("phone", List.of(c.getPhone().trim()));
        if (c.getAddress() != null && !c.getAddress().isBlank()) attributes.put("address", List.of(c.getAddress().trim()));

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("username", username);
        rep.put("email", email);
        rep.put("firstName", first);
        rep.put("lastName", last);
        rep.put("enabled", true);
        rep.put("emailVerified", true);
        rep.put("attributes", attributes);
        return rep;
    }

    private boolean trySendPasswordEmail(String token, String userId) {
        try {
            keycloak.sendActionsEmail(token, userId, List.of("UPDATE_PASSWORD"));
            return true;
        } catch (ExternalServiceException | ResourceNotFoundException e) {
            log.warn("Could not send password email to {}: {}", userId, e.getMessage());
            return false;
        }
    }
}

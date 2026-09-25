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
        String email = c.getEmail() == null ? "" : c.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.endsWith("@pending.local")) {
            throw new BusinessRuleException("Add a real email address to the customer before enabling online banking");
        }

        String token = keycloak.adminToken();
        String userId;
        String outcome;

        var existing = keycloak.findByEmail(token, email);
        if (existing.isPresent()) {
            // They already registered online with this email — just link it
            userId = String.valueOf(existing.get().get("id"));
            List<String> roles = keycloak.realmRoleNames(token, userId);
            if (roles.stream().anyMatch(StaffService.STAFF_ROLES::contains) || roles.contains("TPP")) {
                throw new BusinessRuleException("This email belongs to a staff or partner login, not a customer");
            }
            if (customerRepository.findByKeycloakSub(userId).isPresent()) {
                throw new BusinessRuleException("That login is already linked to another customer record");
            }
            if (!roles.contains("CUSTOMER")) {
                keycloak.addRealmRoles(token, userId, List.of("CUSTOMER"));
            }
            outcome = "Linked to the customer's existing online-banking login";
        } else {
            userId = keycloak.createUser(token, representation(c, email));
            keycloak.addRealmRoles(token, userId, List.of("CUSTOMER"));
            outcome = "Online-banking login created (username: " + email + ")";
        }

        c.setKeycloakSub(userId);
        Customer saved = customerRepository.save(c);
        log.info("Online banking enabled for customer id={} (login {}) by {}",
                customerId, userId, actor.username());

        boolean emailSent = trySendPasswordEmail(token, userId);
        String message = outcome + (emailSent
                ? ". A link to set the password was emailed to " + email + "."
                : ". The email could not be sent — check Keycloak's email (SMTP) settings, "
                  + "then use 'Send password reset email'.");
        return new ActionResponse<>(ResponseMapper.toResponse(saved), emailSent, message);
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

    // ── helpers ────────────────────────────────────────────────────────

    private Map<String, Object> representation(Customer c, String email) {
        String[] parts = c.getName() == null ? new String[0] : c.getName().trim().split("\\s+", 2);
        String first = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "Customer";
        String last = parts.length > 1 ? parts[1] : first;

        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (c.getPhone() != null && !c.getPhone().isBlank()) attributes.put("phone", List.of(c.getPhone().trim()));
        if (c.getAddress() != null && !c.getAddress().isBlank()) attributes.put("address", List.of(c.getAddress().trim()));

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("username", email);
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

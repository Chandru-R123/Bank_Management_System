package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Syncs ALL Keycloak users that have the CUSTOMER role into PostgreSQL.
 *
 * Called when the admin opens the Customers page — so any user who
 * registered via Keycloak's registration form appears immediately,
 * even before they have ever logged into the application.
 *
 * Flow:
 *   1. Get a service-account token from Keycloak (client-credentials)
 *   2. Fetch all users in the bank-management realm
 *   3. For each user that has the CUSTOMER role:
 *      - If keycloak_sub already in DB → skip (already synced)
 *      - If email already in DB → link the keycloak_sub
 *      - Otherwise → create a new Customer row
 */
@Service
public class AdminSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(AdminSyncService.class);

    private final CustomerRepository customerRepository;
    private final RestTemplate        restTemplate;

    @Value("${keycloak.admin.server-url:http://keycloak:8180}")
    private String keycloakServerUrl;

    @Value("${keycloak.admin.realm:bank-management}")
    private String realm;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    public AdminSyncService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
        this.restTemplate        = new RestTemplate();
    }

    @Transactional
    public int syncAllCustomers() {

        String token = getMasterAdminToken();
        if (token == null) {
            log.error("AdminSync: cannot get Keycloak admin token");
            return 0;
        }

        List<Map<String, Object>> allUsers = getRealmUsers(token);
        log.info("AdminSync: found {} users in Keycloak realm", allUsers.size());

        int synced = 0;
        for (Map<String, Object> user : allUsers) {
            String sub   = (String) user.get("id");
            String email = (String) user.get("email");
            String firstName = (String) user.getOrDefault("firstName", "");
            String lastName  = (String) user.getOrDefault("lastName",  "");
            String username  = (String) user.getOrDefault("username",  "");

            // Skip users with no CUSTOMER role
            if (!hasCustomerRole(token, sub)) continue;

            // Build display name
            String name = buildName(firstName, lastName, username, email);

            try {
                // 1. Already linked by sub
                if (customerRepository.findByKeycloakSub(sub).isPresent()) {
                    continue;
                }

                // 2. Existing row by email — link the sub
                if (email != null && !email.isBlank()) {
                    var byEmail = customerRepository.findByEmail(email);
                    if (byEmail.isPresent()) {
                        Customer c = byEmail.get();
                        c.setKeycloakSub(sub);
                        if (name != null && !name.isBlank()) c.setName(name);
                        customerRepository.save(c);
                        log.info("AdminSync: linked sub to existing customer id={}", c.getCustomerId());
                        synced++;
                        continue;
                    }
                }

                // 3. Brand new — create Customer row
                Customer c = new Customer();
                c.setKeycloakSub(sub);
                c.setName(name != null && !name.isBlank() ? name : username);
                c.setEmail(email != null && !email.isBlank()
                        ? email : sub + "@pending.local");
                c.setPhone("");
                c.setAddress("");
                customerRepository.save(c);
                log.info("AdminSync: created new customer '{}' for sub={}", c.getName(), sub);
                synced++;

            } catch (Exception e) {
                log.warn("AdminSync: skipped user sub={} reason={}", sub, e.getMessage());
            }
        }

        log.info("AdminSync: synced {} new/updated customers", synced);
        return synced;
    }

    // ── Keycloak Admin API calls ───────────────────────────────────────────

    private String getMasterAdminToken() {
        try {
            String url = keycloakServerUrl
                    + "/realms/master/protocol/openid-connect/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "grant_type=password"
                    + "&client_id=admin-cli"
                    + "&username=" + adminUsername
                    + "&password=" + adminPassword;

            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            return (String) resp.getBody().get("access_token");
        } catch (Exception e) {
            log.error("AdminSync: failed to get admin token: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRealmUsers(String token) {
        try {
            String url = keycloakServerUrl
                    + "/admin/realms/" + realm + "/users?max=500";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            return resp.getBody() != null ? resp.getBody() : List.of();
        } catch (Exception e) {
            log.error("AdminSync: failed to fetch users: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasCustomerRole(String token, String userId) {
        try {
            String url = keycloakServerUrl
                    + "/admin/realms/" + realm
                    + "/users/" + userId + "/role-mappings/realm";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            if (resp.getBody() == null) return false;

            return ((List<Map<String, Object>>) resp.getBody())
                    .stream()
                    .anyMatch(r -> "CUSTOMER".equals(r.get("name")));
        } catch (Exception e) {
            return false;
        }
    }

    private String buildName(String firstName, String lastName,
                              String username, String email) {
        if (!isBlank(firstName) && !isBlank(lastName))
            return firstName.trim() + " " + lastName.trim();
        if (!isBlank(firstName)) return firstName.trim();
        if (!isBlank(username))  return username.trim();
        if (email != null && email.contains("@"))
            return email.substring(0, email.indexOf('@'));
        return "Customer";
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

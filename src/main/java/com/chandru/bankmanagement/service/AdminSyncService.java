package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Syncs ALL Keycloak users that have the CUSTOMER role into PostgreSQL.
 *
 * Called when staff open the Customers page — so any user who
 * registered via Keycloak's registration form appears immediately,
 * even before they have ever logged into the application.
 *
 * Flow:
 *   1. Get an admin token from Keycloak's master realm (admin-cli)
 *   2. Collect customer candidates:
 *        users mapped to CUSTOMER directly
 *      + users holding default-roles-{realm} (self-registered users get
 *        CUSTOMER through this composite, not as a direct mapping)
 *      − users holding ADMIN, EMPLOYEE, MAKER, CHECKER or TPP (never customers)
 *   3. For each user:
 *      - If keycloak_sub already in DB → skip (already synced)
 *      - If email already in DB → link the keycloak_sub
 *      - Otherwise → create a new Customer row
 *
 * Deliberately NOT @Transactional: each save commits on its own, so one
 * bad record (e.g. a duplicate email) cannot roll back the whole sync.
 */
@Service
public class AdminSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(AdminSyncService.class);

    private static final int PAGE_SIZE = 200;

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

    public int syncAllCustomers() {

        String token = getMasterAdminToken();
        if (token == null) {
            log.error("AdminSync: cannot get Keycloak admin token");
            return 0;
        }

        List<Map<String, Object>> customers = getCustomerUsers(token);
        log.info("AdminSync: found {} CUSTOMER users in Keycloak realm", customers.size());

        int synced = 0;
        for (Map<String, Object> user : customers) {
            String sub       = str(user.get("id"));
            String email     = str(user.get("email"));
            String firstName = str(user.get("firstName"));
            String lastName  = str(user.get("lastName"));
            String username  = str(user.get("username"));
            String phone     = attribute(user, "phone");
            String address   = attribute(user, "address");

            if (sub == null) continue;

            String name = buildName(firstName, lastName, username, email);

            try {
                // 1. Already linked by sub — only fill in missing contact details
                var bySub = customerRepository.findByKeycloakSub(sub);
                if (bySub.isPresent()) {
                    if (fillContactDetails(bySub.get(), phone, address)) {
                        customerRepository.save(bySub.get());
                        synced++;
                    }
                    continue;
                }

                // 2. Existing row by email — link the sub
                if (!isBlank(email)) {
                    var byEmail = customerRepository.findByEmailIgnoreCase(email);
                    if (byEmail.isPresent()) {
                        Customer c = byEmail.get();
                        c.setKeycloakSub(sub);
                        fillContactDetails(c, phone, address);
                        customerRepository.save(c);
                        log.info("AdminSync: linked sub to existing customer id={}", c.getCustomerId());
                        synced++;
                        continue;
                    }
                }

                // 3. Brand new — create Customer row
                Customer c = new Customer();
                c.setKeycloakSub(sub);
                c.setName(name);
                c.setEmail(!isBlank(email) ? email.toLowerCase() : sub + "@pending.local");
                c.setPhone(phone != null ? phone : "");
                c.setAddress(address != null ? address : "");
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

    @SuppressWarnings("rawtypes")
    private String getMasterAdminToken() {
        try {
            String url = keycloakServerUrl
                    + "/realms/master/protocol/openid-connect/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Form-encoded properly so passwords with &, = or % still work
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("client_id", "admin-cli");
            form.add("username", adminUsername);
            form.add("password", adminPassword);

            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(form, headers), Map.class);

            return resp.getBody() != null ? (String) resp.getBody().get("access_token") : null;
        } catch (Exception e) {
            log.error("AdminSync: failed to get admin token: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> getCustomerUsers(String token) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> u : getRoleUsers(token, "CUSTOMER")) {
            byId.put(str(u.get("id")), u);
        }
        for (Map<String, Object> u : getRoleUsers(token, "default-roles-" + realm)) {
            byId.putIfAbsent(str(u.get("id")), u);
        }

        Set<String> staffIds = new HashSet<>();
        for (String staffRole : List.of("ADMIN", "EMPLOYEE", "MAKER", "CHECKER")) {
            for (Map<String, Object> u : getRoleUsers(token, staffRole)) {
                staffIds.add(str(u.get("id")));
            }
        }
        // Third-party provider apps are not bank customers either
        for (Map<String, Object> u : getRoleUsers(token, "TPP")) {
            staffIds.add(str(u.get("id")));
        }
        staffIds.forEach(byId::remove);
        byId.remove(null);
        return new ArrayList<>(byId.values());
    }

    /**
     * GET /admin/realms/{realm}/roles/{role}/users — one request per page
     * instead of one role-mapping request per user.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> getRoleUsers(String token, String role) {
        List<Map<String, Object>> all = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            for (int first = 0; ; first += PAGE_SIZE) {
                String url = keycloakServerUrl + "/admin/realms/" + realm
                        + "/roles/" + role + "/users?briefRepresentation=false"
                        + "&first=" + first + "&max=" + PAGE_SIZE;

                ResponseEntity<List> resp = restTemplate.exchange(
                        url, HttpMethod.GET,
                        new HttpEntity<>(headers), List.class);

                List<Map<String, Object>> page = resp.getBody();
                if (page == null || page.isEmpty()) break;
                all.addAll(page);
                if (page.size() < PAGE_SIZE) break;
            }
        } catch (Exception e) {
            log.warn("AdminSync: failed to fetch users for role {}: {}", role, e.getMessage());
        }
        return all;
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

    /** Keycloak returns custom attributes as {"phone": ["9876543210"]}. */
    private static String attribute(Map<String, Object> user, String name) {
        if (!(user.get("attributes") instanceof Map<?, ?> attrs)) return null;
        Object v = attrs.get(name);
        if (v instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            String s = String.valueOf(list.get(0)).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static boolean fillContactDetails(Customer c, String phone, String address) {
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

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

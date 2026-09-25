package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.ExternalServiceException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client for the Keycloak Admin REST API (realm "bank-management").
 *
 * Authenticates as the master-realm admin through admin-cli and is used to:
 *  - sync self-registered customers into PostgreSQL
 *  - create staff logins (EMPLOYEE / MAKER / CHECKER / ADMIN)
 *  - create online-banking logins for branch customers
 *  - send "set / reset your password" emails (Keycloak execute-actions-email)
 *
 * Every call takes an admin token so a multi-step operation fetches it once.
 */
@Service
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final int PAGE_SIZE = 200;

    /** "Set your password" links stay valid for 24 hours. */
    private static final int ACTION_EMAIL_LIFESPAN_SECONDS = 24 * 60 * 60;

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() { };

    private final RestTemplate rest = new RestTemplate();

    @Value("${keycloak.admin.server-url:http://keycloak:8180}")
    private String serverUrl;

    @Value("${keycloak.admin.realm:bank-management}")
    private String realm;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    /** Client whose login page the "set password" email returns the user to. */
    @Value("${keycloak.admin.client-id:bank-management-backend}")
    private String clientId;

    /** Where the user lands after setting the password (must be a valid redirect URI of the client). */
    @Value("${app.public-url:http://localhost:8080/}")
    private String publicUrl;

    // ── auth ───────────────────────────────────────────────────────────

    public String adminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    serverUrl + "/realms/master/protocol/openid-connect/token",
                    HttpMethod.POST, new HttpEntity<>(form, headers), MAP);
            Object token = resp.getBody() != null ? resp.getBody().get("access_token") : null;
            if (token == null) throw new ExternalServiceException("Keycloak returned no admin token");
            return token.toString();
        } catch (HttpStatusCodeException | ResourceAccessException e) {
            log.error("Keycloak admin login failed: {}", e.getMessage());
            throw new ExternalServiceException(
                    "Cannot reach the identity service (Keycloak). Please try again shortly.");
        }
    }

    // ── users ──────────────────────────────────────────────────────────

    /** Users directly mapped to a realm role (paged). Empty list on any error. */
    public List<Map<String, Object>> roleUsersOrEmpty(String token, String role) {
        List<Map<String, Object>> all = new ArrayList<>();
        try {
            for (int first = 0; ; first += PAGE_SIZE) {
                URI uri = uri("/roles/" + role + "/users")
                        .queryParam("briefRepresentation", false)
                        .queryParam("first", first)
                        .queryParam("max", PAGE_SIZE)
                        .build().encode().toUri();
                List<Map<String, Object>> page = get(token, uri, LIST_OF_MAPS);
                if (page == null || page.isEmpty()) break;
                all.addAll(page);
                if (page.size() < PAGE_SIZE) break;
            }
        } catch (RuntimeException e) {
            log.warn("Keycloak: failed to list users of role {}: {}", role, e.getMessage());
        }
        return all;
    }

    public Optional<Map<String, Object>> findByEmail(String token, String email) {
        return firstExact(token, "email", email);
    }

    public Optional<Map<String, Object>> findByUsername(String token, String username) {
        return firstExact(token, "username", username);
    }

    public Map<String, Object> getUser(String token, String userId) {
        return call(() -> get(token, uri("/users/" + userId).build().toUri(), MAP), "load the user");
    }

    /** Creates a user and returns its id. */
    public String createUser(String token, Map<String, Object> representation) {
        return call(() -> {
            ResponseEntity<Void> resp = rest.exchange(uri("/users").build().toUri(), HttpMethod.POST,
                    new HttpEntity<>(representation, json(token)), Void.class);
            URI location = resp.getHeaders().getLocation();
            if (location == null) throw new ExternalServiceException("Keycloak did not return the new user id");
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }, "create the login");
    }

    /** Updates a user. Pass the full representation (see StaffService#setEnabled). */
    public void updateUser(String token, String userId, Map<String, Object> fields) {
        call(() -> rest.exchange(uri("/users/" + userId).build().toUri(), HttpMethod.PUT,
                new HttpEntity<>(fields, json(token)), Void.class), "update the login");
    }

    // ── roles ──────────────────────────────────────────────────────────

    /** Names of the realm roles mapped DIRECTLY to the user. */
    public List<String> realmRoleNames(String token, String userId) {
        List<Map<String, Object>> roles = call(() -> get(token,
                uri("/users/" + userId + "/role-mappings/realm").build().toUri(), LIST_OF_MAPS), "read roles");
        return roles == null ? List.of()
                : roles.stream().map(r -> String.valueOf(r.get("name"))).toList();
    }

    public void addRealmRoles(String token, String userId, Collection<String> roleNames) {
        if (roleNames.isEmpty()) return;
        List<Map<String, Object>> reps = roleRepresentations(token, roleNames);
        call(() -> rest.exchange(uri("/users/" + userId + "/role-mappings/realm").build().toUri(),
                HttpMethod.POST, new HttpEntity<>(reps, json(token)), Void.class), "assign roles");
    }

    public void removeRealmRoles(String token, String userId, Collection<String> roleNames) {
        if (roleNames.isEmpty()) return;
        List<Map<String, Object>> reps = roleRepresentations(token, roleNames);
        call(() -> rest.exchange(uri("/users/" + userId + "/role-mappings/realm").build().toUri(),
                HttpMethod.DELETE, new HttpEntity<>(reps, json(token)), Void.class), "remove roles");
    }

    private List<Map<String, Object>> roleRepresentations(String token, Collection<String> roleNames) {
        List<Map<String, Object>> reps = new ArrayList<>();
        for (String name : roleNames) {
            reps.add(call(() -> get(token, uri("/roles/" + name).build().toUri(), MAP), "find role " + name));
        }
        return reps;
    }

    // ── passwords & emails ─────────────────────────────────────────────

    /**
     * Emails the user a link to perform the given actions (e.g. UPDATE_PASSWORD).
     * Uses the realm's SMTP settings (MailHog in local Docker).
     */
    public void sendActionsEmail(String token, String userId, List<String> actions) {
        URI uri = uri("/users/" + userId + "/execute-actions-email")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", publicUrl)
                .queryParam("lifespan", ACTION_EMAIL_LIFESPAN_SECONDS)
                .build().encode().toUri();
        try {
            rest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(actions, json(token)), Void.class);
        } catch (HttpStatusCodeException e) {
            log.warn("Keycloak execute-actions-email failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("The login no longer exists in Keycloak");
            }
            throw new ExternalServiceException(
                    "The email could not be sent. Check Keycloak → Realm settings → Email (SMTP) "
                            + "and that the user has an email address.");
        } catch (ResourceAccessException e) {
            throw new ExternalServiceException("Cannot reach the identity service (Keycloak).");
        }
    }

    /** Sets a password directly. {@code temporary} forces a change at next login. */
    public void setPassword(String token, String userId, String password, boolean temporary) {
        Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", temporary);
        try {
            rest.exchange(uri("/users/" + userId + "/reset-password").build().toUri(), HttpMethod.PUT,
                    new HttpEntity<>(credential, json(token)), Void.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 400) {
                throw new BusinessRuleException(
                        "Password rejected by the password policy (at least 8 characters).");
            }
            throw translate(e, "set the password");
        } catch (ResourceAccessException e) {
            throw new ExternalServiceException("Cannot reach the identity service (Keycloak).");
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Optional<Map<String, Object>> firstExact(String token, String field, String value) {
        URI uri = uri("/users").queryParam(field, value).queryParam("exact", true)
                .build().encode().toUri();
        List<Map<String, Object>> list = call(() -> get(token, uri, LIST_OF_MAPS), "search users");
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private <T> T get(String token, URI uri, ParameterizedTypeReference<T> type) {
        return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(json(token)), type).getBody();
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromUriString(serverUrl + "/admin/realms/" + realm + path);
    }

    private HttpHeaders json(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private interface KeycloakCall<T> {
        T run();
    }

    private <T> T call(KeycloakCall<T> action, String what) {
        try {
            return action.run();
        } catch (HttpStatusCodeException e) {
            throw translate(e, what);
        } catch (ResourceAccessException e) {
            throw new ExternalServiceException("Cannot reach the identity service (Keycloak).");
        }
    }

    private RuntimeException translate(HttpStatusCodeException e, String what) {
        int status = e.getStatusCode().value();
        log.warn("Keycloak call '{}' failed: {} {}", what, status, e.getResponseBodyAsString());
        return switch (status) {
            case 409 -> new BusinessRuleException("A login with this username or email already exists");
            case 404 -> new ResourceNotFoundException("Not found in Keycloak while trying to " + what);
            case 400 -> new BusinessRuleException("Keycloak rejected the request to " + what
                    + ": " + e.getResponseBodyAsString());
            default -> new ExternalServiceException("Keycloak could not " + what + " (HTTP " + status + ")");
        };
    }
}

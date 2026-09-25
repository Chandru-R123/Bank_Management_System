package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.ActionResponse;
import com.chandru.bankmanagement.dto.StaffRequest;
import com.chandru.bankmanagement.dto.StaffResponse;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.ExternalServiceException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Staff logins (ADMIN / EMPLOYEE / MAKER / CHECKER) live only in Keycloak —
 * there is no staff table. An ADMIN manages them from the Staff page.
 *
 * Password setup for a new staff member is either
 *   - an email with a link to set their own password (default), or
 *   - a temporary password that must be changed at first login.
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    public static final List<String> STAFF_ROLES = List.of("ADMIN", "EMPLOYEE", "MAKER", "CHECKER");
    private static final String DEFAULT_BRANCH = "State Bank, Coimbatore Branch";

    private final KeycloakAdminClient keycloak;

    public StaffService(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    // ── list ───────────────────────────────────────────────────────────

    public List<StaffResponse> list() {
        String token = keycloak.adminToken();
        Map<String, Map<String, Object>> users = new LinkedHashMap<>();
        Map<String, Set<String>> roles = new LinkedHashMap<>();
        for (String role : STAFF_ROLES) {
            for (Map<String, Object> u : keycloak.roleUsersOrEmpty(token, role)) {
                String id = String.valueOf(u.get("id"));
                users.putIfAbsent(id, u);
                roles.computeIfAbsent(id, k -> new TreeSet<>()).add(role);
            }
        }
        return users.entrySet().stream()
                .map(e -> toResponse(e.getValue(), roles.get(e.getKey())))
                .sorted(Comparator.comparing(StaffResponse::username))
                .toList();
    }

    // ── create ─────────────────────────────────────────────────────────

    public ActionResponse<StaffResponse> create(StaffRequest request, Actor actor) {
        Set<String> roles = validRoles(request.roles());
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        String token = keycloak.adminToken();
        if (keycloak.findByUsername(token, username).isPresent()) {
            throw new BusinessRuleException("Username '" + username + "' is already taken");
        }
        if (keycloak.findByEmail(token, email).isPresent()) {
            throw new BusinessRuleException("A login with email " + email + " already exists");
        }

        String address = request.address() == null || request.address().isBlank()
                ? DEFAULT_BRANCH : request.address().trim();
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("username", username);
        rep.put("email", email);
        rep.put("firstName", request.firstName().trim());
        rep.put("lastName", request.lastName().trim());
        rep.put("enabled", true);
        rep.put("emailVerified", true);
        rep.put("attributes", Map.of(
                "phone", List.of(request.phone().trim()),
                "address", List.of(address)));

        String id = keycloak.createUser(token, rep);
        keycloak.addRealmRoles(token, id, roles);
        log.info("Staff login '{}' created with roles {} by {}", username, roles, actor.username());

        boolean emailSent = false;
        String message;
        if (request.password() != null && !request.password().isBlank()) {
            keycloak.setPassword(token, id, request.password(), true);
            message = "Staff login created. Share the temporary password — "
                    + username + " must change it at first sign-in.";
        } else {
            emailSent = trySendPasswordEmail(token, id);
            message = emailSent
                    ? "Staff login created. A link to set the password was emailed to " + email + "."
                    : "Staff login created, but the email could not be sent. "
                      + "Set a temporary password instead, or fix Keycloak's email settings.";
        }
        StaffResponse staff = toResponse(keycloak.getUser(token, id), new TreeSet<>(roles));
        return new ActionResponse<>(staff, emailSent, message);
    }

    // ── roles ──────────────────────────────────────────────────────────

    public StaffResponse updateRoles(String id, Set<String> requested, Actor actor) {
        Set<String> wanted = validRoles(requested);
        if (id.equals(actor.sub()) && !wanted.contains("ADMIN")) {
            throw new BusinessRuleException("You cannot remove your own ADMIN role");
        }
        String token = keycloak.adminToken();
        Set<String> current = staffRolesOf(token, id);

        List<String> add = wanted.stream().filter(r -> !current.contains(r)).toList();
        List<String> remove = current.stream().filter(r -> !wanted.contains(r)).toList();
        keycloak.addRealmRoles(token, id, add);
        keycloak.removeRealmRoles(token, id, remove);
        log.info("Staff {} roles changed {} → {} by {}", id, current, wanted, actor.username());
        return toResponse(keycloak.getUser(token, id), new TreeSet<>(wanted));
    }

    // ── enable / disable ───────────────────────────────────────────────

    public StaffResponse setEnabled(String id, boolean enabled, Actor actor) {
        if (!enabled && id.equals(actor.sub())) {
            throw new BusinessRuleException("You cannot disable your own login");
        }
        String token = keycloak.adminToken();
        Set<String> roles = staffRolesOf(token, id);
        // Send the full representation back: on Keycloak 24 a partial update can
        // clear user-profile attributes (phone / address) that are not included.
        Map<String, Object> user = new LinkedHashMap<>(keycloak.getUser(token, id));
        user.put("enabled", enabled);
        keycloak.updateUser(token, id, user);
        log.info("Staff {} {} by {}", id, enabled ? "enabled" : "disabled", actor.username());
        return toResponse(keycloak.getUser(token, id), roles);
    }

    // ── passwords ──────────────────────────────────────────────────────

    public ActionResponse<StaffResponse> sendPasswordEmail(String id) {
        String token = keycloak.adminToken();
        Set<String> roles = staffRolesOf(token, id);
        Map<String, Object> user = keycloak.getUser(token, id);
        if (user.get("email") == null) {
            throw new BusinessRuleException("This staff member has no email address");
        }
        keycloak.sendActionsEmail(token, id, List.of("UPDATE_PASSWORD"));
        return new ActionResponse<>(toResponse(user, roles), true,
                "A password reset link was emailed to " + user.get("email") + ".");
    }

    public ActionResponse<StaffResponse> setTemporaryPassword(String id, String password) {
        String token = keycloak.adminToken();
        Set<String> roles = staffRolesOf(token, id);
        keycloak.setPassword(token, id, password, true);
        return new ActionResponse<>(toResponse(keycloak.getUser(token, id), roles), false,
                "Temporary password set — it must be changed at next sign-in.");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Set<String> validRoles(Set<String> requested) {
        Set<String> roles = new HashSet<>();
        for (String r : requested) {
            String role = r == null ? "" : r.trim().toUpperCase(Locale.ROOT);
            if (!STAFF_ROLES.contains(role)) {
                throw new BusinessRuleException("Unknown staff role '" + r + "'. Allowed: " + STAFF_ROLES);
            }
            roles.add(role);
        }
        if (roles.isEmpty()) throw new BusinessRuleException("Choose at least one role");
        return roles;
    }

    /** Current staff roles of a user; 404 if the user is not staff (protects customer logins). */
    private Set<String> staffRolesOf(String token, String id) {
        Set<String> roles = new TreeSet<>(keycloak.realmRoleNames(token, id));
        roles.retainAll(STAFF_ROLES);
        if (roles.isEmpty()) {
            throw new ResourceNotFoundException("Staff member not found");
        }
        return roles;
    }

    private boolean trySendPasswordEmail(String token, String id) {
        try {
            keycloak.sendActionsEmail(token, id, List.of("UPDATE_PASSWORD"));
            return true;
        } catch (ExternalServiceException | ResourceNotFoundException e) {
            log.warn("Could not send password email to staff {}: {}", id, e.getMessage());
            return false;
        }
    }

    private static StaffResponse toResponse(Map<String, Object> u, Set<String> roles) {
        String phone = null;
        if (u.get("attributes") instanceof Map<?, ?> attrs
                && attrs.get("phone") instanceof List<?> list && !list.isEmpty()) {
            phone = String.valueOf(list.get(0));
        }
        Object created = u.get("createdTimestamp");
        return new StaffResponse(
                String.valueOf(u.get("id")),
                (String) u.get("username"),
                (String) u.get("firstName"),
                (String) u.get("lastName"),
                (String) u.get("email"),
                phone,
                Boolean.TRUE.equals(u.get("enabled")),
                roles == null ? List.of() : new ArrayList<>(roles),
                created instanceof Number n ? n.longValue() : null);
    }
}

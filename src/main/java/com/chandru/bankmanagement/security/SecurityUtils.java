package com.chandru.bankmanagement.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static List<String> roles(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return List.of();
            Object roles = realmAccess.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Actor actor(Jwt jwt) {
        List<String> roles = roles(jwt);
        boolean admin = roles.contains("ADMIN");
        boolean staff = admin || roles.contains("EMPLOYEE");
        String username = jwt.getClaimAsString("preferred_username");
        return new Actor(jwt.getSubject(),
                username != null ? username : jwt.getSubject(),
                admin, staff);
    }
}

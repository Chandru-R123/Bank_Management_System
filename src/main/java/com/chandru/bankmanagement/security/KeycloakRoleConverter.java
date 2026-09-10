package com.chandru.bankmanagement.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts roles from the Keycloak JWT claim:
 *
 *   realm_access.roles → ["ADMIN", "CUSTOMER", "EMPLOYEE", ...]
 *
 * and maps each to a Spring Security GrantedAuthority with the
 * standard "ROLE_" prefix so that @PreAuthorize("hasRole('ADMIN')")
 * works without any changes to the controllers.
 */
public class KeycloakRoleConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        // realm_access is a Map<String, Object> in the JWT payload
        Map<String, Object> realmAccess =
                jwt.getClaimAsMap("realm_access");

        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles.stream()
                // Skip Keycloak's own built-in roles
                .filter(role -> !role.startsWith("default-roles-")
                        && !role.equals("offline_access")
                        && !role.equals("uma_authorization"))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}

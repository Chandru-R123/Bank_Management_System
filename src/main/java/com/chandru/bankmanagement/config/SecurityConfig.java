package com.chandru.bankmanagement.config;

import com.chandru.bankmanagement.security.KeycloakRoleConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * OAuth2 Resource Server security configuration.
 *
 * Authentication:  Keycloak issues the JWT — Spring validates signature
 *                  and expiry automatically using the JWKS URI derived
 *                  from the issuer-uri.
 *
 * Authorisation:   Roles are extracted from realm_access.roles by
 *                  KeycloakRoleConverter and prefixed with ROLE_ so that
 *                  @PreAuthorize("hasRole('ADMIN')") works in controllers.
 *
 * No session, no passwords in PostgreSQL.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    // ------------------------------------------------------------------
    // JWT decoder — no-arg @Bean so it can be referenced within the
    // same @Configuration class.
    //
    // If jwk-set-uri is set (e.g. in tests), use it directly to avoid
    // the OIDC discovery HTTP call that withIssuerLocation() makes at
    // bean construction time.  In production only issuer-uri is set.
    // ------------------------------------------------------------------

    @Bean
    public JwtDecoder jwtDecoder() {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }

    // ------------------------------------------------------------------
    // Role converter — realm_access.roles → ROLE_ADMIN / ROLE_CUSTOMER …
    // ------------------------------------------------------------------

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    // ------------------------------------------------------------------
    // Security filter chain
    // ------------------------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── 401 / 403 JSON responses ──────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.setHeader("Access-Control-Allow-Origin",
                            req.getHeader("Origin"));
                    res.getWriter().write("""
                        {"status":401,"error":"Unauthorized",\
"message":"Authentication required — provide a valid Bearer token"}
                        """);
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.setHeader("Access-Control-Allow-Origin",
                            req.getHeader("Origin"));
                    res.getWriter().write("""
                        {"status":403,"error":"Forbidden",\
"message":"You do not have permission to access this resource"}
                        """);
                })
            )

            // ── Route rules ───────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public: health/info, Swagger + OPTIONS pre-flight
                .requestMatchers(
                    "/health",
                    "/api/health",
                    "/api/info",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/health"
                ).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Everything else must carry a valid Keycloak JWT
                .anyRequest().authenticated()
            )

            // ── OAuth2 resource server — JWT validation ────────────────
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    // ------------------------------------------------------------------
    // CORS — allow Vite dev server and production origin
    // ------------------------------------------------------------------

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:8080",   // NGINX gateway (Docker + prod)
            "http://localhost:5173",   // Vite dev
            "http://localhost:4173",   // Vite preview
            "http://localhost:3000"    // CRA / other
        ));
        config.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

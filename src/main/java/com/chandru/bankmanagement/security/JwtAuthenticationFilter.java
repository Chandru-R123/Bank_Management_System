package com.chandru.bankmanagement.security;

import com.chandru.bankmanagement.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        String authHeader =
                request.getHeader("Authorization");

        System.out.println(
                "JWT FILTER: Request = " + requestUri
        );

        // No token
        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            System.out.println(
                    "JWT FILTER: No Bearer token"
            );

            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7).trim();

        // Empty token
        if (jwt.isEmpty()) {

            System.out.println(
                    "JWT FILTER: Empty token"
            );

            filterChain.doFilter(request, response);
            return;
        }

        try {

            // Extract username
            String username =
                    jwtService.extractUsername(jwt);

            System.out.println(
                    "JWT FILTER: Username = " + username
            );

            if (username != null &&
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication() == null) {

                // Load user from database
                UserDetails userDetails =
                        userDetailsService
                                .loadUserByUsername(username);

                System.out.println(
                        "JWT FILTER: User = "
                                + userDetails.getUsername()
                );

                System.out.println(
                        "JWT FILTER: Authorities = "
                                + userDetails.getAuthorities()
                );

                // Validate token
                if (jwtService.isTokenValid(
                        jwt,
                        userDetails)) {

                    UsernamePasswordAuthenticationToken
                            authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(
                                    authentication
                            );

                    System.out.println(
                            "JWT AUTHENTICATED: "
                                    + authentication.getName()
                                    + " | Authorities = "
                                    + authentication.getAuthorities()
                    );

                } else {

                    System.out.println(
                            "JWT FILTER: Token validation failed"
                    );
                }
            }

        } catch (JwtException e) {

            System.out.println(
                    "JWT FILTER: Invalid/expired JWT - "
                            + e.getMessage()
            );

            SecurityContextHolder
                    .clearContext();

        } catch (Exception e) {

            System.out.println(
                    "JWT FILTER: Authentication error - "
                            + e.getMessage()
            );

            SecurityContextHolder
                    .clearContext();
        }

        filterChain.doFilter(request, response);
    }
}


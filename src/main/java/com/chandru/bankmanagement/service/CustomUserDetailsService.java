package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.entity.User;
import com.chandru.bankmanagement.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        System.out.println(
                "USER DETAILS SERVICE: Loading user = " + username
        );

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + username
                        )
                );

        System.out.println(
                "USER DETAILS SERVICE: Found user = "
                        + user.getUsername()
        );

        System.out.println(
                "USER DETAILS SERVICE: Role = "
                        + user.getRole()
        );

        System.out.println(
                "USER DETAILS SERVICE: Enabled = "
                        + user.getEnabled()
        );

        // Check role
        if (user.getRole() == null || user.getRole().isBlank()) {
            throw new UsernameNotFoundException(
                    "User has no role: " + username
            );
        }

        // CUSTOMER -> ROLE_CUSTOMER
        // ADMIN    -> ROLE_ADMIN
        String role = user.getRole().trim().toUpperCase();

        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        System.out.println(
                "USER DETAILS SERVICE: Authority = " + role
        );

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getEnabled()),
                true,
                true,
                true,
                Collections.singletonList(
                        new SimpleGrantedAuthority(role)
                )
        );
    }
}
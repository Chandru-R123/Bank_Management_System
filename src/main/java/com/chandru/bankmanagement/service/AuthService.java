package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.LoginRequest;
import com.chandru.bankmanagement.dto.LoginResponse;
import com.chandru.bankmanagement.dto.RegisterRequest;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.entity.User;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.UserRepository;
import com.chandru.bankmanagement.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerRepository customerRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            CustomUserDetailsService customUserDetailsService,
            JwtService jwtService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CustomerRepository customerRepository) {

        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerRepository = customerRepository;
    }

    // =========================
    // LOGIN
    // =========================

    public LoginResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetails userDetails =
                customUserDetailsService.loadUserByUsername(
                        request.getUsername()
                );

        String token = jwtService.generateToken(userDetails);

        return new LoginResponse(token);
    }

    // =========================
    // REGISTER
    // =========================

    public String register(RegisterRequest request) {

        // Check username
        if (userRepository.existsByUsername(request.getUsername())) {
            return "Username already exists";
        }

        Customer customer = null;

        // CUSTOMER must be linked to a Customer
        if ("CUSTOMER".equalsIgnoreCase(request.getRole())) {

            if (request.getCustomerId() == null) {
                return "Customer ID is required for CUSTOMER role";
            }

            customer = customerRepository
                    .findById(request.getCustomerId())
                    .orElseThrow(() ->
                            new RuntimeException("Customer not found"));
        }

        // Create User
        User user = new User();

        user.setUsername(request.getUsername());

        // Encrypt password
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        // Store role as ADMIN / CUSTOMER
        user.setRole(
                request.getRole().toUpperCase()
        );

        user.setEnabled(true);

        // Connect user to customer
        user.setCustomer(customer);

        // Save user
        userRepository.save(user);

        return "User Registered Successfully";
    }
}


package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.CustomerRequest;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.dto.ProfileUpdateRequest;
import com.chandru.bankmanagement.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // ── STAFF: create ──────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @PostMapping
    public CustomerResponse createCustomer(
            @Valid @RequestBody CustomerRequest request) {
        return customerService.saveCustomer(request);
    }

    // ── STAFF: list all ────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    // ── CUSTOMER: own profile — must be BEFORE /{id} to avoid ambiguity

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public CustomerResponse getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        // jwt.getSubject() is the Keycloak UUID ("sub" claim)
        return customerService.getMyProfile(jwt.getSubject());
    }

    // ── CUSTOMER: update own contact details ───────────────────────────

    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("/me")
    public CustomerResponse updateMyProfile(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ProfileUpdateRequest request) {
        return customerService.updateMyProfile(jwt.getSubject(), request);
    }

    // ── STAFF: by id ───────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/{id}")
    public CustomerResponse getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    // ── STAFF: update ──────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @PutMapping("/{id}")
    public CustomerResponse updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    // ── ADMIN: delete ──────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public String deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return "Customer deleted successfully";
    }

    // ── STAFF: paginated ───────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/page")
    public Page<CustomerResponse> getCustomers(
            @RequestParam(defaultValue = "0")          int    page,
            @RequestParam(defaultValue = "5")          int    size,
            @RequestParam(defaultValue = "customerId") String sortBy,
            @RequestParam(defaultValue = "asc")        String direction) {
        return customerService.getCustomers(page, size, sortBy, direction);
    }

    // ── STAFF: by email ────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/email/{email}")
    public CustomerResponse getCustomerByEmail(@PathVariable String email) {
        return customerService.getCustomerByEmail(email);
    }
}

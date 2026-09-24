package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.CustomerRequest;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.dto.ProfileUpdateRequest;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @PreAuthorize(Roles.CUSTOMER_MANAGERS)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @Valid @RequestBody CustomerRequest request) {
        return customerService.saveCustomer(request);
    }

    // ── STAFF: list all ────────────────────────────────────────────────

    @PreAuthorize(Roles.STAFF)
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

    @PreAuthorize(Roles.STAFF)
    @GetMapping("/{id}")
    public CustomerResponse getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    // ── STAFF: update ──────────────────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER_MANAGERS)
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

    @PreAuthorize(Roles.STAFF)
    @GetMapping("/page")
    public Page<CustomerResponse> getCustomers(
            @RequestParam(defaultValue = "0")          int    page,
            @RequestParam(defaultValue = "5")          int    size,
            @RequestParam(defaultValue = "customerId") String sortBy,
            @RequestParam(defaultValue = "asc")        String direction) {
        return customerService.getCustomers(page, size, sortBy, direction);
    }

    // ── STAFF: by email ────────────────────────────────────────────────

    @PreAuthorize(Roles.STAFF)
    @GetMapping("/email/{email}")
    public CustomerResponse getCustomerByEmail(@PathVariable String email) {
        return customerService.getCustomerByEmail(email);
    }
}

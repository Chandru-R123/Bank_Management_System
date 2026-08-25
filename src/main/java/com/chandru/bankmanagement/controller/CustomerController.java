package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.CustomerRequest;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public CustomerResponse createCustomer(
            @Valid @RequestBody CustomerRequest request) {
        return customerService.saveCustomer(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public CustomerResponse getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public CustomerResponse updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public String deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return "Customer deleted successfully";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/page")
    public Page<CustomerResponse> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "customerId") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        return customerService.getCustomers(
                page, size, sortBy, direction);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/email/{email}")
    public CustomerResponse getCustomerByEmail(
            @PathVariable String email) {
        return customerService.getCustomerByEmail(email);
    }
}
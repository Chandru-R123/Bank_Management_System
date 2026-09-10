package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.CustomerRequest;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.DuplicateEmailException;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // ── ADMIN: create ──────────────────────────────────────────────────

    public CustomerResponse saveCustomer(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already exists");
        }
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        log.info("Creating customer with email: {}", request.getEmail());

        Customer saved = customerRepository.save(customer);
        log.info("Customer created with id: {}", saved.getCustomerId());
        return toResponse(saved);
    }

    // ── ADMIN: list all ────────────────────────────────────────────────

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── ADMIN: paginated list ──────────────────────────────────────────

    public Page<CustomerResponse> getCustomers(int page, int size,
                                               String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return customerRepository.findAll(pageable).map(this::toResponse);
    }

    // ── ADMIN: by id ───────────────────────────────────────────────────

    public CustomerResponse getCustomerById(Long id) {
        return toResponse(customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found")));
    }

    // ── ADMIN: by email ────────────────────────────────────────────────

    public CustomerResponse getCustomerByEmail(String email) {
        return toResponse(customerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found")));
    }

    // ── ADMIN: update ──────────────────────────────────────────────────

    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        return toResponse(customerRepository.save(customer));
    }

    // ── ADMIN: delete ──────────────────────────────────────────────────

    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }

    // ── CUSTOMER: my own profile (resolved by Keycloak sub) ───────────

    /**
     * Called with jwt.getSubject() — the Keycloak user UUID.
     * Returns the Customer row whose keycloak_sub matches.
     */
    public CustomerResponse getMyProfile(String keycloakSub) {
        return toResponse(
                customerRepository.findByKeycloakSub(keycloakSub)
                        .orElseThrow(() -> new CustomerNotFoundException(
                                "No customer profile linked to this account")));
    }

    // ── mapping ────────────────────────────────────────────────────────

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getCustomerId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress()
        );
    }
}

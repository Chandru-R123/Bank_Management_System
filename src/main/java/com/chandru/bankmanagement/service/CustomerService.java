package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.CustomerRequest;
import com.chandru.bankmanagement.dto.CustomerResponse;
import com.chandru.bankmanagement.dto.ProfileUpdateRequest;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.DuplicateEmailException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CustomerService {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerService.class);

    /** Fields the paginated endpoint may sort by (anything else → 400, not 500). */
    private static final Set<String> SORTABLE = Set.of("customerId", "name", "email");

    private final CustomerRepository customerRepository;
    private final AccountRepository  accountRepository;

    public CustomerService(CustomerRepository customerRepository,
                           AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository  = accountRepository;
    }

    // ── STAFF: create ──────────────────────────────────────────────────

    @Transactional
    public CustomerResponse saveCustomer(CustomerRequest request) {
        String email = normaliseEmail(request.getEmail());
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("A customer with this email already exists");
        }
        Customer customer = new Customer();
        apply(customer, request, email);
        log.info("Creating customer with email: {}", email);

        Customer saved = customerRepository.save(customer);
        log.info("Customer created with id: {}", saved.getCustomerId());
        return ResponseMapper.toResponse(saved);
    }

    // ── STAFF: list all ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll(Sort.by("customerId"))
                .stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── STAFF: paginated list ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(int page, int size,
                                               String sortBy, String direction) {
        if (!SORTABLE.contains(sortBy)) {
            throw new BusinessRuleException("Cannot sort by '" + sortBy
                    + "'. Allowed: " + String.join(", ", SORTABLE));
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, sort);
        return customerRepository.findAll(pageable).map(ResponseMapper::toResponse);
    }

    // ── STAFF: by id / email ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        return ResponseMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByEmail(String email) {
        return ResponseMapper.toResponse(customerRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found")));
    }

    // ── STAFF: update ──────────────────────────────────────────────────

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = find(id);
        String email = normaliseEmail(request.getEmail());
        if (customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(email, id)) {
            throw new DuplicateEmailException("Another customer already uses this email");
        }
        apply(customer, request, email);
        return ResponseMapper.toResponse(customerRepository.save(customer));
    }

    // ── ADMIN: delete ──────────────────────────────────────────────────

    /**
     * Customers with any account (open or closed) are retained for audit —
     * banks must keep financial history. Only customers who never had an
     * account can be removed.
     */
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = find(id);
        if (accountRepository.existsByCustomerCustomerId(id)) {
            throw new BusinessRuleException(
                    "Customer has accounts and cannot be deleted. "
                            + "Close their accounts instead — account history must be retained.");
        }
        customerRepository.delete(customer);
        log.info("Customer id={} deleted", id);
    }

    // ── CUSTOMER: my own profile (resolved by Keycloak sub) ────────────

    @Transactional(readOnly = true)
    public CustomerResponse getMyProfile(String keycloakSub) {
        return ResponseMapper.toResponse(findBySub(keycloakSub));
    }

    @Transactional
    public CustomerResponse updateMyProfile(String keycloakSub, ProfileUpdateRequest request) {
        Customer customer = findBySub(keycloakSub);
        customer.setPhone(request.phone().trim());
        customer.setAddress(request.address().trim());
        return ResponseMapper.toResponse(customerRepository.save(customer));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Customer find(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
    }

    private Customer findBySub(String keycloakSub) {
        return customerRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer profile linked to this account"));
    }

    private static void apply(Customer customer, CustomerRequest request, String email) {
        customer.setName(request.getName().trim());
        customer.setEmail(email);
        customer.setPhone(request.getPhone().trim());
        customer.setAddress(request.getAddress().trim());
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

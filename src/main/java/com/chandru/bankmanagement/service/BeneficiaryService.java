package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.BeneficiaryRequest;
import com.chandru.bankmanagement.dto.BeneficiaryResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.AccountStatus;
import com.chandru.bankmanagement.entity.Beneficiary;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.exception.AccountNotFoundException;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.BeneficiaryRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Saved payees.
 *
 * Rules:
 *  - The payee must be an existing State Bank account that is not closed.
 *  - A customer cannot add their own account (own-account transfers need no payee).
 *  - The same account number can be saved only once per customer.
 *  - Customers manage their own list; staff can view all lists and add on a
 *    customer's behalf; deletion is allowed for the owner, ADMIN or CHECKER.
 */
@Service
public class BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository beneficiaryRepository;
    private final CustomerRepository    customerRepository;
    private final AccountRepository     accountRepository;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                              CustomerRepository customerRepository,
                              AccountRepository accountRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.customerRepository    = customerRepository;
        this.accountRepository     = accountRepository;
    }

    @Transactional
    public BeneficiaryResponse create(BeneficiaryRequest request, Actor actor) {
        Customer owner = resolveOwner(request.customerId(), actor);
        String number = request.accountNumber().trim().toUpperCase(Locale.ROOT);

        Account payee = accountRepository.findByAccountNumberIgnoreCase(number)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No State Bank account found with number " + number));

        if (payee.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account " + number + " is closed and cannot be added");
        }
        if (payee.getCustomer() != null
                && payee.getCustomer().getCustomerId().equals(owner.getCustomerId())) {
            throw new BusinessRuleException(
                    "This is your own account — own-account transfers don't need a beneficiary");
        }
        if (beneficiaryRepository.existsByCustomerCustomerIdAndAccountNumberIgnoreCase(
                owner.getCustomerId(), payee.getAccountNumber())) {
            throw new BusinessRuleException("Account " + number + " is already in the beneficiary list");
        }

        Beneficiary b = new Beneficiary();
        b.setCustomer(owner);
        b.setNickname(request.nickname().trim());
        b.setAccountNumber(payee.getAccountNumber());
        Beneficiary saved = beneficiaryRepository.save(b);
        log.info("Beneficiary {} added for customer id={} by {}",
                payee.getAccountNumber(), owner.getCustomerId(), actor.username());
        return toResponse(saved);
    }

    /** Staff see every list; customers see their own. */
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(Actor actor) {
        List<Beneficiary> list = actor.staff()
                ? beneficiaryRepository.findAllByOrderByBeneficiaryIdDesc()
                : beneficiaryRepository.findByCustomerCustomerIdOrderByNicknameAsc(
                        currentCustomer(actor).getCustomerId());
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id, Actor actor) {
        Beneficiary b = beneficiaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
        boolean owner = b.getCustomer().getKeycloakSub() != null
                && b.getCustomer().getKeycloakSub().equals(actor.sub());
        if (!owner && !actor.checker()) {
            throw new UnauthorizedAccessException(
                    "Only the owner, an ADMIN or a CHECKER can delete this beneficiary");
        }
        beneficiaryRepository.delete(b);
        log.info("Beneficiary id={} deleted by {}", id, actor.username());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Customer resolveOwner(Long customerId, Actor actor) {
        if (actor.staff()) {
            if (customerId == null) {
                throw new BusinessRuleException("customerId is required when staff add a beneficiary");
            }
            return customerRepository.findById(customerId)
                    .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        }
        return currentCustomer(actor);
    }

    private Customer currentCustomer(Actor actor) {
        return customerRepository.findByKeycloakSub(actor.sub())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer profile linked to this login"));
    }

    private BeneficiaryResponse toResponse(Beneficiary b) {
        Account payee = accountRepository.findByAccountNumberIgnoreCase(b.getAccountNumber()).orElse(null);
        String holder = payee != null && payee.getCustomer() != null
                ? AccountService.maskName(payee.getCustomer().getName()) : "Unknown";
        boolean canReceive = payee != null && payee.isActive()
                && !AccountService.FIXED_DEPOSIT.equals(payee.getAccountType());
        return new BeneficiaryResponse(
                b.getBeneficiaryId(),
                b.getNickname(),
                b.getAccountNumber(),
                holder,
                payee != null ? payee.getAccountType() : null,
                canReceive,
                b.getCustomer().getCustomerId(),
                b.getCustomer().getName(),
                b.getCreatedAt());
    }
}

package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.ConsentApprovalRequest;
import com.chandru.bankmanagement.dto.ConsentRequest;
import com.chandru.bankmanagement.dto.ConsentResponse;
import com.chandru.bankmanagement.dto.OpenBankingAccountResponse;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.AccountStatus;
import com.chandru.bankmanagement.entity.Consent;
import com.chandru.bankmanagement.entity.ConsentPermission;
import com.chandru.bankmanagement.entity.ConsentStatus;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.ConsentRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Open Banking account-information consents.
 *
 * Flow:
 *   1. A TPP (fintech app) creates a consent request for a customer
 *      → AWAITING_AUTHORISATION
 *   2. The customer reviews it in online banking, picks the accounts to
 *      share and approves (→ AUTHORISED) or rejects (→ REJECTED)
 *   3. While AUTHORISED and unexpired, the TPP can read the shared
 *      accounts through /api/open-banking/**, limited by the permissions
 *   4. The customer, the TPP, an ADMIN or a CHECKER can revoke (→ REVOKED)
 *   5. Past expiresAt, any non-final consent becomes EXPIRED
 */
@Service
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);
    private static final int DEFAULT_VALIDITY_DAYS = 90;

    private final ConsentRepository     consentRepository;
    private final CustomerRepository    customerRepository;
    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;

    public ConsentService(ConsentRepository consentRepository,
                          CustomerRepository customerRepository,
                          AccountRepository accountRepository,
                          TransactionRepository transactionRepository) {
        this.consentRepository     = consentRepository;
        this.customerRepository    = customerRepository;
        this.accountRepository     = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    // ── TPP (or ADMIN): create request ─────────────────────────────────

    @Transactional
    public ConsentResponse create(ConsentRequest request, Actor actor) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.customerEmail().trim())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer found with email " + request.customerEmail().trim()));

        String sub = customer.getKeycloakSub();
        if (sub == null || sub.isBlank() || sub.endsWith("-placeholder")) {
            throw new BusinessRuleException(
                    "This customer has not enrolled in online banking, so they cannot authorise a consent");
        }

        int days = request.validityDays() == null ? DEFAULT_VALIDITY_DAYS : request.validityDays();
        Set<ConsentPermission> permissions = EnumSet.copyOf(request.permissions());
        // Balances and transactions are meaningless without knowing the accounts
        permissions.add(ConsentPermission.READ_ACCOUNTS);

        Consent c = new Consent();
        c.setConsentId("CNS-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        c.setCustomer(customer);
        c.setTppUsername(actor.username());
        c.setTppName(request.tppName() == null || request.tppName().isBlank()
                ? actor.username() : request.tppName().trim());
        c.setPurpose(request.purpose().trim());
        c.setPermissions(new HashSet<>(permissions));
        c.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
        c.setCreatedAt(LocalDateTime.now());
        c.setExpiresAt(LocalDateTime.now().plusDays(days));
        Consent saved = consentRepository.save(c);

        log.info("Consent {} requested by {} for customer id={}",
                saved.getConsentId(), actor.username(), customer.getCustomerId());
        return toResponse(saved);
    }

    // ── read ───────────────────────────────────────────────────────────

    /** Staff see all consents, a TPP sees the ones it created, a customer sees their own. */
    @Transactional
    public List<ConsentResponse> list(Actor actor) {
        List<Consent> list;
        if (actor.staff()) {
            list = consentRepository.findAllByOrderByCreatedAtDesc();
        } else if (actor.tpp()) {
            list = consentRepository.findByTppUsernameOrderByCreatedAtDesc(actor.username());
        } else {
            list = consentRepository.findByCustomerKeycloakSubOrderByCreatedAtDesc(actor.sub());
        }
        list.forEach(this::expireIfDue);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ConsentResponse get(String id, Actor actor) {
        Consent c = find(id);
        ensureCanView(c, actor);
        expireIfDue(c);
        return toResponse(c);
    }

    // ── CUSTOMER: approve / reject ─────────────────────────────────────

    @Transactional
    public ConsentResponse approve(String id, ConsentApprovalRequest request, Actor actor) {
        Consent c = find(id);
        ensureCustomerOwner(c, actor);
        expireIfDue(c);
        ensureAwaiting(c);

        Set<Account> shared = new HashSet<>();
        for (Long accountId : new HashSet<>(request.accountIds())) {
            Account a = accountRepository.findById(accountId)
                    .orElseThrow(() -> new BusinessRuleException("Account " + accountId + " not found"));
            if (a.getCustomer() == null
                    || !a.getCustomer().getCustomerId().equals(c.getCustomer().getCustomerId())) {
                throw new UnauthorizedAccessException("You can only share your own accounts");
            }
            if (a.getStatus() == AccountStatus.CLOSED) {
                throw new BusinessRuleException("Account " + a.getAccountNumber() + " is closed and cannot be shared");
            }
            shared.add(a);
        }

        c.setAccounts(shared);
        changeStatus(c, ConsentStatus.AUTHORISED, actor);
        log.info("Consent {} authorised by {} for {} account(s)", id, actor.username(), shared.size());
        return toResponse(consentRepository.save(c));
    }

    @Transactional
    public ConsentResponse reject(String id, Actor actor) {
        Consent c = find(id);
        ensureCustomerOwner(c, actor);
        expireIfDue(c);
        ensureAwaiting(c);
        changeStatus(c, ConsentStatus.REJECTED, actor);
        log.info("Consent {} rejected by {}", id, actor.username());
        return toResponse(consentRepository.save(c));
    }

    // ── CUSTOMER / TPP / CHECKER: revoke ───────────────────────────────

    @Transactional
    public ConsentResponse revoke(String id, Actor actor) {
        Consent c = find(id);
        boolean customerOwner = isCustomerOwner(c, actor);
        boolean creatorTpp = actor.tpp() && c.getTppUsername().equals(actor.username());
        if (!customerOwner && !creatorTpp && !actor.checker()) {
            throw new UnauthorizedAccessException("You cannot revoke this consent");
        }
        expireIfDue(c);
        if (c.isFinal()) {
            throw new BusinessRuleException("Consent is already " + c.getStatus());
        }
        changeStatus(c, ConsentStatus.REVOKED, actor);
        log.info("Consent {} revoked by {}", id, actor.username());
        return toResponse(consentRepository.save(c));
    }

    // ── TPP: Open Banking data access ──────────────────────────────────

    @Transactional
    public List<OpenBankingAccountResponse> sharedAccounts(String consentId, Actor actor) {
        Consent c = usableConsent(consentId, actor, ConsentPermission.READ_ACCOUNTS);
        boolean balances = c.getPermissions().contains(ConsentPermission.READ_BALANCES);
        return c.getAccounts().stream()
                .sorted(Comparator.comparing(Account::getAccountId))
                .map(a -> new OpenBankingAccountResponse(
                        a.getAccountId(),
                        a.getAccountNumber(),
                        a.getAccountType(),
                        a.getStatus().name(),
                        a.getCustomer() != null ? a.getCustomer().getName() : null,
                        balances ? ResponseMapper.money(a.getBalance()) : null,
                        "INR"))
                .toList();
    }

    @Transactional
    public List<TransactionResponse> sharedTransactions(String consentId, Long accountId, Actor actor) {
        Consent c = usableConsent(consentId, actor, ConsentPermission.READ_TRANSACTIONS);
        boolean shared = c.getAccounts().stream().anyMatch(a -> a.getAccountId().equals(accountId));
        if (!shared) {
            throw new UnauthorizedAccessException("This account is not covered by the consent");
        }
        return transactionRepository
                .findByAccountAccountIdOrderByTransactionDateDescTransactionIdDesc(accountId)
                .stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── rules / helpers ────────────────────────────────────────────────

    /** A consent the calling TPP may use right now, with the given permission. */
    private Consent usableConsent(String consentId, Actor actor, ConsentPermission needed) {
        if (consentId == null || consentId.isBlank()) {
            throw new BusinessRuleException("Missing consent id (send the x-consent-id header)");
        }
        Consent c = find(consentId.trim());
        if (!actor.admin() && !c.getTppUsername().equals(actor.username())) {
            throw new UnauthorizedAccessException("This consent was issued to a different provider");
        }
        expireIfDue(c);
        if (c.getStatus() != ConsentStatus.AUTHORISED) {
            throw new UnauthorizedAccessException("Consent is " + c.getStatus() + " — access denied");
        }
        if (!c.getPermissions().contains(needed)) {
            throw new UnauthorizedAccessException("Consent does not grant " + needed);
        }
        return c;
    }

    private void expireIfDue(Consent c) {
        if (!c.isFinal() && c.getExpiresAt() != null && c.getExpiresAt().isBefore(LocalDateTime.now())) {
            c.setStatus(ConsentStatus.EXPIRED);
            c.setStatusUpdatedAt(LocalDateTime.now());
            c.setStatusUpdatedBy("system");
            consentRepository.save(c);
        }
    }

    private void ensureAwaiting(Consent c) {
        if (c.getStatus() != ConsentStatus.AWAITING_AUTHORISATION) {
            throw new BusinessRuleException("Consent is " + c.getStatus() + " and can no longer be approved or rejected");
        }
    }

    private boolean isCustomerOwner(Consent c, Actor actor) {
        String sub = c.getCustomer().getKeycloakSub();
        return sub != null && sub.equals(actor.sub());
    }

    private void ensureCustomerOwner(Consent c, Actor actor) {
        if (!isCustomerOwner(c, actor)) {
            throw new UnauthorizedAccessException("Only the customer can approve or reject this consent");
        }
    }

    private void ensureCanView(Consent c, Actor actor) {
        boolean ok = actor.staff()
                || isCustomerOwner(c, actor)
                || (actor.tpp() && c.getTppUsername().equals(actor.username()));
        if (!ok) {
            throw new UnauthorizedAccessException("You cannot view this consent");
        }
    }

    private void changeStatus(Consent c, ConsentStatus status, Actor actor) {
        c.setStatus(status);
        c.setStatusUpdatedAt(LocalDateTime.now());
        c.setStatusUpdatedBy(actor.username());
    }

    private Consent find(String id) {
        return consentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consent " + id + " not found"));
    }

    private ConsentResponse toResponse(Consent c) {
        return new ConsentResponse(
                c.getConsentId(),
                c.getStatus().name(),
                c.getCustomer().getCustomerId(),
                c.getCustomer().getName(),
                c.getTppUsername(),
                c.getTppName(),
                c.getPurpose(),
                c.getPermissions().stream().map(Enum::name).sorted().toList(),
                c.getAccounts().stream()
                        .sorted(Comparator.comparing(Account::getAccountId))
                        .map(a -> new ConsentResponse.SharedAccount(
                                a.getAccountId(), a.getAccountNumber(), a.getAccountType()))
                        .toList(),
                c.getCreatedAt(),
                c.getExpiresAt(),
                c.getStatusUpdatedAt(),
                c.getStatusUpdatedBy());
    }
}

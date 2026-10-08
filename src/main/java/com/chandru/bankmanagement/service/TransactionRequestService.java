package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.CheckerActionDto;
import com.chandru.bankmanagement.dto.CreateTransactionRequestDto;
import com.chandru.bankmanagement.dto.TransactionRequestResponse;
import com.chandru.bankmanagement.entity.*;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.repository.TransactionRequestRepository;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.chandru.bankmanagement.service.ResponseMapper.money;
import static com.chandru.bankmanagement.service.ResponseMapper.rupees;

/**
 * Maker–Checker financial request workflow.
 *
 * Invariants enforced here (backed by DB constraints and locking):
 *  1. Creating a request NEVER changes any account balance.
 *  2. Balance changes happen ONLY during execution (PROCESSING → SUCCESS/FAILED).
 *  3. makerUserId != checkerUserId (backend-enforced; never trust client).
 *  4. Only CHECKER or ADMIN can approve/reject — not EMPLOYEE, CUSTOMER, or TPP.
 *  5. Once in a final state no further transitions are allowed.
 *  6. Pessimistic DB lock on approve → prevents duplicate execution.
 *  7. Optimistic version field prevents concurrent status overwrites.
 *  8. All existing account business rules (limits, frozen/closed, FD, min-balance)
 *     are re-validated at execution time — not just at request-creation time.
 */
@Service
public class TransactionRequestService {

    private static final Logger log = LoggerFactory.getLogger(TransactionRequestService.class);
    private static final DateTimeFormatter REF_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TransactionRequestRepository requestRepository;
    private final AccountRepository            accountRepository;
    private final TransactionRepository        transactionRepository;
    private final AuditService                 auditService;

    public TransactionRequestService(TransactionRequestRepository requestRepository,
                                     AccountRepository accountRepository,
                                     TransactionRepository transactionRepository,
                                     AuditService auditService) {
        this.requestRepository    = requestRepository;
        this.accountRepository    = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService         = auditService;
    }

    // ── MAKER: create request ─────────────────────────────────────────────────

    /**
     * Creates a PENDING_APPROVAL request.  No money moves.
     * Actor must be MAKER (or ADMIN acting in maker capacity).
     */
    @Transactional
    public TransactionRequestResponse create(CreateTransactionRequestDto dto, Actor actor) {
        // Parse and validate request type
        TransactionRequestType reqType;
        try {
            reqType = TransactionRequestType.valueOf(
                    dto.requestType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                    "Invalid requestType. Use DEPOSIT, WITHDRAW, or TRANSFER");
        }

        // Validate amount (BigDecimal, max 2 decimal places)
        BigDecimal amount = validateAmount(dto.amount());

        // Resolve accounts — read-only, no balance change here
        Account fromAccount = findAccount(dto.fromAccountId());
        Account toAccount   = null;

        if (reqType == TransactionRequestType.TRANSFER) {
            toAccount = resolveToAccount(dto);
            if (fromAccount.getAccountId().equals(toAccount.getAccountId())) {
                throw new BusinessRuleException("Cannot transfer to the same account");
            }
        }

        // Light pre-validation: reject obviously invalid requests early
        // Full validation happens again at execution time
        preValidateRequest(reqType, fromAccount, toAccount, amount);

        TransactionRequest req = new TransactionRequest();
        req.setRequestRef(generateRef());
        req.setRequestType(reqType);
        req.setStatus(TransactionRequestStatus.PENDING_APPROVAL);
        req.setFromAccount(fromAccount);
        req.setToAccount(toAccount);
        req.setAmount(amount);
        req.setDescription(dto.description());
        req.setRemarks(dto.remarks());
        req.setMakerUserId(actor.sub());         // always from JWT, never client
        req.setMakerUsername(actor.username());  // always from JWT

        TransactionRequest saved = requestRepository.save(req);
        log.info("TXN-REQ {} created by MAKER {} (type={}, amount={}, from={})",
                saved.getRequestRef(), actor.username(), reqType, amount,
                fromAccount.getAccountNumber());

        auditService.log(AuditActions.TXN_REQUEST_CREATED, actor,
                "TransactionRequest", saved.getRequestRef(), "PENDING_APPROVAL",
                reqType + " of " + rupees(amount) + " on " + fromAccount.getAccountNumber());

        return toResponse(saved);
    }

    // ── CHECKER: approve ──────────────────────────────────────────────────────

    /**
     * CHECKER approves and immediately executes the request in one atomic transaction.
     *
     * The pessimistic write lock on the request row ensures that two concurrent
     * approval attempts cannot both proceed.
     */
    @Transactional
    public TransactionRequestResponse approve(Long requestId, CheckerActionDto dto, Actor actor) {
        // Pessimistic lock — prevents concurrent duplicate approval
        TransactionRequest req = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction request not found: " + requestId));

        enforceCheckerCanAct(req, actor);
        ensurePendingApproval(req);

        // Stamp approval metadata
        req.setCheckerUserId(actor.sub());
        req.setCheckerUsername(actor.username());
        req.setApprovedAt(LocalDateTime.now());
        req.setStatus(TransactionRequestStatus.APPROVED);
        if (dto != null && dto.remarks() != null) {
            req.setRemarks((req.getRemarks() != null ? req.getRemarks() + " | " : "")
                    + "CHECKER: " + dto.remarks());
        }

        log.info("TXN-REQ {} approved by CHECKER {}", req.getRequestRef(), actor.username());
        auditService.log(AuditActions.TXN_REQUEST_APPROVED, actor,
                "TransactionRequest", req.getRequestRef(), "APPROVED",
                "Approved by " + actor.username());

        // Execute immediately after approval
        return toResponse(executeApproved(req, actor));
    }

    // ── CHECKER: reject ───────────────────────────────────────────────────────

    @Transactional
    public TransactionRequestResponse reject(Long requestId, CheckerActionDto dto, Actor actor) {
        TransactionRequest req = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction request not found: " + requestId));

        enforceCheckerCanAct(req, actor);
        ensurePendingApproval(req);

        if (dto == null || dto.rejectionReason() == null || dto.rejectionReason().isBlank()) {
            throw new BusinessRuleException("Rejection reason is required");
        }

        req.setCheckerUserId(actor.sub());
        req.setCheckerUsername(actor.username());
        req.setRejectedAt(LocalDateTime.now());
        req.setStatus(TransactionRequestStatus.REJECTED);
        req.setRejectionReason(dto.rejectionReason().trim());
        if (dto.remarks() != null && !dto.remarks().isBlank()) {
            req.setRemarks(dto.remarks().trim());
        }

        TransactionRequest saved = requestRepository.save(req);
        log.info("TXN-REQ {} rejected by CHECKER {} — reason: {}",
                req.getRequestRef(), actor.username(), req.getRejectionReason());
        auditService.log(AuditActions.TXN_REQUEST_REJECTED, actor,
                "TransactionRequest", req.getRequestRef(), "REJECTED",
                "Reason: " + req.getRejectionReason());

        return toResponse(saved);
    }

    // ── MAKER: cancel own pending request ─────────────────────────────────────

    @Transactional
    public TransactionRequestResponse cancel(Long requestId, Actor actor) {
        TransactionRequest req = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction request not found: " + requestId));

        // Only the original MAKER (or ADMIN) can cancel
        if (!actor.admin() && !req.getMakerUserId().equals(actor.sub())) {
            throw new UnauthorizedAccessException(
                    "Only the original MAKER or an ADMIN can cancel this request");
        }
        if (req.getStatus() != TransactionRequestStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(
                    "Only PENDING_APPROVAL requests can be cancelled (current: " + req.getStatus() + ")");
        }

        req.setStatus(TransactionRequestStatus.CANCELLED);
        TransactionRequest saved = requestRepository.save(req);
        log.info("TXN-REQ {} cancelled by {}", req.getRequestRef(), actor.username());
        auditService.log(AuditActions.TXN_REQUEST_CANCELLED, actor,
                "TransactionRequest", req.getRequestRef(), "CANCELLED", null);

        return toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** MAKER sees their own requests; CHECKER/ADMIN sees all. */
    @Transactional(readOnly = true)
    public List<TransactionRequestResponse> listForActor(Actor actor) {
        List<TransactionRequest> list;
        if (actor.admin() || actor.checker()) {
            list = requestRepository.findAllByOrderByCreatedAtDesc();
        } else {
            // Pure MAKER — own requests only
            list = requestRepository.findByMakerUserIdOrderByCreatedAtDesc(actor.sub());
        }
        return list.stream().map(this::toResponse).toList();
    }

    /** CHECKER sees only PENDING requests. */
    @Transactional(readOnly = true)
    public List<TransactionRequestResponse> listPending(Actor actor) {
        if (!actor.checker()) {
            throw new UnauthorizedAccessException("Only CHECKER or ADMIN can view pending requests");
        }
        return requestRepository
                .findByStatusOrderByCreatedAtAsc(TransactionRequestStatus.PENDING_APPROVAL)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransactionRequestResponse getById(Long requestId, Actor actor) {
        TransactionRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction request not found: " + requestId));
        // MAKER can only see own requests; CHECKER/ADMIN can see all
        if (!actor.admin() && !actor.checker()
                && !req.getMakerUserId().equals(actor.sub())) {
            throw new UnauthorizedAccessException("You cannot view this request");
        }
        return toResponse(req);
    }

    // ── Execution (private — called right after approval) ─────────────────────

    private TransactionRequest executeApproved(TransactionRequest req, Actor checkerActor) {
        req.setStatus(TransactionRequestStatus.PROCESSING);
        requestRepository.save(req);

        try {
            BigDecimal amount = req.getAmount();
            Transaction tx;

            switch (req.getRequestType()) {
                case DEPOSIT -> tx = executeDeposit(req, amount, checkerActor);
                case WITHDRAW -> tx = executeWithdraw(req, amount, checkerActor);
                case TRANSFER -> tx = executeTransfer(req, amount, checkerActor);
                default -> throw new BusinessRuleException(
                        "Unknown request type: " + req.getRequestType());
            }

            req.setStatus(TransactionRequestStatus.SUCCESS);
            req.setTransactionId(tx.getTransactionId());
            req.setExecutedAt(LocalDateTime.now());

            // Capture balance after execution (debit side for deposit/withdraw, from-side for transfer)
            Account fromAcc = accountRepository.findById(req.getFromAccount().getAccountId())
                    .orElseThrow();
            req.setBalanceAfter(money(fromAcc.getBalance()));

            TransactionRequest saved = requestRepository.save(req);
            log.info("TXN-REQ {} executed successfully (txId={})",
                    req.getRequestRef(), tx.getTransactionId());
            auditService.log(AuditActions.TXN_REQUEST_EXECUTED, checkerActor,
                    "TransactionRequest", req.getRequestRef(), "SUCCESS",
                    "TxId=" + tx.getTransactionId() + ", amount=" + rupees(amount));
            return saved;

        } catch (Exception ex) {
            req.setStatus(TransactionRequestStatus.FAILED);
            req.setRejectionReason("Execution failed: " + ex.getMessage());
            TransactionRequest saved = requestRepository.save(req);
            log.error("TXN-REQ {} FAILED during execution: {}",
                    req.getRequestRef(), ex.getMessage());
            auditService.log(AuditActions.TXN_REQUEST_FAILED, checkerActor,
                    "TransactionRequest", req.getRequestRef(), "FAILED", ex.getMessage());
            return saved;
        }
    }

    // ── Low-level executors — mirror AccountService logic with FOR UPDATE locks ──

    private Transaction executeDeposit(TransactionRequest req, BigDecimal amount, Actor actor) {
        Account account = accountRepository.findByIdForUpdate(req.getFromAccount().getAccountId())
                .orElseThrow(() -> new BusinessRuleException("Account not found"));

        ensureCanReceive(account);

        account.setBalance(money(account.getBalance()).add(amount));
        accountRepository.save(account);

        return recordTx(account, TransactionTypes.DEPOSIT, amount,
                orDefault(req.getDescription(), "Staff deposit (Maker–Checker)"),
                null, null, actor);
    }

    private Transaction executeWithdraw(TransactionRequest req, BigDecimal amount, Actor actor) {
        Account account = accountRepository.findByIdForUpdate(req.getFromAccount().getAccountId())
                .orElseThrow(() -> new BusinessRuleException("Account not found"));

        ensureCanDebit(account, amount);

        account.setBalance(money(account.getBalance()).subtract(amount));
        accountRepository.save(account);

        return recordTx(account, TransactionTypes.WITHDRAW, amount,
                orDefault(req.getDescription(), "Staff withdrawal (Maker–Checker)"),
                null, null, actor);
    }

    private Transaction executeTransfer(TransactionRequest req, BigDecimal amount, Actor actor) {
        Long fromId = req.getFromAccount().getAccountId();
        Long toId   = req.getToAccount().getAccountId();

        // Lock in deterministic order (ascending id) to prevent deadlocks
        Account first  = accountRepository.findByIdForUpdate(Math.min(fromId, toId))
                .orElseThrow(() -> new BusinessRuleException("Source account not found"));
        Account second = accountRepository.findByIdForUpdate(Math.max(fromId, toId))
                .orElseThrow(() -> new BusinessRuleException("Destination account not found"));

        Account from = first.getAccountId().equals(fromId) ? first : second;
        Account to   = from == first ? second : first;

        ensureCanDebit(from, amount);
        ensureCanReceive(to);

        from.setBalance(money(from.getBalance()).subtract(amount));
        to.setBalance(money(to.getBalance()).add(amount));
        accountRepository.save(from);
        accountRepository.save(to);

        String reference = "TRF" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String desc = req.getDescription();

        Transaction debit = recordTx(from, TransactionTypes.TRANSFER_OUT, amount,
                orDefault(desc, "Transfer to " + to.getAccountNumber()),
                to.getAccountNumber(), reference, actor);
        recordTx(to, TransactionTypes.TRANSFER_IN, amount,
                orDefault(desc, "Transfer from " + from.getAccountNumber()),
                from.getAccountNumber(), reference, actor);

        log.info("TXN-REQ {} transfer {} → {} ref={}",
                req.getRequestRef(), from.getAccountNumber(), to.getAccountNumber(), reference);
        return debit;
    }

    // ── Business rule helpers ──────────────────────────────────────────────────

    private void preValidateRequest(TransactionRequestType type, Account from,
                                    Account to, BigDecimal amount) {
        // Refuse obviously dead requests at creation time
        if (from.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account " + from.getAccountNumber() + " is closed");
        }
        if (type == TransactionRequestType.TRANSFER && to != null
                && to.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Destination account " + to.getAccountNumber() + " is closed");
        }
        if (type == TransactionRequestType.TRANSFER
                && AccountService.FIXED_DEPOSIT.equals(from.getAccountType())) {
            throw new BusinessRuleException(
                    "Withdrawals are not permitted from a Fixed Deposit account");
        }
        if (type == TransactionRequestType.DEPOSIT
                && AccountService.FIXED_DEPOSIT.equals(from.getAccountType())) {
            throw new BusinessRuleException(
                    "Top-ups are not accepted by Fixed Deposit accounts");
        }
    }

    private void ensureCanReceive(Account account) {
        switch (account.getStatus()) {
            case FROZEN -> throw new BusinessRuleException(
                    "Account " + account.getAccountNumber() + " is frozen");
            case CLOSED -> throw new BusinessRuleException(
                    "Account " + account.getAccountNumber() + " is closed");
            default -> {}
        }
        if (AccountService.FIXED_DEPOSIT.equals(account.getAccountType())) {
            throw new BusinessRuleException(
                    "Account " + account.getAccountNumber() + " is a Fixed Deposit and does not accept deposits");
        }
    }

    private void ensureCanDebit(Account account, BigDecimal amount) {
        switch (account.getStatus()) {
            case FROZEN -> throw new BusinessRuleException(
                    "Account " + account.getAccountNumber() + " is frozen");
            case CLOSED -> throw new BusinessRuleException(
                    "Account " + account.getAccountNumber() + " is closed");
            default -> {}
        }
        if (AccountService.FIXED_DEPOSIT.equals(account.getAccountType())) {
            throw new BusinessRuleException(
                    "Withdrawals are not permitted from Fixed Deposit account " + account.getAccountNumber());
        }

        BigDecimal balance  = money(account.getBalance());
        BigDecimal minimum  = AccountService.SAVINGS.equals(account.getAccountType())
                ? new BigDecimal("1000.00") : BigDecimal.ZERO;
        BigDecimal available = balance.subtract(minimum).max(BigDecimal.ZERO);

        if (amount.compareTo(available) > 0) {
            throw new BusinessRuleException(
                    "Insufficient balance. Available: " + rupees(available)
                            + " (account " + account.getAccountNumber() + ")");
        }
    }

    private void enforceCheckerCanAct(TransactionRequest req, Actor actor) {
        // Must be CHECKER or ADMIN — not EMPLOYEE, CUSTOMER, TPP
        if (!actor.checker()) {
            throw new UnauthorizedAccessException(
                    "Only a CHECKER or ADMIN can approve or reject transaction requests");
        }
        // Cannot self-approve: checker must differ from maker — enforced by sub, not username
        if (req.getMakerUserId().equals(actor.sub())) {
            throw new BusinessRuleException(
                    "A MAKER cannot approve or reject their own request (self-approval prohibited)");
        }
    }

    private void ensurePendingApproval(TransactionRequest req) {
        if (req.getStatus() != TransactionRequestStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(
                    "Request is " + req.getStatus() + " — only PENDING_APPROVAL requests can be acted upon");
        }
    }

    // ── Amount validation ─────────────────────────────────────────────────────

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Amount can have at most 2 decimal places");
        }
        // Staff Maker–Checker requests can exceed the customer per-transaction limit
        // but we cap at 10 crore to prevent data-entry errors
        BigDecimal maxStaff = new BigDecimal("100000000"); // 10 crore
        if (amount.compareTo(maxStaff) > 0) {
            throw new BusinessRuleException(
                    "Amount exceeds maximum allowed per request (" + rupees(maxStaff) + ")");
        }
        return money(amount);
    }

    // ── Account lookup helpers ────────────────────────────────────────────────

    private Account findAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Account not found: " + id));
    }

    private Account resolveToAccount(CreateTransactionRequestDto dto) {
        if (dto.toAccountId() != null) {
            return accountRepository.findById(dto.toAccountId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Destination account not found: " + dto.toAccountId()));
        }
        if (dto.toAccountNumber() != null && !dto.toAccountNumber().isBlank()) {
            return accountRepository.findByAccountNumberIgnoreCase(dto.toAccountNumber().trim())
                    .orElseThrow(() -> new BusinessRuleException(
                            "No account found with number " + dto.toAccountNumber().trim()));
        }
        throw new BusinessRuleException("TRANSFER requires toAccountId or toAccountNumber");
    }

    // ── Transaction record helper ─────────────────────────────────────────────

    private Transaction recordTx(Account account, String type, BigDecimal amount,
                                  String description, String counterparty,
                                  String reference, Actor actor) {
        Transaction tx = new Transaction();
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setTransactionDate(LocalDateTime.now());
        tx.setAccount(account);
        tx.setBalanceAfter(money(account.getBalance()));
        tx.setDescription(description);
        tx.setCounterpartyAccountNumber(counterparty);
        tx.setReferenceId(reference);
        tx.setPerformedBy(actor != null ? actor.username() : "system");
        return transactionRepository.save(tx);
    }

    // ── Reference generator ───────────────────────────────────────────────────

    private String generateRef() {
        String date = LocalDateTime.now().format(REF_DATE_FMT);
        for (int i = 0; i < 20; i++) {
            long seq = ThreadLocalRandom.current().nextLong(100000L, 999999L);
            String ref = "TXR-" + date + "-" + seq;
            if (!requestRepository.existsByRequestRef(ref)) return ref;
        }
        // Fallback with UUID to guarantee uniqueness
        return "TXR-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT);
    }

    // ── Response mapper ───────────────────────────────────────────────────────

    private TransactionRequestResponse toResponse(TransactionRequest r) {
        Account from = r.getFromAccount();
        Account to   = r.getToAccount();
        return new TransactionRequestResponse(
                r.getId(),
                r.getRequestRef(),
                r.getRequestType().name(),
                r.getStatus().name(),
                from.getAccountId(),
                from.getAccountNumber(),
                from.getAccountType(),
                to != null ? to.getAccountId()   : null,
                to != null ? to.getAccountNumber() : null,
                r.getAmount(),
                r.getDescription(),
                r.getMakerUserId(),
                r.getMakerUsername(),
                r.getCheckerUserId(),
                r.getCheckerUsername(),
                r.getCreatedAt(),
                r.getApprovedAt(),
                r.getRejectedAt(),
                r.getExecutedAt(),
                r.getRejectionReason(),
                r.getRemarks(),
                r.getTransactionId(),
                r.getBalanceAfter()
        );
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

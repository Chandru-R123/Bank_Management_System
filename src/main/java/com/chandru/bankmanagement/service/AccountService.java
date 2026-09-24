package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.AccountLookupResponse;
import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.dto.TransferRequest;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.AccountStatus;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.entity.TransactionTypes;
import com.chandru.bankmanagement.exception.AccountNotFoundException;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.chandru.bankmanagement.service.ResponseMapper.money;
import static com.chandru.bankmanagement.service.ResponseMapper.rupees;

/**
 * Accounts and money movement.
 *
 * Business rules enforced here:
 *  - Money is BigDecimal with at most 2 decimal places.
 *  - Every balance change locks the account row (SELECT ... FOR UPDATE) and
 *    writes a Transaction with the resulting balance — no silent edits.
 *  - Only ACTIVE accounts can send or receive money.
 *  - SAVINGS accounts must keep a minimum balance.
 *  - FIXED_DEPOSIT accounts accept no top-ups and no withdrawals;
 *    funds are released only by closing the deposit.
 *  - Customers are limited per transaction and per day on debits.
 *  - Customers may only debit accounts they own; staff (ADMIN/EMPLOYEE) may
 *    operate on any account. Freezing and closing is ADMIN only (controller).
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public static final String SAVINGS       = "SAVINGS";
    public static final String CURRENT       = "CURRENT";
    public static final String FIXED_DEPOSIT = "FIXED_DEPOSIT";

    private final AccountRepository     accountRepository;
    private final CustomerRepository    customerRepository;
    private final TransactionRepository transactionRepository;

    @Value("${bank.savings.minimum-balance:1000}")
    private BigDecimal savingsMinimumBalance;

    @Value("${bank.customer.max-transaction-amount:1000000}")
    private BigDecimal maxTransactionAmount;

    @Value("${bank.customer.daily-debit-limit:200000}")
    private BigDecimal dailyDebitLimit;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository     = accountRepository;
        this.customerRepository    = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    // ── STAFF: open account ────────────────────────────────────────────

    @Transactional
    public AccountResponse createAccount(AccountRequest request, Actor actor) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        String type = request.getAccountType();
        BigDecimal opening = request.getBalance() == null
                ? BigDecimal.ZERO : request.getBalance();
        validateScale(opening);
        opening = money(opening);

        if (FIXED_DEPOSIT.equals(type) && opening.signum() <= 0) {
            throw new BusinessRuleException(
                    "A Fixed Deposit must be opened with an amount greater than zero");
        }
        if (SAVINGS.equals(type) && opening.compareTo(savingsMinimumBalance) < 0) {
            throw new BusinessRuleException(
                    "A Savings account requires a minimum opening balance of "
                            + rupees(savingsMinimumBalance));
        }

        String number = request.getAccountNumber() == null
                ? "" : request.getAccountNumber().trim().toUpperCase(Locale.ROOT);
        if (number.isEmpty()) {
            number = generateAccountNumber(type);
        } else if (accountRepository.existsByAccountNumber(number)) {
            throw new BusinessRuleException("Account number " + number + " already exists");
        }

        Account account = new Account();
        account.setAccountNumber(number);
        account.setAccountType(type);
        account.setBalance(opening);
        account.setCustomer(customer);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        if (opening.signum() > 0) {
            record(saved, TransactionTypes.OPENING_DEPOSIT, opening,
                    "Account opening deposit", null, null, actor);
        }
        log.info("Account {} ({}) opened for customer id={} by {}",
                number, type, customer.getCustomerId(), actor.username());
        return ResponseMapper.toResponse(saved);
    }

    // ── STAFF: read ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        return ResponseMapper.toResponse(findAccount(id));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAllByOrderByAccountIdAsc().stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── CUSTOMER: my accounts (resolved by Keycloak sub) ───────────────

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForSub(String keycloakSub) {
        return accountRepository.findByCustomerKeycloakSubOrderByAccountIdAsc(keycloakSub)
                .stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── ANY ROLE: beneficiary lookup ───────────────────────────────────

    @Transactional(readOnly = true)
    public AccountLookupResponse lookup(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BusinessRuleException("Account number is required");
        }
        Account a = accountRepository.findByAccountNumberIgnoreCase(accountNumber.trim())
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found with number " + accountNumber.trim()));
        return new AccountLookupResponse(
                a.getAccountNumber(),
                maskName(a.getCustomer() != null ? a.getCustomer().getName() : null),
                a.getAccountType(),
                a.isActive() && !FIXED_DEPOSIT.equals(a.getAccountType()));
    }

    // ── ADMIN: update (type / owner only — never the balance) ──────────

    @Transactional
    public AccountResponse updateAccount(Long id, AccountRequest request) {
        Account account = findAccountForUpdate(id);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("A closed account cannot be modified");
        }
        String requestedNumber = request.getAccountNumber();
        if (requestedNumber != null && !requestedNumber.isBlank()
                && !requestedNumber.trim().equalsIgnoreCase(account.getAccountNumber())) {
            throw new BusinessRuleException("Account number cannot be changed once issued");
        }

        String newType = request.getAccountType();
        if (!newType.equals(account.getAccountType())) {
            if (FIXED_DEPOSIT.equals(newType) || FIXED_DEPOSIT.equals(account.getAccountType())) {
                throw new BusinessRuleException(
                        "Fixed Deposit accounts cannot be converted to or from other account types");
            }
            if (SAVINGS.equals(newType)
                    && money(account.getBalance()).compareTo(savingsMinimumBalance) < 0) {
                throw new BusinessRuleException(
                        "Balance is below the Savings minimum of " + rupees(savingsMinimumBalance));
            }
            account.setAccountType(newType);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
            account.setCustomer(customer);
        }
        return ResponseMapper.toResponse(accountRepository.save(account));
    }

    // ── ADMIN: freeze / unfreeze / close ───────────────────────────────

    @Transactional
    public AccountResponse freeze(Long id, Actor actor) {
        Account account = findAccountForUpdate(id);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Only an active account can be frozen (current status: "
                            + account.getStatus() + ")");
        }
        account.setStatus(AccountStatus.FROZEN);
        log.info("Account {} frozen by {}", account.getAccountNumber(), actor.username());
        return ResponseMapper.toResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse unfreeze(Long id, Actor actor) {
        Account account = findAccountForUpdate(id);
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new BusinessRuleException("Account is not frozen");
        }
        account.setStatus(AccountStatus.ACTIVE);
        log.info("Account {} unfrozen by {}", account.getAccountNumber(), actor.username());
        return ResponseMapper.toResponse(accountRepository.save(account));
    }

    /**
     * Closes the account. Any remaining balance is paid out to the customer
     * and recorded as a CLOSURE_PAYOUT so the ledger balances to zero.
     * Financial history is retained — accounts are never hard-deleted.
     */
    @Transactional
    public AccountResponse closeAccount(Long id, Actor actor) {
        Account account = findAccountForUpdate(id);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account is already closed");
        }
        BigDecimal balance = money(account.getBalance());
        if (balance.signum() < 0) {
            throw new BusinessRuleException("Account has a negative balance and cannot be closed");
        }
        account.setBalance(BigDecimal.ZERO.setScale(2));
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        if (balance.signum() > 0) {
            record(saved, TransactionTypes.CLOSURE_PAYOUT, balance,
                    "Account closed — balance paid out", null, null, actor);
        }
        log.info("Account {} closed by {} (payout {})",
                account.getAccountNumber(), actor.username(), balance);
        return ResponseMapper.toResponse(saved);
    }

    // ── deposit ────────────────────────────────────────────────────────

    @Transactional
    public AccountResponse deposit(Long id, BigDecimal amount, String description, Actor actor) {
        amount = validateAmount(amount, actor);
        Account account = findAccountForUpdate(id);
        ensureOwner(account, actor);
        ensureCanReceive(account, "This account");

        account.setBalance(money(account.getBalance()).add(amount));
        Account updated = accountRepository.save(account);
        record(updated, TransactionTypes.DEPOSIT, amount,
                orDefault(description, "Cash deposit"), null, null, actor);
        return ResponseMapper.toResponse(updated);
    }

    // ── withdraw ───────────────────────────────────────────────────────

    @Transactional
    public AccountResponse withdraw(Long id, BigDecimal amount, String description, Actor actor) {
        amount = validateAmount(amount, actor);
        Account account = findAccountForUpdate(id);
        ensureOwner(account, actor);
        ensureCanDebit(account, amount, actor);

        account.setBalance(money(account.getBalance()).subtract(amount));
        Account updated = accountRepository.save(account);
        record(updated, TransactionTypes.WITHDRAW, amount,
                orDefault(description, "Cash withdrawal"), null, null, actor);
        return ResponseMapper.toResponse(updated);
    }

    // ── transfer ───────────────────────────────────────────────────────

    /**
     * @return the debit leg (TRANSFER_OUT) of the transfer, as a receipt
     */
    @Transactional
    public TransactionResponse transferMoney(TransferRequest request, Actor actor) {
        BigDecimal amount = validateAmount(request.getAmount(), actor);
        Long fromId = request.getFromAccountId();
        Long toId   = resolveDestinationId(request);

        if (fromId.equals(toId)) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }

        // Lock both rows in a consistent (ascending id) order so two opposite
        // transfers running at the same time cannot deadlock each other.
        Account first  = findAccountForUpdate(Math.min(fromId, toId));
        Account second = findAccountForUpdate(Math.max(fromId, toId));
        Account from = first.getAccountId().equals(fromId) ? first : second;
        Account to   = from == first ? second : first;

        ensureOwner(from, actor);
        ensureCanDebit(from, amount, actor);
        ensureCanReceive(to, "Destination account " + to.getAccountNumber());

        from.setBalance(money(from.getBalance()).subtract(amount));
        to.setBalance(money(to.getBalance()).add(amount));
        accountRepository.save(from);
        accountRepository.save(to);

        String reference = "TRF" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String remark = request.getDescription();

        Transaction debit = record(from, TransactionTypes.TRANSFER_OUT, amount,
                orDefault(remark, "Transfer to " + to.getAccountNumber()),
                to.getAccountNumber(), reference, actor);
        record(to, TransactionTypes.TRANSFER_IN, amount,
                orDefault(remark, "Transfer from " + from.getAccountNumber()),
                from.getAccountNumber(), reference, actor);

        log.info("Transfer {} of {} from {} to {} by {}", reference, amount,
                from.getAccountNumber(), to.getAccountNumber(), actor.username());
        return ResponseMapper.toResponse(debit);
    }

    // ── rules ──────────────────────────────────────────────────────────

    private BigDecimal validateAmount(BigDecimal amount, Actor actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero");
        }
        validateScale(amount);
        if (actor.requiresOwnership() && amount.compareTo(maxTransactionAmount) > 0) {
            throw new BusinessRuleException(
                    "Amount exceeds the per-transaction limit of " + rupees(maxTransactionAmount)
                            + ". Please visit your branch.");
        }
        return money(amount);
    }

    private void validateScale(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Amount can have at most 2 decimal places");
        }
    }

    /** Customers may only operate on their own accounts. */
    private void ensureOwner(Account account, Actor actor) {
        if (!actor.requiresOwnership()) return;
        Customer owner = account.getCustomer();
        if (owner == null || owner.getKeycloakSub() == null
                || !owner.getKeycloakSub().equals(actor.sub())) {
            throw new UnauthorizedAccessException("You are not authorised to access this account");
        }
    }

    private void ensureActive(Account account, String label) {
        switch (account.getStatus()) {
            case FROZEN -> throw new BusinessRuleException(
                    label + " is frozen. Please contact the bank.");
            case CLOSED -> throw new BusinessRuleException(label + " is closed.");
            default -> { }
        }
    }

    private void ensureCanReceive(Account account, String label) {
        ensureActive(account, label);
        if (FIXED_DEPOSIT.equals(account.getAccountType())) {
            throw new BusinessRuleException(
                    label + " is a Fixed Deposit and does not accept additional deposits");
        }
    }

    private void ensureCanDebit(Account account, BigDecimal amount, Actor actor) {
        ensureActive(account, "This account");
        if (FIXED_DEPOSIT.equals(account.getAccountType())) {
            throw new BusinessRuleException(
                    "Withdrawals are not permitted from a Fixed Deposit. "
                            + "Funds are released when the deposit is closed.");
        }

        BigDecimal balance = money(account.getBalance());
        BigDecimal minimum = SAVINGS.equals(account.getAccountType())
                ? savingsMinimumBalance : BigDecimal.ZERO;
        BigDecimal available = balance.subtract(minimum).max(BigDecimal.ZERO);

        if (amount.compareTo(available) > 0) {
            if (minimum.signum() > 0) {
                throw new BusinessRuleException(
                        "Insufficient balance. Savings accounts must keep a minimum of "
                                + rupees(minimum) + " — you can use up to " + rupees(available) + ".");
            }
            throw new BusinessRuleException(
                    "Insufficient balance. Available: " + rupees(available) + ".");
        }

        if (actor.requiresOwnership()) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            BigDecimal used = transactionRepository.sumAmountSince(
                    account.getAccountId(), TransactionTypes.CUSTOMER_DEBITS, startOfDay);
            used = used == null ? BigDecimal.ZERO : used;
            if (used.add(amount).compareTo(dailyDebitLimit) > 0) {
                BigDecimal remaining = dailyDebitLimit.subtract(used).max(BigDecimal.ZERO);
                throw new BusinessRuleException(
                        "Daily withdrawal/transfer limit of " + rupees(dailyDebitLimit)
                                + " reached. Remaining today: " + rupees(remaining) + ".");
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Long resolveDestinationId(TransferRequest request) {
        if (request.getToAccountId() != null) {
            return request.getToAccountId();
        }
        String number = request.getToAccountNumber();
        if (number == null || number.isBlank()) {
            throw new BusinessRuleException("Destination account is required");
        }
        return accountRepository.findByAccountNumberIgnoreCase(number.trim())
                .map(Account::getAccountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found with number " + number.trim()));
    }

    private Account findAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    private Account findAccountForUpdate(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    private Transaction record(Account account, String type, BigDecimal amount,
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

    private String generateAccountNumber(String type) {
        String prefix = switch (type) {
            case SAVINGS       -> "SB";
            case CURRENT       -> "CA";
            case FIXED_DEPOSIT -> "FD";
            default            -> "AC";
        };
        for (int attempt = 0; attempt < 20; attempt++) {
            long digits = ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L);
            String candidate = prefix + "-" + digits;
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** "Priya Venkat" → "Priya V." — enough to confirm a beneficiary, not a full disclosure. */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return "Account holder";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            String p = parts[0];
            return p.length() <= 2 ? p : p.substring(0, 2) + "*".repeat(Math.min(p.length() - 2, 5));
        }
        return parts[0] + " " + parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }
}

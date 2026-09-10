package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.exception.AccountNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * All methods that are CUSTOMER-facing receive the Keycloak JWT subject ("sub")
 * as the identity — never a username or DB user id.
 *
 * ADMIN methods do not perform ownership checks.
 */
@Service
public class AccountService {

    private final AccountRepository    accountRepository;
    private final CustomerRepository   customerRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository     = accountRepository;
        this.customerRepository    = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    // ── ADMIN: create ──────────────────────────────────────────────────

    public AccountResponse createAccount(AccountRequest request) {
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new RuntimeException("Account number already exists");
        }
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Account account = new Account();
        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance());
        account.setCustomer(customer);

        return convertToResponse(accountRepository.save(account));
    }

    // ── ADMIN: read one ────────────────────────────────────────────────

    public AccountResponse getAccountById(Long id) {
        return convertToResponse(findAccount(id));
    }

    // ── ADMIN: update ──────────────────────────────────────────────────

    public AccountResponse updateAccount(Long id, AccountRequest request) {
        Account account = findAccount(id);
        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance());

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            account.setCustomer(customer);
        }
        return convertToResponse(accountRepository.save(account));
    }

    // ── ADMIN: delete ──────────────────────────────────────────────────

    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException("Account not found");
        }
        accountRepository.deleteById(id);
    }

    // ── ADMIN: list all ────────────────────────────────────────────────

    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // ── CUSTOMER: my accounts (resolved by Keycloak sub) ──────────────

    public List<AccountResponse> getAccountsForSub(String keycloakSub) {
        return accountRepository.findByCustomerKeycloakSub(keycloakSub)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // ── CUSTOMER + ADMIN: deposit ──────────────────────────────────────

    /**
     * @param keycloakSub  null → ADMIN (no ownership check)
     *                     non-null → CUSTOMER (must own the account)
     */
    public AccountResponse deposit(Long id, Double amount, String keycloakSub) {
        Account account = findAccount(id);
        validateAmount(amount);
        if (keycloakSub != null) validateOwnership(account, keycloakSub);

        account.setBalance(account.getBalance() + amount);
        Account updated = accountRepository.save(account);
        saveTransaction(updated, "DEPOSIT", amount);
        return convertToResponse(updated);
    }

    // ── CUSTOMER + ADMIN: withdraw ─────────────────────────────────────

    public AccountResponse withdraw(Long id, Double amount, String keycloakSub) {
        Account account = findAccount(id);
        validateAmount(amount);
        if (keycloakSub != null) validateOwnership(account, keycloakSub);

        if (account.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }
        account.setBalance(account.getBalance() - amount);
        Account updated = accountRepository.save(account);
        saveTransaction(updated, "WITHDRAW", amount);
        return convertToResponse(updated);
    }

    // ── CUSTOMER + ADMIN: transfer ─────────────────────────────────────

    @Transactional
    public void transferMoney(Long fromId, Long toId,
                              Double amount, String keycloakSub) {
        if (fromId.equals(toId)) {
            throw new RuntimeException("Cannot transfer to the same account");
        }
        Account from = findAccount(fromId);
        Account to   = findAccount(toId);
        validateAmount(amount);

        // Customer may only transfer FROM their own account
        if (keycloakSub != null) validateOwnership(from, keycloakSub);

        if (from.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        accountRepository.save(from);
        accountRepository.save(to);

        saveTransaction(from, "TRANSFER_OUT", amount);
        saveTransaction(to,   "TRANSFER_IN",  amount);
    }

    // ── private helpers ────────────────────────────────────────────────

    private Account findAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    private void validateAmount(Double amount) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }
    }

    /**
     * Verify that the account belongs to the customer identified by
     * the Keycloak sub claim.  Throws 403 if not.
     */
    private void validateOwnership(Account account, String keycloakSub) {
        accountRepository
                .findByAccountIdAndCustomerKeycloakSub(
                        account.getAccountId(), keycloakSub)
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "You are not authorised to access this account"));
    }

    private void saveTransaction(Account account, String type, Double amount) {
        Transaction tx = new Transaction();
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setTransactionDate(LocalDateTime.now());
        tx.setAccount(account);
        transactionRepository.save(tx);
    }

    private AccountResponse convertToResponse(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCustomer().getName()
        );
    }
}

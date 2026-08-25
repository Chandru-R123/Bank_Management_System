package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.entity.User;
import com.chandru.bankmanagement.exception.AccountNotFoundException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository) {

        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // =====================================================
    // CREATE ACCOUNT - ADMIN
    // =====================================================

    public AccountResponse createAccount(AccountRequest request) {

        if (accountRepository.existsByAccountNumber(
                request.getAccountNumber())) {

            throw new RuntimeException(
                    "Account number already exists");
        }

        Customer customer = customerRepository
                .findById(request.getCustomerId())
                .orElseThrow(() ->
                        new RuntimeException("Customer not found"));

        Account account = new Account();

        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance());
        account.setCustomer(customer);

        Account savedAccount =
                accountRepository.save(account);

        return convertToResponse(savedAccount);
    }

    // =====================================================
    // GET ACCOUNT BY ID
    // ADMIN
    // =====================================================

    public AccountResponse getAccountById(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found"));

        return convertToResponse(account);
    }

    // =====================================================
    // UPDATE ACCOUNT - ADMIN
    // =====================================================

    public AccountResponse updateAccount(
            Long id,
            AccountRequest request) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found"));

        account.setAccountNumber(
                request.getAccountNumber());

        account.setAccountType(
                request.getAccountType());

        account.setBalance(
                request.getBalance());

        Account updated =
                accountRepository.save(account);

        return convertToResponse(updated);
    }

    // =====================================================
    // DELETE ACCOUNT - ADMIN
    // =====================================================

    public void deleteAccount(Long id) {

        if (!accountRepository.existsById(id)) {

            throw new AccountNotFoundException(
                    "Account not found");
        }

        accountRepository.deleteById(id);
    }

    // =====================================================
    // GET ALL ACCOUNTS - ADMIN
    // =====================================================

    public List<AccountResponse> getAllAccounts() {

        return accountRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // DEPOSIT - ADMIN
    // =====================================================

    public AccountResponse deposit(
            Long id,
            Double amount) {

        return deposit(id, amount, null);
    }

    // =====================================================
    // DEPOSIT - USER AWARE
    // =====================================================

    public AccountResponse deposit(
            Long id,
            Double amount,
            String username) {

        Account account = getAccount(id);

        validateAmount(amount);

        // If username is provided,
        // verify ownership.
        if (username != null) {
            validateOwnership(account, username);
        }

        account.setBalance(
                account.getBalance() + amount);

        Account updated =
                accountRepository.save(account);

        Transaction transaction =
                new Transaction();

        transaction.setTransactionType("DEPOSIT");
        transaction.setAmount(amount);
        transaction.setTransactionDate(
                LocalDateTime.now());
        transaction.setAccount(updated);

        transactionRepository.save(transaction);

        return convertToResponse(updated);
    }

    // =====================================================
    // WITHDRAW - ADMIN
    // =====================================================

    public AccountResponse withdraw(
            Long id,
            Double amount) {

        return withdraw(id, amount, null);
    }

    // =====================================================
    // WITHDRAW - USER AWARE
    // =====================================================

    public AccountResponse withdraw(
            Long id,
            Double amount,
            String username) {

        Account account = getAccount(id);

        validateAmount(amount);

        if (username != null) {
            validateOwnership(account, username);
        }

        if (account.getBalance() < amount) {

            throw new RuntimeException(
                    "Insufficient balance");
        }

        account.setBalance(
                account.getBalance() - amount);

        Account updated =
                accountRepository.save(account);

        Transaction transaction =
                new Transaction();

        transaction.setTransactionType("WITHDRAW");
        transaction.setAmount(amount);
        transaction.setTransactionDate(
                LocalDateTime.now());
        transaction.setAccount(updated);

        transactionRepository.save(transaction);

        return convertToResponse(updated);
    }

    // =====================================================
    // TRANSFER - ADMIN
    // =====================================================

    @Transactional
    public void transferMoney(
            Long fromId,
            Long toId,
            Double amount) {

        transferMoney(
                fromId,
                toId,
                amount,
                null);
    }

    // =====================================================
    // TRANSFER - USER AWARE
    // =====================================================

    @Transactional
    public void transferMoney(
            Long fromId,
            Long toId,
            Double amount,
            String username) {

        if (fromId.equals(toId)) {

            throw new RuntimeException(
                    "Cannot transfer to the same account");
        }

        Account fromAccount = getAccount(fromId);

        Account toAccount = getAccount(toId);

        validateAmount(amount);

        // Customer can only transfer
        // FROM their own account.
        if (username != null) {

            validateOwnership(
                    fromAccount,
                    username);
        }

        if (fromAccount.getBalance() < amount) {

            throw new RuntimeException(
                    "Insufficient balance");
        }

        fromAccount.setBalance(
                fromAccount.getBalance() - amount);

        toAccount.setBalance(
                toAccount.getBalance() + amount);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Sender transaction
        Transaction sender =
                new Transaction();

        sender.setTransactionType(
                "TRANSFER_OUT");

        sender.setAmount(amount);

        sender.setTransactionDate(
                LocalDateTime.now());

        sender.setAccount(fromAccount);

        transactionRepository.save(sender);

        // Receiver transaction
        Transaction receiver =
                new Transaction();

        receiver.setTransactionType(
                "TRANSFER_IN");

        receiver.setAmount(amount);

        receiver.setTransactionDate(
                LocalDateTime.now());

        receiver.setAccount(toAccount);

        transactionRepository.save(receiver);
    }

    // =====================================================
    // FIND ACCOUNT
    // =====================================================

    private Account getAccount(Long id) {

        return accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found"));
    }

    // =====================================================
    // VALIDATE AMOUNT
    // =====================================================

    private void validateAmount(Double amount) {

        if (amount == null || amount <= 0) {

            throw new RuntimeException(
                    "Amount must be greater than zero");
        }
    }

    // =====================================================
    // VALIDATE ACCOUNT OWNERSHIP
    // =====================================================

    private void validateOwnership(
            Account account,
            String username) {

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"));

        // ADMIN can access everything
        if ("ADMIN".equalsIgnoreCase(
                user.getRole())) {

            return;
        }

        // Customer must have linked customer
        if (user.getCustomer() == null) {

            throw new RuntimeException(
                    "Customer is not linked to this user");
        }

        Long userCustomerId =
                user.getCustomer()
                        .getCustomerId();

        Long accountCustomerId =
                account.getCustomer()
                        .getCustomerId();

        if (!userCustomerId.equals(
                accountCustomerId)) {

            throw new RuntimeException(
                    "You are not authorized to access this account");
        }
    }

    // =====================================================
    // ENTITY → RESPONSE
    // =====================================================

    private AccountResponse convertToResponse(
            Account account) {

        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCustomer().getName()
        );
    }
}


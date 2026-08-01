package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.repository.TransactionRepository;

import java.time.LocalDateTime;
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRepository transactionRepository) {

        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    // Create Account
    public AccountResponse createAccount(AccountRequest request) {

        // Check if account number already exists
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new RuntimeException("Account number already exists");
        }

        // Find customer
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        // Create account
        Account account = new Account();
        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance());
        account.setCustomer(customer);

        // Save account
        Account savedAccount = accountRepository.save(account);

        // Return response
        return new AccountResponse(
                savedAccount.getAccountId(),
                savedAccount.getAccountNumber(),
                savedAccount.getAccountType(),
                savedAccount.getBalance(),
                customer.getName()
        );
    }
    public AccountResponse getAccountById(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCustomer().getName()
        );
    }
    public AccountResponse updateAccount(Long id, AccountRequest request) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance());

        Account updated = accountRepository.save(account);

        return new AccountResponse(
                updated.getAccountId(),
                updated.getAccountNumber(),
                updated.getAccountType(),
                updated.getBalance(),
                updated.getCustomer().getName()
        );
    }
    public void deleteAccount(Long id) {

        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Account not found");
        }

        accountRepository.deleteById(id);
    }
    // Get All Accounts
    public List<AccountResponse> getAllAccounts() {

        return accountRepository.findAll()
                .stream()
                .map(account -> new AccountResponse(
                        account.getAccountId(),
                        account.getAccountNumber(),
                        account.getAccountType(),
                        account.getBalance(),
                        account.getCustomer().getName()
                ))
                .collect(Collectors.toList());
    }
    public AccountResponse withdraw(Long id, Double amount) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (amount <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        if (account.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        account.setBalance(account.getBalance() - amount);

        Account updated = accountRepository.save(account);
        Transaction transaction = new Transaction();

        transaction.setTransactionType("WITHDRAW");
        transaction.setAmount(amount);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setAccount(updated);

        transactionRepository.save(transaction);

        return new AccountResponse(
                updated.getAccountId(),
                updated.getAccountNumber(),
                updated.getAccountType(),
                updated.getBalance(),
                updated.getCustomer().getName()
        );
    }
    public AccountResponse deposit(Long id, Double amount) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (amount <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        account.setBalance(account.getBalance() + amount);

        Account updated = accountRepository.save(account);
        Transaction transaction = new Transaction();

        transaction.setTransactionType("DEPOSIT");
        transaction.setAmount(amount);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setAccount(updated);

        transactionRepository.save(transaction);

        return new AccountResponse(
                updated.getAccountId(),
                updated.getAccountNumber(),
                updated.getAccountType(),
                updated.getBalance(),
                updated.getCustomer().getName()
        );
    }
    @Transactional
    public void transferMoney(Long fromId, Long toId, Double amount) {

        Account fromAccount = accountRepository.findById(fromId)
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        Account toAccount = accountRepository.findById(toId)
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        if (amount <= 0) {
            throw new RuntimeException("Invalid amount");
        }

        if (fromAccount.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        Transaction sender = new Transaction();

        sender.setTransactionType("TRANSFER_OUT");
        sender.setAmount(amount);
        sender.setTransactionDate(LocalDateTime.now());
        sender.setAccount(fromAccount);

        transactionRepository.save(sender);

        Transaction receiver = new Transaction();

        receiver.setTransactionType("TRANSFER_IN");
        receiver.setAmount(amount);
        receiver.setTransactionDate(LocalDateTime.now());
        receiver.setAccount(toAccount);

        transactionRepository.save(receiver);
    }
}
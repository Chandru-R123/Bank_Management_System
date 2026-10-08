package com.chandru.bankmanagement.config;

import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.AccountStatus;
import com.chandru.bankmanagement.entity.Beneficiary;
import com.chandru.bankmanagement.entity.Consent;
import com.chandru.bankmanagement.entity.ConsentPermission;
import com.chandru.bankmanagement.entity.ConsentStatus;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.entity.TransactionRequest;
import com.chandru.bankmanagement.entity.TransactionRequestStatus;
import com.chandru.bankmanagement.entity.TransactionRequestType;
import com.chandru.bankmanagement.entity.TransactionTypes;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.BeneficiaryRepository;
import com.chandru.bankmanagement.repository.ConsentRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.repository.TransactionRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Sample data for demos: 5 customers, 8 accounts, ~30 days of transactions
 * (with correct running balances), beneficiaries and Open Banking consents
 * in every status.
 *
 * - Runs once: skipped if the marker customer already exists.
 * - Runs in one DB transaction: all or nothing.
 * - Never blocks startup: any failure is logged and ignored.
 * - Disable with SEED_SAMPLE_DATA=false.
 *
 * The five customers have matching Keycloak logins in the realm import
 * (password Customer@1234) and are linked by email on first login.
 */
@Configuration
public class SampleDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

    static final String MARKER_EMAIL = "meera.nair@example.com";

    @Bean
    @Order(2)
    public CommandLineRunner seedSampleData(CustomerRepository customers,
                                            AccountRepository accounts,
                                            TransactionRepository transactions,
                                            BeneficiaryRepository beneficiaries,
                                            ConsentRepository consents,
                                            TransactionRequestRepository txRequests,
                                            PlatformTransactionManager transactionManager,
                                            @Value("${app.seed-sample-data:true}") boolean enabled) {
        return args -> {
            if (!enabled) {
                log.info("SAMPLE DATA: disabled (app.seed-sample-data=false)");
                return;
            }
            if (customers.existsByEmailIgnoreCase(MARKER_EMAIL)) {
                log.info("SAMPLE DATA: already present — skipping");
                return;
            }
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        new Seeder(customers, accounts, transactions, beneficiaries, consents, txRequests).run());
                log.info("SAMPLE DATA: seeded 5 customers, 8 accounts, transactions, beneficiaries, consents and Maker–Checker requests");
            } catch (RuntimeException e) {
                log.warn("SAMPLE DATA: skipped because of an error (the app still starts): {}", e.getMessage());
            }
        };
    }

    // ────────────────────────────────────────────────────────────────────

    private static final class Seeder {

        private final CustomerRepository       customers;
        private final AccountRepository        accounts;
        private final TransactionRepository    transactions;
        private final BeneficiaryRepository    beneficiaries;
        private final ConsentRepository        consents;
        private final TransactionRequestRepository txRequests;
        private final LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        Seeder(CustomerRepository customers, AccountRepository accounts,
               TransactionRepository transactions, BeneficiaryRepository beneficiaries,
               ConsentRepository consents, TransactionRequestRepository txRequests) {
            this.customers     = customers;
            this.accounts      = accounts;
            this.transactions  = transactions;
            this.beneficiaries = beneficiaries;
            this.consents      = consents;
            this.txRequests    = txRequests;
        }

        void run() {
            // ── Customers (logins: meera / karthik / divya / vikram / fatima) ──
            Customer meera   = customer("Meera Nair", MARKER_EMAIL, "9840012345",
                    "22 Besant Nagar, Chennai, Tamil Nadu");
            Customer karthik = customer("Karthik Subramanian", "karthik.s@example.com", "9894056789",
                    "14 Race Course Road, Coimbatore, Tamil Nadu");
            Customer divya   = customer("Divya Krishnan", "divya.krishnan@example.com", "9786543210",
                    "9 KK Nagar, Madurai, Tamil Nadu");
            Customer vikram  = customer("Vikram Reddy", "vikram.reddy@example.com", "9900112233",
                    "301 Jubilee Hills, Hyderabad, Telangana");
            Customer fatima  = customer("Fatima Sheikh", "fatima.sheikh@example.com", "9123012345",
                    "45 Fraser Town, Bengaluru, Karnataka");

            // ── Accounts (opened 30 days ago with an opening deposit) ─────────
            Account meeraSb     = account(meera,   "SB-2001-2025", "SAVINGS",       "25000.00");
            Account karthikSb   = account(karthik, "SB-2002-2025", "SAVINGS",       "10000.00");
            Account karthikCa   = account(karthik, "CA-2003-2025", "CURRENT",       "50000.00");
            Account karthikOld  = account(karthik, "SB-2004-2025", "SAVINGS",       "5000.00");
            Account divyaSb     = account(divya,   "SB-2005-2025", "SAVINGS",       "20000.00");
            account(divya, "FD-2006-2025", "FIXED_DEPOSIT", "100000.00");
            Account vikramCa    = account(vikram,  "CA-2007-2025", "CURRENT",       "75000.00");
            Account fatimaSb    = account(fatima,  "SB-2008-2025", "SAVINGS",       "15000.00");

            // ── 30 days of activity, oldest first ────────────────────────────
            deposit (karthikCa, "85000.00", "Client payment — Invoice #1042", at(28, 11), "maker");
            deposit (meeraSb,   "42000.00", "Salary credit",                 at(27, 9),  "maker");
            transfer(meeraSb, divyaSb,   "3500.00",  "Dinner split",          at(25, 21), "meera");
            withdraw(divyaSb,   "5000.00",  "ATM withdrawal",                at(24, 18), "divya");
            transfer(karthikCa, vikramCa, "27500.00", "Office supplies",      at(22, 15), "karthik");
            deposit (vikramCa,  "60000.00", "Cash deposit at branch",        at(20, 12), "maker");
            deposit (fatimaSb,  "18000.00", "Freelance project payment",     at(18, 10), "maker");
            withdraw(meeraSb,   "8000.00",  "Rent — October",                at(15, 8),  "meera");
            deposit (karthikSb, "12000.00", "Savings top-up",                at(14, 17), "karthik");
            withdraw(vikramCa,  "15000.00", "Vendor payout",                 at(12, 14), "maker");
            transfer(divyaSb, meeraSb,   "1200.00",  "Movie tickets",         at(10, 20), "divya");
            transfer(fatimaSb, karthikSb, "2500.00", "Book club share",       at(9, 19),  "fatima");
            close   (karthikOld, at(6, 11));
            deposit (meeraSb,   "5000.00",  "Festival bonus",                at(5, 10),  "maker");
            transfer(vikramCa, fatimaSb, "4000.00",  "Design work",           at(4, 16),  "vikram");
            withdraw(karthikCa, "20000.00", "Staff salary disbursal",        at(3, 10),  "maker");
            deposit (divyaSb,   "7500.00",  "Cashback & refunds",            at(2, 13),  "divya");
            transfer(meeraSb, karthikCa, "1500.00",  "Consulting fee",        at(1, 12),  "meera");
            withdraw(fatimaSb,  "1000.00",  "ATM withdrawal",                at(0, 9),   "fatima");
            deposit (karthikCa, "30000.00", "Client payment — Invoice #1057", at(0, 10), "maker");

            // Fatima's account is frozen for review (demo of the FROZEN status)
            fatimaSb.setStatus(AccountStatus.FROZEN);
            accounts.save(fatimaSb);

            // ── Beneficiaries ────────────────────────────────────────────────
            beneficiary(meera,  "Divya",            divyaSb.getAccountNumber());
            beneficiary(meera,  "Karthik (Office)", karthikCa.getAccountNumber());
            beneficiary(divya,  "Meera",            meeraSb.getAccountNumber());
            beneficiary(fatima, "Karthik",          karthikSb.getAccountNumber());
            beneficiary(vikram, "Fatima Design",    fatimaSb.getAccountNumber());
            beneficiary(karthik, "Vikram Traders",  vikramCa.getAccountNumber());

            // ── Open Banking consents (TPP login: fintech-app) ────────────────
            Set<ConsentPermission> all = EnumSet.allOf(ConsentPermission.class);
            consent(meera, "BudgetBuddy", "Monthly budgeting and spend insights", all,
                    ConsentStatus.AUTHORISED, 10, 80, Set.of(meeraSb), "meera");
            consent(meera, "LoanWise Credit", "Pre-approved personal loan offer",
                    EnumSet.of(ConsentPermission.READ_ACCOUNTS, ConsentPermission.READ_BALANCES),
                    ConsentStatus.AWAITING_AUTHORISATION, 0, 30, Set.of(), null);
            consent(karthik, "BudgetBuddy", "Business cash-flow dashboard",
                    EnumSet.of(ConsentPermission.READ_ACCOUNTS, ConsentPermission.READ_TRANSACTIONS),
                    ConsentStatus.AUTHORISED, 7, 173, Set.of(karthikSb, karthikCa), "karthik");
            consent(divya, "SaveSmart", "Round-up savings", all,
                    ConsentStatus.REJECTED, 12, 78, Set.of(), "divya");
            consent(vikram, "BudgetBuddy", "Expense categorisation", all,
                    ConsentStatus.REVOKED, 20, 70, Set.of(vikramCa), "vikram");
            consent(fatima, "TaxEase", "Income proof for tax filing",
                    EnumSet.of(ConsentPermission.READ_ACCOUNTS, ConsentPermission.READ_TRANSACTIONS),
                    ConsentStatus.EXPIRED, 40, -10, Set.of(fatimaSb), "system");

            // ── Extras for the original demo customers, if present ───────────
            customers.findByEmailIgnoreCase("rahul.sharma@statebank.com").ifPresent(rahul -> {
                accounts.findByAccountNumber("SB-1002-2024")
                        .ifPresent(a -> beneficiary(rahul, "Priya", a.getAccountNumber()));
                beneficiary(rahul, "Meera", meeraSb.getAccountNumber());
                consent(rahul, "BudgetBuddy", "Monthly budgeting and spend insights", all,
                        ConsentStatus.AWAITING_AUTHORISATION, 0, 90, Set.of(), null);
            });

            // ── Maker–Checker sample requests (idempotent: skipped if ref already exists) ──
            // Demo requests in every status so the UI has something to display.
            // Maker = "maker-demo", Checker = "checker-demo" (Keycloak demo staff accounts)
            //
            // PENDING_APPROVAL — waiting for checker
            txRequest("TXR-DEMO-PENDING-01", TransactionRequestType.DEPOSIT,
                    TransactionRequestStatus.PENDING_APPROVAL,
                    karthikCa, null, "25000.00",
                    "Branch cash deposit — pending approval",
                    "maker-demo", "maker-sub-placeholder",
                    null, null,
                    at(1, 10), null, null, null, null);

            // PENDING_APPROVAL — a withdrawal waiting for checker
            txRequest("TXR-DEMO-PENDING-02", TransactionRequestType.WITHDRAW,
                    TransactionRequestStatus.PENDING_APPROVAL,
                    vikramCa, null, "10000.00",
                    "Vendor payment — pending approval",
                    "maker-demo", "maker-sub-placeholder",
                    null, null,
                    at(0, 9), null, null, null, null);

            // PENDING_APPROVAL — a transfer waiting
            txRequest("TXR-DEMO-PENDING-03", TransactionRequestType.TRANSFER,
                    TransactionRequestStatus.PENDING_APPROVAL,
                    karthikCa, meeraSb, "5000.00",
                    "Staff salary advance — pending approval",
                    "maker-demo", "maker-sub-placeholder",
                    null, null,
                    at(0, 11), null, null, null, null);

            // SUCCESS — already approved and executed
            txRequest("TXR-DEMO-SUCCESS-01", TransactionRequestType.DEPOSIT,
                    TransactionRequestStatus.SUCCESS,
                    meeraSb, null, "15000.00",
                    "Salary disbursement",
                    "maker-demo", "maker-sub-placeholder",
                    "checker-demo", "checker-sub-placeholder",
                    at(5, 9), at(5, 10), null,
                    "Approved and executed", null);

            // SUCCESS — approved transfer
            txRequest("TXR-DEMO-SUCCESS-02", TransactionRequestType.TRANSFER,
                    TransactionRequestStatus.SUCCESS,
                    vikramCa, karthikSb, "8000.00",
                    "Inter-branch fund movement",
                    "maker-demo", "maker-sub-placeholder",
                    "checker-demo", "checker-sub-placeholder",
                    at(3, 14), at(3, 15), null,
                    null, null);

            // REJECTED — checker rejected with reason
            txRequest("TXR-DEMO-REJECTED-01", TransactionRequestType.WITHDRAW,
                    TransactionRequestStatus.REJECTED,
                    karthikCa, null, "50000.00",
                    "Large cash withdrawal",
                    "maker-demo", "maker-sub-placeholder",
                    "checker-demo", "checker-sub-placeholder",
                    at(7, 11), null, at(7, 14),
                    null, "Amount exceeds daily branch limit without manager authorisation");

            // CANCELLED — maker cancelled before checker acted
            txRequest("TXR-DEMO-CANCELLED-01", TransactionRequestType.DEPOSIT,
                    TransactionRequestStatus.CANCELLED,
                    divyaSb, null, "3000.00",
                    "Cancelled — duplicate entry",
                    "maker-demo", "maker-sub-placeholder",
                    null, null,
                    at(2, 10), null, null,
                    "Duplicate — cancelled by maker", null);
        }

        // ── builders ─────────────────────────────────────────────────────

        private Customer customer(String name, String email, String phone, String address) {
            Customer c = new Customer();
            c.setName(name);
            c.setEmail(email);
            c.setPhone(phone);
            c.setAddress(address);
            return customers.save(c);
        }

        private Account account(Customer owner, String number, String type, String opening) {
            Account a = new Account();
            a.setAccountNumber(number);
            a.setAccountType(type);
            a.setBalance(BigDecimal.ZERO.setScale(2));
            a.setCustomer(owner);
            a.setStatus(AccountStatus.ACTIVE);
            a.setCreatedAt(at(30, 10));
            a = accounts.save(a);
            credit(a, TransactionTypes.OPENING_DEPOSIT, opening, "Account opening deposit",
                    null, null, at(30, 10), "admin-user");
            return a;
        }

        private void deposit(Account a, String amount, String desc, LocalDateTime when, String by) {
            credit(a, TransactionTypes.DEPOSIT, amount, desc, null, null, when, by);
        }

        private void withdraw(Account a, String amount, String desc, LocalDateTime when, String by) {
            debit(a, TransactionTypes.WITHDRAW, amount, desc, null, null, when, by);
        }

        private void transfer(Account from, Account to, String amount, String desc,
                              LocalDateTime when, String by) {
            String ref = "TRF" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            debit(from, TransactionTypes.TRANSFER_OUT, amount, desc, to.getAccountNumber(), ref, when, by);
            credit(to, TransactionTypes.TRANSFER_IN, amount, desc, from.getAccountNumber(), ref, when, by);
        }

        private void close(Account a, LocalDateTime when) {
            debit(a, TransactionTypes.CLOSURE_PAYOUT, a.getBalance().toPlainString(),
                    "Account closed — balance paid out", null, null, when, "admin-user");
            a.setStatus(AccountStatus.CLOSED);
            a.setClosedAt(when);
            accounts.save(a);
        }

        private void credit(Account a, String type, String amount, String desc,
                            String counterparty, String ref, LocalDateTime when, String by) {
            BigDecimal amt = new BigDecimal(amount).setScale(2);
            a.setBalance(a.getBalance().add(amt));
            accounts.save(a);
            record(a, type, amt, desc, counterparty, ref, when, by);
        }

        private void debit(Account a, String type, String amount, String desc,
                           String counterparty, String ref, LocalDateTime when, String by) {
            BigDecimal amt = new BigDecimal(amount).setScale(2);
            if (a.getBalance().compareTo(amt) < 0) {
                throw new IllegalStateException("Sample data would overdraw " + a.getAccountNumber());
            }
            a.setBalance(a.getBalance().subtract(amt));
            accounts.save(a);
            record(a, type, amt, desc, counterparty, ref, when, by);
        }

        private void record(Account a, String type, BigDecimal amount, String desc,
                            String counterparty, String ref, LocalDateTime when, String by) {
            Transaction t = new Transaction();
            t.setAccount(a);
            t.setTransactionType(type);
            t.setAmount(amount);
            t.setTransactionDate(when);
            t.setBalanceAfter(a.getBalance());
            t.setDescription(desc);
            t.setCounterpartyAccountNumber(counterparty);
            t.setReferenceId(ref);
            t.setPerformedBy(by);
            transactions.save(t);
        }

        private void beneficiary(Customer owner, String nickname, String accountNumber) {
            if (beneficiaries.existsByCustomerCustomerIdAndAccountNumberIgnoreCase(
                    owner.getCustomerId(), accountNumber)) {
                return;
            }
            Beneficiary b = new Beneficiary();
            b.setCustomer(owner);
            b.setNickname(nickname);
            b.setAccountNumber(accountNumber);
            b.setCreatedAt(at(8, 12));
            beneficiaries.save(b);
        }

        /**
         * @param createdDaysAgo when the TPP requested it
         * @param expiresInDays  days from today until expiry (negative = already expired)
         */
        private void consent(Customer customer, String tppName, String purpose,
                             Set<ConsentPermission> permissions, ConsentStatus status,
                             int createdDaysAgo, int expiresInDays, Set<Account> shared,
                             String decidedBy) {
            Consent c = new Consent();
            c.setConsentId("CNS-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 16).toUpperCase(Locale.ROOT));
            c.setCustomer(customer);
            c.setTppUsername("fintech-app");
            c.setTppName(tppName);
            c.setPurpose(purpose);
            c.setPermissions(new HashSet<>(permissions));
            c.setAccounts(new HashSet<>(shared));
            c.setStatus(status);
            c.setCreatedAt(at(createdDaysAgo, 9));
            c.setExpiresAt(now.plusDays(expiresInDays));
            if (status != ConsentStatus.AWAITING_AUTHORISATION) {
                c.setStatusUpdatedAt(status == ConsentStatus.EXPIRED
                        ? now.plusDays(expiresInDays)
                        : at(Math.max(createdDaysAgo - 1, 0), 10));
                c.setStatusUpdatedBy(decidedBy);
            }
            consents.save(c);
        }

        /** A time {@code daysAgo} days back at the given hour — never in the future. */
        private LocalDateTime at(int daysAgo, int hour) {
            LocalDateTime t = now.minusDays(daysAgo).withHour(hour).withMinute(15);
            if (t.isAfter(now)) {
                // "today" events scheduled later than the current time: keep them in the past, in order
                t = now.minusMinutes(Math.max(1, 24 - hour) * 5L);
            }
            return t;
        }

        /**
         * Idempotent — skipped if a request with the same requestRef already exists.
         * Uses placeholder sub values since Keycloak UUIDs are not known at seed time;
         * the UI lookup is always by username in real use.
         */
        private void txRequest(String ref,
                               TransactionRequestType type,
                               TransactionRequestStatus status,
                               Account fromAccount,
                               Account toAccount,
                               String amount,
                               String description,
                               String makerUsername, String makerUserId,
                               String checkerUsername, String checkerUserId,
                               LocalDateTime createdAt,
                               LocalDateTime approvedAt,
                               LocalDateTime rejectedAt,
                               String remarks,
                               String rejectionReason) {
            if (txRequests.existsByRequestRef(ref)) return; // idempotent
            TransactionRequest r = new TransactionRequest();
            r.setRequestRef(ref);
            r.setRequestType(type);
            r.setStatus(status);
            r.setFromAccount(fromAccount);
            r.setToAccount(toAccount);
            r.setAmount(new BigDecimal(amount).setScale(2));
            r.setDescription(description);
            r.setMakerUsername(makerUsername);
            r.setMakerUserId(makerUserId);
            r.setCheckerUsername(checkerUsername);
            r.setCheckerUserId(checkerUserId);
            r.setCreatedAt(createdAt);
            r.setApprovedAt(approvedAt);
            r.setRejectedAt(rejectedAt);
            r.setRemarks(remarks);
            r.setRejectionReason(rejectionReason);
            if (status == TransactionRequestStatus.SUCCESS) {
                r.setExecutedAt(approvedAt != null ? approvedAt.plusMinutes(1) : createdAt.plusHours(1));
            }
            txRequests.save(r);
        }
    }
}

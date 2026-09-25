package com.chandru.bankmanagement.config;

import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.AccountStatus;
import com.chandru.bankmanagement.entity.Customer;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;

/**
 * Seeds three demo customers on first startup.
 *
 * The keycloak_sub values below are PLACEHOLDERS.
 * After creating the Keycloak test users (rahul / priya / arjun),
 * copy their UUIDs from the Keycloak admin console and update these
 * constants — or leave them as-is and link them later via the admin API.
 *
 * Authentication (passwords, sessions) is handled entirely by Keycloak.
 * No local User table is used.
 */
@Configuration
public class DataInitializer {

    // ── Replace these with the real Keycloak user UUIDs after setup ────
    private static final String SUB_RAHUL = "keycloak-sub-rahul-placeholder";
    private static final String SUB_PRIYA = "keycloak-sub-priya-placeholder";
    private static final String SUB_ARJUN = "keycloak-sub-arjun-placeholder";

    @Bean
    @Order(1)   // before SampleDataInitializer, which references these customers
    public CommandLineRunner seedData(
            CustomerRepository customerRepo,
            AccountRepository  accountRepo) {

        return args -> {

            if (customerRepo.existsByEmail("rahul.sharma@statebank.com")) {
                System.out.println("DATA: Already seeded — skipping.");
                return;
            }

            System.out.println("DATA: Seeding demo customers & accounts...");

            // ── Customer 1: Rahul Sharma ────────────────────────────
            Customer c1 = new Customer();
            c1.setName("Rahul Sharma");
            c1.setEmail("rahul.sharma@statebank.com");
            c1.setPhone("9876543210");
            c1.setAddress("12 Anna Nagar, Chennai, Tamil Nadu");
            c1.setKeycloakSub(SUB_RAHUL);
            customerRepo.save(c1);

            Account a1 = new Account();
            a1.setAccountNumber("SB-1001-2024");
            a1.setAccountType("SAVINGS");
            a1.setBalance(new BigDecimal("50000.00"));
            a1.setStatus(AccountStatus.ACTIVE);
            a1.setCustomer(c1);
            accountRepo.save(a1);

            // ── Customer 2: Priya Venkat ────────────────────────────
            Customer c2 = new Customer();
            c2.setName("Priya Venkat");
            c2.setEmail("priya.venkat@statebank.com");
            c2.setPhone("9123456780");
            c2.setAddress("45 Gandhipuram, Coimbatore, Tamil Nadu");
            c2.setKeycloakSub(SUB_PRIYA);
            customerRepo.save(c2);

            Account a2 = new Account();
            a2.setAccountNumber("SB-1002-2024");
            a2.setAccountType("CURRENT");
            a2.setBalance(new BigDecimal("125000.00"));
            a2.setStatus(AccountStatus.ACTIVE);
            a2.setCustomer(c2);
            accountRepo.save(a2);

            // ── Customer 3: Arjun Mehta ─────────────────────────────
            Customer c3 = new Customer();
            c3.setName("Arjun Mehta");
            c3.setEmail("arjun.mehta@statebank.com");
            c3.setPhone("9988776655");
            c3.setAddress("78 RS Puram, Coimbatore, Tamil Nadu");
            c3.setKeycloakSub(SUB_ARJUN);
            customerRepo.save(c3);

            Account a3 = new Account();
            a3.setAccountNumber("FD-1003-2024");
            a3.setAccountType("FIXED_DEPOSIT");
            a3.setBalance(new BigDecimal("200000.00"));
            a3.setStatus(AccountStatus.ACTIVE);
            a3.setCustomer(c3);
            accountRepo.save(a3);

            System.out.println("DATA: Seeded 3 customers & 3 accounts.");
            System.out.println("DATA: Update keycloak_sub after creating Keycloak users.");
        };
    }
}

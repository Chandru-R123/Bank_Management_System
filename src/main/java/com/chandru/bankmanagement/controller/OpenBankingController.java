package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.OpenBankingAccountResponse;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.ConsentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Account Information APIs for Third-Party Providers.
 * Every call must carry the id of an AUTHORISED consent:
 *
 *   x-consent-id: CNS-XXXXXXXXXXXXXXXX
 *
 *   GET /api/open-banking/accounts                        READ_ACCOUNTS (+ balance with READ_BALANCES)
 *   GET /api/open-banking/accounts/{accountId}/transactions  READ_TRANSACTIONS
 */
@RestController
@RequestMapping("/api/open-banking")
@PreAuthorize("hasAnyRole('TPP', 'ADMIN')")
public class OpenBankingController {

    private final ConsentService consentService;

    public OpenBankingController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping("/accounts")
    public List<OpenBankingAccountResponse> accounts(
            @RequestHeader(value = "x-consent-id", required = false) String consentId,
            @AuthenticationPrincipal Jwt jwt) {
        return consentService.sharedAccounts(consentId, SecurityUtils.actor(jwt));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<TransactionResponse> transactions(
            @PathVariable Long accountId,
            @RequestHeader(value = "x-consent-id", required = false) String consentId,
            @AuthenticationPrincipal Jwt jwt) {
        return consentService.sharedTransactions(consentId, accountId, SecurityUtils.actor(jwt));
    }
}

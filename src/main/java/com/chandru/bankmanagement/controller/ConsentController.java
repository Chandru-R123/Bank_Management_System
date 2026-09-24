package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.ConsentApprovalRequest;
import com.chandru.bankmanagement.dto.ConsentRequest;
import com.chandru.bankmanagement.dto.ConsentResponse;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.ConsentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Open Banking consent management.
 *
 *   POST /api/consents               TPP (or ADMIN) — request access
 *   GET  /api/consents               STAFF: all · TPP: own requests · CUSTOMER: own consents
 *   GET  /api/consents/{id}
 *   POST /api/consents/{id}/approve  CUSTOMER — choose accounts to share
 *   POST /api/consents/{id}/reject   CUSTOMER
 *   POST /api/consents/{id}/revoke   CUSTOMER, the TPP, ADMIN or CHECKER
 */
@RestController
@RequestMapping("/api/consents")
public class ConsentController {

    private static final String ANY_USER =
            "hasAnyRole('ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER', 'CUSTOMER', 'TPP')";

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @PreAuthorize("hasAnyRole('TPP', 'ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse create(@Valid @RequestBody ConsentRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return consentService.create(request, SecurityUtils.actor(jwt));
    }

    @PreAuthorize(ANY_USER)
    @GetMapping
    public List<ConsentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return consentService.list(SecurityUtils.actor(jwt));
    }

    @PreAuthorize(ANY_USER)
    @GetMapping("/{id}")
    public ConsentResponse get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return consentService.get(id, SecurityUtils.actor(jwt));
    }

    @PreAuthorize(Roles.CUSTOMER)
    @PostMapping("/{id}/approve")
    public ConsentResponse approve(@PathVariable String id,
                                   @Valid @RequestBody ConsentApprovalRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return consentService.approve(id, request, SecurityUtils.actor(jwt));
    }

    @PreAuthorize(Roles.CUSTOMER)
    @PostMapping("/{id}/reject")
    public ConsentResponse reject(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return consentService.reject(id, SecurityUtils.actor(jwt));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'TPP', 'ADMIN', 'CHECKER')")
    @PostMapping("/{id}/revoke")
    public ConsentResponse revoke(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return consentService.revoke(id, SecurityUtils.actor(jwt));
    }
}

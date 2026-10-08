package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.CheckerActionDto;
import com.chandru.bankmanagement.dto.CreateTransactionRequestDto;
import com.chandru.bankmanagement.dto.TransactionRequestResponse;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.TransactionRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Maker–Checker transaction request API.
 *
 * POST   /api/transaction-requests            MAKER/ADMIN — create request (no balance change)
 * GET    /api/transaction-requests/my         MAKER — own requests
 * GET    /api/transaction-requests/pending    CHECKER/ADMIN — all PENDING_APPROVAL
 * GET    /api/transaction-requests            CHECKER/ADMIN — all requests
 * GET    /api/transaction-requests/{id}       MAKER (own) / CHECKER / ADMIN
 * POST   /api/transaction-requests/{id}/approve  CHECKER/ADMIN — approve + execute
 * POST   /api/transaction-requests/{id}/reject   CHECKER/ADMIN — reject
 * POST   /api/transaction-requests/{id}/cancel   MAKER (own) / ADMIN — cancel
 */
@RestController
@RequestMapping("/api/transaction-requests")
public class TransactionRequestController {

    private final TransactionRequestService requestService;

    public TransactionRequestController(TransactionRequestService requestService) {
        this.requestService = requestService;
    }

    // ── MAKER: create ─────────────────────────────────────────────────────────

    @PreAuthorize(Roles.MAKERS)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionRequestResponse create(
            @Valid @RequestBody CreateTransactionRequestDto dto,
            @AuthenticationPrincipal Jwt jwt) {
        return requestService.create(dto, SecurityUtils.actor(jwt));
    }

    // ── MAKER: my own requests ────────────────────────────────────────────────

    @PreAuthorize(Roles.MAKERS)
    @GetMapping("/my")
    public List<TransactionRequestResponse> getMy(@AuthenticationPrincipal Jwt jwt) {
        var actor = SecurityUtils.actor(jwt);
        return requestService.listForActor(actor);
    }

    // ── CHECKER/ADMIN: pending queue ──────────────────────────────────────────

    @PreAuthorize(Roles.CHECKERS)
    @GetMapping("/pending")
    public List<TransactionRequestResponse> getPending(@AuthenticationPrincipal Jwt jwt) {
        return requestService.listPending(SecurityUtils.actor(jwt));
    }

    // ── CHECKER/ADMIN: all requests ───────────────────────────────────────────

    @PreAuthorize(Roles.CHECKERS)
    @GetMapping
    public List<TransactionRequestResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return requestService.listForActor(SecurityUtils.actor(jwt));
    }

    // ── ANY MAKER/CHECKER/ADMIN: single request ───────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'MAKER', 'CHECKER')")   // makers see only their own (checked in the service)
    @GetMapping("/{id}")
    public TransactionRequestResponse getById(@PathVariable Long id,
                                              @AuthenticationPrincipal Jwt jwt) {
        return requestService.getById(id, SecurityUtils.actor(jwt));
    }

    // ── CHECKER/ADMIN: approve ────────────────────────────────────────────────

    @PreAuthorize(Roles.CHECKERS)
    @PostMapping("/{id}/approve")
    public TransactionRequestResponse approve(@PathVariable Long id,
                                              @RequestBody(required = false) CheckerActionDto dto,
                                              @AuthenticationPrincipal Jwt jwt) {
        return requestService.approve(id, dto, SecurityUtils.actor(jwt));
    }

    // ── CHECKER/ADMIN: reject ─────────────────────────────────────────────────

    @PreAuthorize(Roles.CHECKERS)
    @PostMapping("/{id}/reject")
    public TransactionRequestResponse reject(@PathVariable Long id,
                                             @RequestBody CheckerActionDto dto,
                                             @AuthenticationPrincipal Jwt jwt) {
        return requestService.reject(id, dto, SecurityUtils.actor(jwt));
    }

    // ── MAKER/ADMIN: cancel own pending request ───────────────────────────────

    @PreAuthorize(Roles.MAKERS)
    @PostMapping("/{id}/cancel")
    public TransactionRequestResponse cancel(@PathVariable Long id,
                                             @AuthenticationPrincipal Jwt jwt) {
        return requestService.cancel(id, SecurityUtils.actor(jwt));
    }
}

package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.BeneficiaryRequest;
import com.chandru.bankmanagement.dto.BeneficiaryResponse;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 *   POST   /api/beneficiaries       CUSTOMER (own list) or ADMIN/EMPLOYEE (with customerId)
 *   GET    /api/beneficiaries       CUSTOMER (own) or STAFF (all)
 *   DELETE /api/beneficiaries/{id}  ADMIN / CHECKER, or the owning CUSTOMER
 */
@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse create(@Valid @RequestBody BeneficiaryRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return beneficiaryService.create(request, SecurityUtils.actor(jwt));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER')")
    @GetMapping
    public List<BeneficiaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return beneficiaryService.list(SecurityUtils.actor(jwt));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CHECKER', 'CUSTOMER')")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        beneficiaryService.delete(id, SecurityUtils.actor(jwt));
        return Map.of("deleted", true, "beneficiaryId", id);
    }
}

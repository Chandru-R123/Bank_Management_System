package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.KycRecordResponse;
import com.chandru.bankmanagement.dto.KycReviewRequest;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.KycService;
import com.chandru.bankmanagement.service.kyc.KycInitiationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * KYC API endpoints.
 *
 * Customer flow:
 *   POST   /api/kyc/start                           Start KYC process
 *   POST   /api/kyc/documents?type=IDENTITY         Upload a document
 *   POST   /api/kyc/submit                          Submit for review
 *   GET    /api/kyc/my                              Get own KYC status
 *
 * Staff (EMPLOYEE/ADMIN) flow:
 *   GET    /api/kyc/pending                         List pending KYC submissions
 *   GET    /api/kyc/customer/{customerId}            Get full KYC for a customer
 *   GET    /api/kyc/documents/{docId}/download       Securely download a document
 *   POST   /api/kyc/customer/{customerId}/approve    Approve KYC
 *   POST   /api/kyc/customer/{customerId}/reject     Reject KYC with reason
 *
 * TPP: NO ACCESS to any KYC endpoint (enforced in KycService).
 */
@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    // ── CUSTOMER: start KYC ───────────────────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER)
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public KycInitiationResult start(@AuthenticationPrincipal Jwt jwt) {
        return kycService.startKyc(SecurityUtils.actor(jwt));
    }

    // ── CUSTOMER: upload document ─────────────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER)
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public com.chandru.bankmanagement.dto.KycDocumentResponse uploadDocument(
            @RequestParam("type") String documentType,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        return kycService.uploadDocument(documentType, file, SecurityUtils.actor(jwt));
    }

    // ── CUSTOMER: submit for staff review ─────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER)
    @PostMapping("/submit")
    public KycRecordResponse submit(@AuthenticationPrincipal Jwt jwt) {
        return kycService.submitKyc(SecurityUtils.actor(jwt));
    }

    // ── CUSTOMER: get own KYC status ──────────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER)
    @GetMapping("/my")
    public KycRecordResponse getMy(@AuthenticationPrincipal Jwt jwt) {
        return kycService.getMyKyc(SecurityUtils.actor(jwt));
    }

    // ── STAFF: list pending ───────────────────────────────────────────────────

    @PreAuthorize(Roles.KYC_REVIEWERS)
    @GetMapping("/pending")
    public List<KycRecordResponse> getPending() {
        return kycService.listPending();
    }

    // ── STAFF: get customer's KYC ─────────────────────────────────────────────

    @PreAuthorize(Roles.KYC_REVIEWERS)
    @GetMapping("/customer/{customerId}")
    public KycRecordResponse getForCustomer(@PathVariable Long customerId) {
        return kycService.getForCustomer(customerId);
    }

    // ── STAFF: download document (bytes streamed — URL never exposed) ─────────

    @PreAuthorize(Roles.KYC_REVIEWERS)
    @GetMapping("/documents/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long docId,
                                                    @AuthenticationPrincipal Jwt jwt) throws IOException {
        byte[] bytes = kycService.downloadDocument(docId, SecurityUtils.actor(jwt));
        HttpHeaders headers = new HttpHeaders();
        // Do not set Content-Disposition with original filename — use opaque name
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "kyc-document-" + docId);
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ── STAFF: approve ────────────────────────────────────────────────────────

    @PreAuthorize(Roles.KYC_REVIEWERS)
    @PostMapping("/customer/{customerId}/approve")
    public KycRecordResponse approve(@PathVariable Long customerId,
                                     @RequestBody(required = false) @Valid KycReviewRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
        return kycService.approve(customerId, req, SecurityUtils.actor(jwt));
    }

    // ── STAFF: reject ─────────────────────────────────────────────────────────

    @PreAuthorize(Roles.KYC_REVIEWERS)
    @PostMapping("/customer/{customerId}/reject")
    public KycRecordResponse reject(@PathVariable Long customerId,
                                    @RequestBody @Valid KycReviewRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        return kycService.reject(customerId, req, SecurityUtils.actor(jwt));
    }
}

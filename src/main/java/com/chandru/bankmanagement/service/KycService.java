package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.KycDocumentResponse;
import com.chandru.bankmanagement.dto.KycRecordResponse;
import com.chandru.bankmanagement.dto.KycReviewRequest;
import com.chandru.bankmanagement.entity.*;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.exception.CustomerNotFoundException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.CustomerRepository;
import com.chandru.bankmanagement.repository.KycDocumentRepository;
import com.chandru.bankmanagement.repository.KycRecordRepository;
import com.chandru.bankmanagement.security.Actor;
import com.chandru.bankmanagement.service.kyc.KycInitiationResult;
import com.chandru.bankmanagement.service.kyc.KycVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manual KYC workflow.
 *
 * Customer flow:
 *   start() → upload documents → submit() → await staff review
 *
 * Staff flow (EMPLOYEE or ADMIN):
 *   listPending() → getForCustomer() → downloadDocument() → approve() / reject()
 *
 * Security invariants:
 *   - Customer CANNOT self-verify (VERIFIED status can only be set by staff).
 *   - Documents are stored outside the codebase/Docker image (configurable path).
 *   - TPP has NO access to KYC records or documents.
 *   - storageReference is NEVER returned to the client.
 *   - Only EMPLOYEE or ADMIN can read/download documents and approve/reject.
 */
@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    /** Allowed MIME types. Executable and dangerous types are rejected. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic",
            "application/pdf"
    );
    /** Maximum upload size: 10 MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    private final KycRecordRepository   kycRecordRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final CustomerRepository    customerRepository;
    private final KycVerificationService verificationService;
    private final AuditService          auditService;

    @Value("${kyc.upload-dir:./kyc-uploads}")
    private String uploadDir;

    public KycService(KycRecordRepository kycRecordRepository,
                      KycDocumentRepository kycDocumentRepository,
                      CustomerRepository customerRepository,
                      KycVerificationService verificationService,
                      AuditService auditService) {
        this.kycRecordRepository   = kycRecordRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.customerRepository    = customerRepository;
        this.verificationService   = verificationService;
        this.auditService          = auditService;
    }

    // ── CUSTOMER: start KYC ───────────────────────────────────────────────────

    @Transactional
    public KycInitiationResult startKyc(Actor actor) {
        Customer customer = customerRepository.findByKeycloakSub(actor.sub())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer profile linked to this login"));

        KycRecord record = kycRecordRepository
                .findByCustomerCustomerId(customer.getCustomerId())
                .orElseGet(() -> {
                    KycRecord r = new KycRecord();
                    r.setCustomer(customer);
                    r.setStatus(KycStatus.NOT_STARTED);
                    r.setInitiatedAt(LocalDateTime.now());
                    return r;
                });

        // Reject if already VERIFIED (re-start not allowed without staff action)
        if (record.getStatus() == KycStatus.VERIFIED) {
            throw new BusinessRuleException("Your KYC is already verified");
        }
        // Allow re-start only after REJECTED
        if (record.getStatus() == KycStatus.PENDING
                || record.getStatus() == KycStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "A KYC submission is already in progress (status: " + record.getStatus() + ")");
        }

        KycInitiationResult result = verificationService.initiate(record);
        kycRecordRepository.save(record);

        log.info("KYC started for customer id={} using provider={}",
                customer.getCustomerId(), verificationService.providerLabel());
        auditService.log(AuditActions.KYC_STARTED, actor,
                "KycRecord", String.valueOf(customer.getCustomerId()),
                "STARTED", "Provider: " + verificationService.providerLabel());

        return result;
    }

    // ── CUSTOMER: upload document ─────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse uploadDocument(String documentType, MultipartFile file, Actor actor) {
        Customer customer = customerRepository.findByKeycloakSub(actor.sub())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer profile linked to this login"));

        KycRecord record = kycRecordRepository
                .findByCustomerCustomerId(customer.getCustomerId())
                .orElseThrow(() -> new BusinessRuleException(
                        "Please start KYC before uploading documents"));

        if (record.getStatus() == KycStatus.VERIFIED) {
            throw new BusinessRuleException("Your KYC is already verified");
        }
        if (record.getStatus() == KycStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Your KYC is under review. You cannot add documents at this stage");
        }

        // Validate document type
        KycDocumentType docType;
        try {
            docType = KycDocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                    "Invalid documentType. Use: IDENTITY, ADDRESS, PHOTOGRAPH, or OTHER");
        }

        // Validate file
        validateFile(file);

        // Store file securely (outside webroot, not inside the Docker image)
        String storageRef = storeFile(file, customer.getCustomerId(), docType);

        // Sanitise filename (strip path components to prevent traversal)
        String safeName = sanitiseFilename(file.getOriginalFilename());

        KycDocument doc = new KycDocument();
        doc.setCustomer(customer);
        doc.setKycRecord(record);
        doc.setDocumentType(docType);
        doc.setFileName(safeName);
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setStorageReference(storageRef); // never returned in response
        doc.setStatus("PENDING");
        doc.setUploadedAt(LocalDateTime.now());
        kycDocumentRepository.save(doc);

        log.info("KYC document uploaded: customer id={}, type={}, file={}",
                customer.getCustomerId(), docType, safeName);
        auditService.log(AuditActions.KYC_DOCUMENT_UPLOADED, actor,
                "KycDocument", String.valueOf(doc.getId()),
                "UPLOADED", docType + " — " + safeName);

        return toDocResponse(doc);
    }

    // ── CUSTOMER: submit KYC for review ───────────────────────────────────────

    @Transactional
    public KycRecordResponse submitKyc(Actor actor) {
        Customer customer = customerRepository.findByKeycloakSub(actor.sub())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer profile linked to this login"));

        KycRecord record = kycRecordRepository
                .findByCustomerCustomerId(customer.getCustomerId())
                .orElseThrow(() -> new BusinessRuleException("Please start KYC first"));

        if (record.getStatus() == KycStatus.VERIFIED) {
            throw new BusinessRuleException("Your KYC is already verified");
        }
        if (record.getStatus() == KycStatus.PENDING
                || record.getStatus() == KycStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Already submitted (status: " + record.getStatus() + ")");
        }

        // Require at least one document
        List<KycDocument> docs = kycDocumentRepository
                .findByKycRecordIdOrderByUploadedAtAsc(record.getId());
        if (docs.isEmpty()) {
            throw new BusinessRuleException(
                    "Please upload at least one document before submitting");
        }

        verificationService.onSubmit(record, null);
        record.setSubmittedAt(LocalDateTime.now());
        KycRecord saved = kycRecordRepository.save(record);

        log.info("KYC submitted for customer id={}", customer.getCustomerId());
        auditService.log(AuditActions.KYC_SUBMITTED, actor,
                "KycRecord", String.valueOf(customer.getCustomerId()),
                "PENDING", "Submitted " + docs.size() + " document(s)");

        return toResponse(saved, docs, false);
    }

    // ── CUSTOMER: get own KYC status ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public KycRecordResponse getMyKyc(Actor actor) {
        Customer customer = customerRepository.findByKeycloakSub(actor.sub())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer profile linked to this login"));

        KycRecord record = kycRecordRepository
                .findByCustomerCustomerId(customer.getCustomerId())
                .orElseGet(() -> {
                    KycRecord r = new KycRecord();
                    r.setCustomer(customer);
                    r.setStatus(KycStatus.NOT_STARTED);
                    return r;
                });

        List<KycDocument> docs = record.getId() != null
                ? kycDocumentRepository.findByKycRecordIdOrderByUploadedAtAsc(record.getId())
                : List.of();
        // Customer does NOT see reviewerNotes
        return toResponse(record, docs, false);
    }

    // ── STAFF (EMPLOYEE/ADMIN): list pending KYC ──────────────────────────────

    @Transactional(readOnly = true)
    public List<KycRecordResponse> listPending() {
        return kycRecordRepository
                .findByStatusInOrderBySubmittedAtAsc(
                        List.of(KycStatus.PENDING, KycStatus.UNDER_REVIEW))
                .stream()
                .map(r -> toResponse(r,
                        kycDocumentRepository.findByKycRecordIdOrderByUploadedAtAsc(r.getId()),
                        true)) // staff CAN see reviewerNotes
                .toList();
    }

    /** Staff: get any customer's full KYC record. */
    @Transactional(readOnly = true)
    public KycRecordResponse getForCustomer(Long customerId) {
        KycRecord record = kycRecordRepository.findByCustomerCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No KYC record for customer " + customerId));
        List<KycDocument> docs =
                kycDocumentRepository.findByKycRecordIdOrderByUploadedAtAsc(record.getId());
        return toResponse(record, docs, true);
    }

    // ── STAFF (EMPLOYEE/ADMIN): approve ───────────────────────────────────────

    @Transactional
    public KycRecordResponse approve(Long customerId, KycReviewRequest req, Actor actor) {
        KycRecord record = kycRecordRepository.findByCustomerCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No KYC record for customer " + customerId));

        if (record.getStatus() != KycStatus.PENDING
                && record.getStatus() != KycStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "KYC cannot be approved in state: " + record.getStatus());
        }

        // Customer CANNOT self-verify — enforced here
        if (actor.sub().equals(record.getCustomer().getKeycloakSub())) {
            throw new UnauthorizedAccessException(
                    "A customer cannot approve their own KYC");
        }

        record.setStatus(KycStatus.VERIFIED);
        record.setReviewedBy(actor.username());
        record.setReviewedAt(LocalDateTime.now());
        record.setVerifiedAt(LocalDateTime.now());
        if (req != null && req.reviewerNotes() != null) {
            record.setReviewerNotes(req.reviewerNotes());
        }
        record.setRejectionReason(null); // clear previous rejection if any
        KycRecord saved = kycRecordRepository.save(record);

        log.info("KYC APPROVED for customer id={} by {}",
                customerId, actor.username());
        auditService.log(AuditActions.KYC_APPROVED, actor,
                "KycRecord", String.valueOf(customerId), "VERIFIED",
                "Approved by " + actor.username());

        List<KycDocument> docs =
                kycDocumentRepository.findByKycRecordIdOrderByUploadedAtAsc(saved.getId());
        return toResponse(saved, docs, true);
    }

    // ── STAFF (EMPLOYEE/ADMIN): reject ────────────────────────────────────────

    @Transactional
    public KycRecordResponse reject(Long customerId, KycReviewRequest req, Actor actor) {
        KycRecord record = kycRecordRepository.findByCustomerCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No KYC record for customer " + customerId));

        if (record.getStatus() != KycStatus.PENDING
                && record.getStatus() != KycStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "KYC cannot be rejected in state: " + record.getStatus());
        }

        if (req == null || req.rejectionReason() == null || req.rejectionReason().isBlank()) {
            throw new BusinessRuleException("Rejection reason is required");
        }

        record.setStatus(KycStatus.REJECTED);
        record.setReviewedBy(actor.username());
        record.setReviewedAt(LocalDateTime.now());
        record.setRejectionReason(req.rejectionReason().trim());
        if (req.reviewerNotes() != null) {
            record.setReviewerNotes(req.reviewerNotes());
        }
        KycRecord saved = kycRecordRepository.save(record);

        log.info("KYC REJECTED for customer id={} by {} — reason: {}",
                customerId, actor.username(), req.rejectionReason());
        auditService.log(AuditActions.KYC_REJECTED, actor,
                "KycRecord", String.valueOf(customerId), "REJECTED",
                "Reason: " + req.rejectionReason());

        List<KycDocument> docs =
                kycDocumentRepository.findByKycRecordIdOrderByUploadedAtAsc(saved.getId());
        return toResponse(saved, docs, true);
    }

    // ── STAFF: securely download a document ───────────────────────────────────

    /**
     * Returns the file bytes for a KYC document.
     * Only EMPLOYEE and ADMIN may call this endpoint.
     * The storageReference is never exposed to the caller — the controller
     * streams the bytes directly.
     */
    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long documentId, Actor actor) throws IOException {
        KycDocument doc = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "KYC document not found: " + documentId));

        // TPP must NEVER access KYC documents — enforced here
        if (actor.tpp()) {
            throw new UnauthorizedAccessException("TPP cannot access KYC documents");
        }
        // Customer cannot download documents (even their own — use the metadata endpoint)
        if (!actor.staff()) {
            throw new UnauthorizedAccessException("Only EMPLOYEE or ADMIN can download KYC documents");
        }

        Path filePath = Paths.get(uploadDir).resolve(doc.getStorageReference()).normalize();
        // Security: ensure the resolved path stays within the upload directory
        if (!filePath.startsWith(Paths.get(uploadDir).toAbsolutePath())) {
            throw new UnauthorizedAccessException("Invalid document path");
        }
        return Files.readAllBytes(filePath);
    }

    // ── File storage ──────────────────────────────────────────────────────────

    private String storeFile(MultipartFile file, Long customerId, KycDocumentType docType) {
        try {
            Path uploadPath = Paths.get(uploadDir)
                    .resolve("customer-" + customerId)
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(uploadPath);

            // Generate a UUID-based name to prevent guessing and path traversal
            String ext = getExtension(file.getOriginalFilename());
            String uniqueName = docType.name().toLowerCase() + "-"
                    + UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = uploadPath.resolve(uniqueName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // Return relative reference (e.g. "customer-42/identity-abc123.pdf")
            return "customer-" + customerId + "/" + uniqueName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store KYC document: " + e.getMessage(), e);
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleException("File exceeds maximum allowed size of 10 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleException(
                    "Invalid file type '" + contentType + "'. Allowed: JPEG, PNG, HEIC, PDF");
        }

        String filename = file.getOriginalFilename();
        if (filename != null && filename.contains("..")) {
            throw new BusinessRuleException("Invalid filename");
        }
        // Reject executable extensions regardless of MIME type
        if (filename != null && filename.matches(".*\\.(exe|sh|bat|cmd|php|js|jar|class|py|rb)$")) {
            throw new BusinessRuleException("Executable files are not permitted");
        }
    }

    private String sanitiseFilename(String original) {
        if (original == null) return "document";
        // Strip path components and dangerous characters
        return Paths.get(original).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._\\-]", "_")
                .substring(0, Math.min(original.length(), 100));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        String ext = filename.substring(filename.lastIndexOf('.'));
        // Only allow safe extensions
        return ext.matches("\\.(jpg|jpeg|png|heic|pdf)") ? ext.toLowerCase() : "";
    }

    // ── Response mappers ──────────────────────────────────────────────────────

    private KycRecordResponse toResponse(KycRecord r, List<KycDocument> docs, boolean includeStaffNotes) {
        Customer c = r.getCustomer();
        return new KycRecordResponse(
                r.getId(),
                c.getCustomerId(),
                c.getName(),
                c.getEmail(),
                r.getStatus() != null ? r.getStatus().name() : KycStatus.NOT_STARTED.name(),
                r.getMethod() != null ? r.getMethod().name() : null,
                r.getInitiatedAt(),
                r.getSubmittedAt(),
                r.getReviewedAt(),
                r.getVerifiedAt(),
                r.getReviewedBy(),
                r.getRejectionReason(),
                includeStaffNotes ? r.getReviewerNotes() : null,
                docs.stream().map(this::toDocResponse).toList()
        );
    }

    private KycDocumentResponse toDocResponse(KycDocument d) {
        return new KycDocumentResponse(
                d.getId(),
                d.getDocumentType().name(),
                d.getFileName(),
                d.getContentType(),
                d.getFileSize(),
                d.getStatus(),
                d.getUploadedAt()
        );
    }
}

package com.chandru.bankmanagement.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One KYC record per customer — the current verification status and method.
 *
 * Separate KycDocument rows hold individual uploaded files.
 * A customer can resubmit after REJECTED (new documents, same KycRecord updated).
 */
@Entity
@Table(name = "kyc_records",
       indexes = {
           @Index(name = "idx_kyc_customer", columnList = "customer_id", unique = true),
           @Index(name = "idx_kyc_status",   columnList = "status")
       })
public class KycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private KycMethod method;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // ── Review ────────────────────────────────────────────────────────────────

    /** Keycloak preferred_username of the reviewer (employee/ADMIN). */
    @Column(name = "reviewed_by")
    private String reviewedBy;

    /** Rejection reason provided by the reviewer. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Optional reviewer notes (internal only, not shown to customer). */
    @Column(name = "reviewer_notes", length = 500)
    private String reviewerNotes;

    // ── For DigiLocker (populated only after real API success) ───────────────
    /** DigiLocker transaction/reference id — only set after successful API call. */
    @Column(name = "digilocker_ref", length = 120)
    private String digilockerRef;

    public KycRecord() {
    }

    @PrePersist
    void onCreate() {
        if (status == null) status = KycStatus.NOT_STARTED;
        if (initiatedAt == null) initiatedAt = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public KycStatus getStatus() { return status; }
    public void setStatus(KycStatus status) { this.status = status; }

    public KycMethod getMethod() { return method; }
    public void setMethod(KycMethod method) { this.method = method; }

    public LocalDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(LocalDateTime initiatedAt) { this.initiatedAt = initiatedAt; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    public String getDigilockerRef() { return digilockerRef; }
    public void setDigilockerRef(String digilockerRef) { this.digilockerRef = digilockerRef; }
}

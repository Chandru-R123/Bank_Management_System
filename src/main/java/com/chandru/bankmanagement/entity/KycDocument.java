package com.chandru.bankmanagement.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Metadata for one document uploaded as part of a KYC submission.
 *
 * The actual file is NEVER stored in the database or in Git.
 * It is stored in configurable external storage (local filesystem path or
 * Azure Blob Storage) and referenced via {@code storageReference}.
 *
 * Documents are NEVER publicly accessible; they require staff/admin
 * authentication.  TPP must not access these records.
 */
@Entity
@Table(name = "kyc_documents",
       indexes = {
           @Index(name = "idx_kycdoc_customer", columnList = "customer_id"),
           @Index(name = "idx_kycdoc_kyc",      columnList = "kyc_record_id")
       })
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "kyc_record_id", nullable = false)
    private KycRecord kycRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private KycDocumentType documentType;

    /** Original filename as uploaded (sanitised — no path traversal). */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MIME type validated on upload (e.g. image/jpeg, application/pdf). */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** File size in bytes. */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Opaque reference to the file in external storage.
     * For local filesystem: relative path under the configured upload dir.
     * For Azure Blob: "container/blob-name".
     * Never a URL constructed from user input (prevents path traversal).
     */
    @Column(name = "storage_reference", nullable = false, length = 500)
    private String storageReference;

    /** "PENDING", "REVIEWED", "REPLACED" (if resubmitted). */
    @Column(length = 20)
    private String status;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public KycDocument() {
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public KycRecord getKycRecord() { return kycRecord; }
    public void setKycRecord(KycRecord kycRecord) { this.kycRecord = kycRecord; }

    public KycDocumentType getDocumentType() { return documentType; }
    public void setDocumentType(KycDocumentType documentType) { this.documentType = documentType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getStorageReference() { return storageReference; }
    public void setStorageReference(String storageReference) { this.storageReference = storageReference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}

package com.chandru.bankmanagement.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A financial operation submitted by a MAKER that requires CHECKER approval
 * before the actual money movement is executed.
 *
 * Lifecycle:
 *   MAKER calls POST /api/transaction-requests
 *     → status = PENDING_APPROVAL, balances unchanged
 *   CHECKER calls POST /api/transaction-requests/{id}/approve
 *     → status = APPROVED
 *   System executes the money movement
 *     → status = SUCCESS (or FAILED)
 *   CHECKER can reject instead
 *     → status = REJECTED
 *   MAKER can cancel before a CHECKER acts
 *     → status = CANCELLED
 *
 * Key invariants (enforced by TransactionRequestService):
 *   - Creating a request NEVER changes any account balance.
 *   - Balance changes happen ONLY during execution (status PROCESSING → SUCCESS/FAILED).
 *   - makerUserId != checkerUserId (cannot self-approve).
 *   - An EMPLOYEE, CUSTOMER or TPP cannot act as CHECKER.
 *   - Once in a final state the record is immutable.
 *   - Optimistic/pessimistic locking prevents duplicate execution.
 */
@Entity
@Table(name = "transaction_requests",
       indexes = {
           @Index(name = "idx_txreq_status", columnList = "status"),
           @Index(name = "idx_txreq_maker", columnList = "maker_user_id"),
           @Index(name = "idx_txreq_created", columnList = "created_at")
       })
public class TransactionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable request reference, e.g. "TXR-20250401-000042". */
    @Column(nullable = false, unique = true, length = 40)
    private String requestRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionRequestStatus status;

    // ── Source account ──────────────────────────────────────────────────────

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    // ── Destination account (transfers only) ────────────────────────────────

    @ManyToOne
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    // ── Maker identity (never trust client; always from JWT) ─────────────────

    @Column(name = "maker_user_id", nullable = false)
    private String makerUserId;

    @Column(nullable = false)
    private String makerUsername;

    // ── Checker identity (populated on approve/reject) ───────────────────────

    @Column(name = "checker_user_id")
    private String checkerUserId;

    @Column
    private String checkerUsername;

    // ── Timestamps ───────────────────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime rejectedAt;

    private LocalDateTime executedAt;

    // ── Outcome ──────────────────────────────────────────────────────────────

    @Column(length = 500)
    private String rejectionReason;

    @Column(length = 500)
    private String remarks;

    /** The resulting Transaction id after successful execution. */
    private Long transactionId;

    /** Account balance captured after successful execution (from/debit side). */
    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** X-Request-ID from the HTTP request that created this record. */
    @Column(length = 64)
    private String requestId;

    // ── Version field for optimistic locking ─────────────────────────────────
    @Version
    private Long version;

    public TransactionRequest() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = TransactionRequestStatus.PENDING_APPROVAL;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestRef() { return requestRef; }
    public void setRequestRef(String requestRef) { this.requestRef = requestRef; }

    public TransactionRequestType getRequestType() { return requestType; }
    public void setRequestType(TransactionRequestType requestType) { this.requestType = requestType; }

    public TransactionRequestStatus getStatus() { return status; }
    public void setStatus(TransactionRequestStatus status) { this.status = status; }

    public Account getFromAccount() { return fromAccount; }
    public void setFromAccount(Account fromAccount) { this.fromAccount = fromAccount; }

    public Account getToAccount() { return toAccount; }
    public void setToAccount(Account toAccount) { this.toAccount = toAccount; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMakerUserId() { return makerUserId; }
    public void setMakerUserId(String makerUserId) { this.makerUserId = makerUserId; }

    public String getMakerUsername() { return makerUsername; }
    public void setMakerUsername(String makerUsername) { this.makerUsername = makerUsername; }

    public String getCheckerUserId() { return checkerUserId; }
    public void setCheckerUserId(String checkerUserId) { this.checkerUserId = checkerUserId; }

    public String getCheckerUsername() { return checkerUsername; }
    public void setCheckerUsername(String checkerUsername) { this.checkerUsername = checkerUsername; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

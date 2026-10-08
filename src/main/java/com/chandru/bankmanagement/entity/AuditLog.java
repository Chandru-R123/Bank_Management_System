package com.chandru.bankmanagement.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Centralised, append-only audit log.
 *
 * Every significant action in the system — account lifecycle, money movements,
 * Maker–Checker decisions, KYC changes, consent changes, security events —
 * writes one row here.  Rows are never updated or deleted.
 *
 * Fields that are NEVER stored:
 *   passwords, OTPs, access tokens, refresh tokens, CVV,
 *   client secrets, DigiLocker secrets, document file contents.
 */
@Entity
@Table(name = "audit_logs",
       indexes = {
           @Index(name = "idx_audit_actor",    columnList = "actor_user_id"),
           @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id"),
           @Index(name = "idx_audit_ts",       columnList = "timestamp"),
           @Index(name = "idx_audit_action",   columnList = "action"),
           @Index(name = "idx_audit_request",  columnList = "request_id")
       })
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short verb / event name, e.g. "ACCOUNT_FROZEN", "TRANSFER_EXECUTED". */
    @Column(nullable = false, length = 80)
    private String action;

    /** Keycloak "sub" UUID of the actor; "system" for automated events. */
    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;

    /** preferred_username from the JWT. */
    @Column(nullable = false)
    private String actorUsername;

    /** Highest-privilege role in force when the action occurred. */
    @Column(length = 30)
    private String actorRole;

    /** Type of the affected resource, e.g. "Account", "Customer", "Consent". */
    @Column(name = "resource_type", length = 60)
    private String resourceType;

    /** Primary key / identifier of the affected resource (as a string). */
    @Column(name = "resource_id", length = 80)
    private String resourceId;

    /** "SUCCESS", "FAILURE", "REJECTED", "PENDING", etc. */
    @Column(length = 30)
    private String status;

    /** Human-readable notes; must NOT include secrets or passwords. */
    @Column(length = 500)
    private String remarks;

    /** When the event occurred. */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** The Maker–Checker request id this event is associated with (nullable). */
    @Column(name = "request_id", length = 40)
    private String requestId;

    /** HTTP X-Request-ID for correlation with gateway/application logs. */
    @Column(name = "http_request_id", length = 64)
    private String httpRequestId;

    public AuditLog() {
    }

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }

    // ── Builder-style factory ─────────────────────────────────────────────────

    public static AuditLog of(String action, String actorUserId, String actorUsername,
                              String actorRole, String resourceType, String resourceId,
                              String status, String remarks) {
        AuditLog log = new AuditLog();
        log.action        = action;
        log.actorUserId   = actorUserId;
        log.actorUsername = actorUsername;
        log.actorRole     = actorRole;
        log.resourceType  = resourceType;
        log.resourceId    = resourceId;
        log.status        = status;
        log.remarks       = remarks;
        log.timestamp     = LocalDateTime.now();
        return log;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }

    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getHttpRequestId() { return httpRequestId; }
    public void setHttpRequestId(String httpRequestId) { this.httpRequestId = httpRequestId; }
}

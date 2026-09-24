package com.chandru.bankmanagement.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * An Open Banking account-information consent.
 *
 * A Third-Party Provider (TPP) requests access to a customer's data with a
 * set of permissions. The customer chooses which accounts to share and
 * approves — or rejects — the request. The TPP can read data only while the
 * consent is AUTHORISED and not expired, and only for the chosen accounts.
 */
@Entity
@Table(name = "consents")
public class Consent {

    /** Public identifier, e.g. "CNS-3F9A1C2B7D4E5F60". */
    @Id
    @Column(length = 40)
    private String consentId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Keycloak username of the TPP that created the request. */
    @Column(nullable = false)
    private String tppUsername;

    /** Display name of the TPP shown to the customer. */
    @Column(nullable = false)
    private String tppName;

    @Column(nullable = false, length = 140)
    private String purpose;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "consent_permissions", joinColumns = @JoinColumn(name = "consent_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 30)
    private Set<ConsentPermission> permissions = new HashSet<>();

    /** Accounts the customer chose to share (set on approval). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "consent_accounts",
               joinColumns = @JoinColumn(name = "consent_id"),
               inverseJoinColumns = @JoinColumn(name = "account_id"))
    private Set<Account> accounts = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentStatus status;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime statusUpdatedAt;

    /** Username of whoever last changed the status. */
    private String statusUpdatedBy;

    public Consent() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ConsentStatus.AWAITING_AUTHORISATION;
    }

    /** Final states can never change again. */
    public boolean isFinal() {
        return status == ConsentStatus.REJECTED
                || status == ConsentStatus.REVOKED
                || status == ConsentStatus.EXPIRED;
    }

    public String getConsentId() {
        return consentId;
    }

    public void setConsentId(String consentId) {
        this.consentId = consentId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getTppUsername() {
        return tppUsername;
    }

    public void setTppUsername(String tppUsername) {
        this.tppUsername = tppUsername;
    }

    public String getTppName() {
        return tppName;
    }

    public void setTppName(String tppName) {
        this.tppName = tppName;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Set<ConsentPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<ConsentPermission> permissions) {
        this.permissions = permissions;
    }

    public Set<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(Set<Account> accounts) {
        this.accounts = accounts;
    }

    public ConsentStatus getStatus() {
        return status;
    }

    public void setStatus(ConsentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(LocalDateTime statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }

    public String getStatusUpdatedBy() {
        return statusUpdatedBy;
    }

    public void setStatusUpdatedBy(String statusUpdatedBy) {
        this.statusUpdatedBy = statusUpdatedBy;
    }
}

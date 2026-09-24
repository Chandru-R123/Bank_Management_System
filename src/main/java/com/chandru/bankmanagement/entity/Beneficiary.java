package com.chandru.bankmanagement.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A saved payee of a customer. Beneficiaries are State Bank accounts
 * (transfers are internal), stored by account number so the payee
 * survives even if the internal id changes.
 */
@Entity
@Table(name = "beneficiaries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "account_number"}))
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long beneficiaryId;

    /** The customer who owns this payee list entry. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 60)
    private String nickname;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    private LocalDateTime createdAt;

    public Beneficiary() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

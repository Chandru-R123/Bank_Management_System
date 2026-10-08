package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.TransactionRequest;
import com.chandru.bankmanagement.entity.TransactionRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TransactionRequestRepository extends JpaRepository<TransactionRequest, Long> {

    /** Find by request ref (human-readable id). */
    Optional<TransactionRequest> findByRequestRef(String requestRef);

    /** All requests a MAKER created, newest first. */
    List<TransactionRequest> findByMakerUserIdOrderByCreatedAtDesc(String makerUserId);

    /** All PENDING requests (for CHECKERs). */
    List<TransactionRequest> findByStatusOrderByCreatedAtAsc(TransactionRequestStatus status);

    /** All requests across all makers — for ADMIN/CHECKER dashboards. */
    List<TransactionRequest> findAllByOrderByCreatedAtDesc();

    /** Pessimistic write lock — used during approve+execute to prevent duplicate execution. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TransactionRequest r where r.id = :id")
    Optional<TransactionRequest> findByIdForUpdate(Long id);

    /** Check if a request ref already exists (deduplication). */
    boolean existsByRequestRef(String requestRef);
}

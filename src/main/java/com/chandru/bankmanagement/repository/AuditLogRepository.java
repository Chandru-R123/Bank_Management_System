package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** All logs, newest first — for admin dashboard. */
    List<AuditLog> findAllByOrderByTimestampDesc();

    /** Paged — for large datasets. */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /** By actor (staff or customer). */
    List<AuditLog> findByActorUserIdOrderByTimestampDesc(String actorUserId);

    /** By resource (e.g. show all audit events for account id 42). */
    List<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(String resourceType, String resourceId);

    /** By action type. */
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    /** Time-range query — for compliance reports. */
    @Query("select a from AuditLog a where a.timestamp between :from and :to order by a.timestamp desc")
    List<AuditLog> findBetween(LocalDateTime from, LocalDateTime to);

    /** Correlate by Maker–Checker request ref. */
    List<AuditLog> findByRequestIdOrderByTimestampAsc(String requestId);
}

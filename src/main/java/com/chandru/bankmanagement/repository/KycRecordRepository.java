package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.KycRecord;
import com.chandru.bankmanagement.entity.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {

    Optional<KycRecord> findByCustomerCustomerId(Long customerId);

    Optional<KycRecord> findByCustomerKeycloakSub(String keycloakSub);

    List<KycRecord> findByStatusOrderBySubmittedAtAsc(KycStatus status);

    List<KycRecord> findByStatusInOrderBySubmittedAtAsc(List<KycStatus> statuses);

    boolean existsByCustomerCustomerId(Long customerId);
}

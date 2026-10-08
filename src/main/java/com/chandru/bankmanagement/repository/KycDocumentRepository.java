package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByCustomerCustomerIdOrderByUploadedAtDesc(Long customerId);

    List<KycDocument> findByKycRecordIdOrderByUploadedAtAsc(Long kycRecordId);

    void deleteByKycRecordId(Long kycRecordId);
}

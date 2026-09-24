package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findAllByOrderByBeneficiaryIdDesc();

    List<Beneficiary> findByCustomerCustomerIdOrderByNicknameAsc(Long customerId);

    boolean existsByCustomerCustomerIdAndAccountNumberIgnoreCase(Long customerId, String accountNumber);
}

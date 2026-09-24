package com.chandru.bankmanagement.repository;

import com.chandru.bankmanagement.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, String> {

    List<Consent> findAllByOrderByCreatedAtDesc();

    List<Consent> findByCustomerKeycloakSubOrderByCreatedAtDesc(String keycloakSub);

    List<Consent> findByTppUsernameOrderByCreatedAtDesc(String tppUsername);
}

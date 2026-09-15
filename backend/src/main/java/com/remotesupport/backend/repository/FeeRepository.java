package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Fee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

  List<Fee> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}

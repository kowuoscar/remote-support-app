package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Smartphone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartphoneRepository extends JpaRepository<Smartphone, UUID> {

  List<Smartphone> findByContractIdOrderByCreatedAtAsc(UUID contractId);

  Optional<Smartphone> findByIdAndContractId(UUID id, UUID contractId);
}

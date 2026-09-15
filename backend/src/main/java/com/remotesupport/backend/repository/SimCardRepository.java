package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.SimCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimCardRepository extends JpaRepository<SimCard, UUID> {

  List<SimCard> findByContractIdOrderByCreatedAtAsc(UUID contractId);

  Optional<SimCard> findByIdAndContractId(UUID id, UUID contractId);
}

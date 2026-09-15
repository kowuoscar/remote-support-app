package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Tester;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TesterRepository extends JpaRepository<Tester, UUID> {

  List<Tester> findByClientIdOrderByCreatedAtAsc(UUID clientId);

  boolean existsByClientIdAndPrimaryContactTrue(UUID clientId);

  long countByClientId(UUID clientId);
}

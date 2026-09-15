package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

  List<Client> findByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<Client> findByIdAndTenantId(UUID id, UUID tenantId);
}

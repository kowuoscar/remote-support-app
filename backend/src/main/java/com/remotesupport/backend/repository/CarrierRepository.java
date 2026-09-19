package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Country;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarrierRepository extends JpaRepository<Carrier, UUID> {

  List<Carrier> findByTenantIdAndCountry(UUID tenantId, Country country);

  Optional<Carrier> findByIdAndTenantId(UUID id, UUID tenantId);

  /** The active Carrier of a Country holding this name, ignoring case — V19's unique index. */
  @Query(
      "SELECT c FROM Carrier c WHERE c.tenant.id = :tenantId AND c.country = :country"
          + " AND lower(c.name) = lower(:name) AND c.archivedAt IS NULL")
  Optional<Carrier> findActiveByName(
      @Param("tenantId") UUID tenantId, @Param("country") Country country, @Param("name") String name);
}

package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.CarrierOffer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/** The queries Topup Options and Postpaid Plans share. Callers scope the Carrier to a tenant first. */
@NoRepositoryBean
public interface CarrierOfferRepository<T extends CarrierOffer> extends JpaRepository<T, UUID> {

  List<T> findByCarrierId(UUID carrierId);

  List<T> findByCarrierIdIn(Collection<UUID> carrierIds);

  Optional<T> findByIdAndCarrierId(UUID id, UUID carrierId);

  /** The Carrier's active entry holding this name, ignoring case — V21's unique indexes. */
  @Query(
      "SELECT e FROM #{#entityName} e WHERE e.carrier.id = :carrierId"
          + " AND lower(e.name) = lower(:name) AND e.archivedAt IS NULL")
  Optional<T> findActiveByName(@Param("carrierId") UUID carrierId, @Param("name") String name);
}

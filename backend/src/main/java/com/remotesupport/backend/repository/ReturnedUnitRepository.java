package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ReturnedUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnedUnitRepository extends JpaRepository<ReturnedUnit, UUID> {

  /** Every unit named on one Return Request, in the order they were named — for denormalizing
   * {@link com.remotesupport.backend.dto.RequestResponse} and for {@code ReturnCompletionEffect}. */
  List<ReturnedUnit> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}

package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.ReturnedUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnedUnitRepository extends JpaRepository<ReturnedUnit, UUID> {

  /** Every unit named on one Return Request, in the order they were named — for denormalizing
   * {@link com.remotesupport.backend.dto.RequestResponse} and for {@code ReturnCompletionEffect}. */
  List<ReturnedUnit> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

  /**
   * {@link #findByRequestIdOrderByCreatedAtAsc} by {@code Request} rather than a bare id — the one
   * call every caller that already holds the {@link Request} object wants (was duplicated as a
   * private {@code returnedUnitsOf}/{@code returnedUnitsOf} method in both {@code
   * RequestController} and {@code RequestByIdController}; review finding on this feature's
   * finisher pass hoisted it here instead). Cheap and empty for every type but {@code RETURN},
   * since {@code request_id} never matches any row for any other type.
   */
  default List<ReturnedUnit> forRequest(Request request) {
    return findByRequestIdOrderByCreatedAtAsc(request.getId());
  }

  /**
   * The most recent {@link Disposition#KEPT_IN_STOCK} row for one Smartphone/SIM Card (agent-stock
   * ticket) — {@code StockController} reads this to show "the Contract each came from" (ticket AC),
   * since a Stock unit's own {@code contract} is null once it has moved there. Every Stock unit
   * entered through exactly one Return (spec.md Solution's Agent Stock: "units enter through a
   * Return"), so the most recent row is always the right one even if a future ticket lets a unit
   * cycle back into Stock more than once.
   */
  Optional<ReturnedUnit> findFirstBySmartphoneIdAndDispositionOrderByCreatedAtDesc(
      UUID smartphoneId, Disposition disposition);

  Optional<ReturnedUnit> findFirstBySimCardIdAndDispositionOrderByCreatedAtDesc(
      UUID simCardId, Disposition disposition);
}

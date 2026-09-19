package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Disposition;
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

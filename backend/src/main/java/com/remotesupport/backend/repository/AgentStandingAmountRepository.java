package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.AgentStandingAmount;
import com.remotesupport.backend.domain.StandingAmountType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStandingAmountRepository extends JpaRepository<AgentStandingAmount, UUID> {

  /**
   * The row resolving "the amount in effect for {@code month}" (see {@link
   * AgentStandingAmount}'s Javadoc): the most recent row, by {@code effectiveMonth} then {@code
   * setAt} as a same-month tiebreaker, with {@code effectiveMonth <= month}. Empty means "nothing
   * was ever set in effect by this month" — {@code StandingAmountService} treats that as 0.
   */
  Optional<AgentStandingAmount>
      findFirstByAgentIdAndAmountTypeAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDescSetAtDesc(
          UUID agentId, StandingAmountType amountType, LocalDate month);
}

package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Fee;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

  List<Fee> findByContractIdOrderByCreatedAtAsc(UUID contractId);

  /**
   * client-invoice-generation ticket: "every Fee logged against the Contract this month appears
   * as its own line" — the plain equality filter {@code billingMonth} exists to make possible
   * (see Fee.java's Javadoc), backed by V9's {@code idx_fees_contract_id_billing_month} index.
   */
  List<Fee> findByContractIdAndBillingMonthOrderByCreatedAtAsc(UUID contractId, LocalDate billingMonth);
}

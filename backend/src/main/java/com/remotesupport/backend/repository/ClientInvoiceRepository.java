package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientInvoiceRepository extends JpaRepository<ClientInvoice, UUID> {

  Optional<ClientInvoice> findByContractIdAndBillingMonth(UUID contractId, LocalDate billingMonth);

  Optional<ClientInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Every Client Invoice in {@code status} for a tenant, any Contract and billing month, with its
   * frozen total's parts summed in the same query rather than one snapshot read per invoice.
   */
  @Query(
      """
      select new com.remotesupport.backend.repository.ClientInvoiceQueueRow(
          ci.id, ci.status, ci.billingMonth, c.id, cl.name, a.name, ci.currency,
          ci.snapshotBaseAmount, sum(f.amount), ci.sentAt)
      from ClientInvoice ci
      join ci.contract c
      join c.client cl
      join c.agent a
      left join ClientInvoiceFeeSnapshot s on s.clientInvoice = ci
      left join s.fee f
      where ci.tenant.id = :tenantId and ci.status = :status
      group by ci.id, ci.status, ci.billingMonth, c.id, cl.name, a.name, ci.currency,
          ci.snapshotBaseAmount, ci.sentAt
      """)
  List<ClientInvoiceQueueRow> findQueueRows(
      @Param("tenantId") UUID tenantId, @Param("status") ClientInvoiceStatus status);
}

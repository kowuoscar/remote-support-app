package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientInvoiceRepository extends JpaRepository<ClientInvoice, UUID> {

  Optional<ClientInvoice> findByContractIdAndBillingMonth(UUID contractId, LocalDate billingMonth);

  Optional<ClientInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * The invoice re-read under a row lock, for the send: a concurrent send waits for it, then finds
   * the invoice already {@code SENT}.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select ci from ClientInvoice ci where ci.id = :id")
  Optional<ClientInvoice> findByIdForUpdate(@Param("id") UUID id);

  /** The latest-month invoice of a Contract whose status is one of {@code statuses}, if any. */
  Optional<ClientInvoice> findFirstByContractIdAndStatusInOrderByBillingMonthDesc(
      UUID contractId, Collection<ClientInvoiceStatus> statuses);

  /**
   * Every Client Invoice in {@code status} for a tenant, any Contract and billing month, with its
   * total's two parts summed from its stored lines in the same query rather than one line read per
   * invoice. Meant for {@code SENT} invoices, which always have stored lines.
   */
  @Query(
      """
      select new com.remotesupport.backend.repository.ClientInvoiceQueueRow(
          ci.id, ci.status, ci.billingMonth, c.id, cl.name, a.name, ci.currency,
          coalesce(sum(case when l.kind <> com.remotesupport.backend.domain.ClientInvoiceLineKind.FEE
                            then l.amount else 0 end), 0),
          coalesce(sum(case when l.kind = com.remotesupport.backend.domain.ClientInvoiceLineKind.FEE
                            then l.amount else 0 end), 0),
          ci.sentAt)
      from ClientInvoice ci
      join ci.contract c
      join c.client cl
      join c.agent a
      left join ClientInvoiceLine l on l.clientInvoice = ci
      where ci.tenant.id = :tenantId and ci.status = :status
      group by ci.id, ci.status, ci.billingMonth, c.id, cl.name, a.name, ci.currency, ci.sentAt
      """)
  List<ClientInvoiceQueueRow> findQueueRows(
      @Param("tenantId") UUID tenantId, @Param("status") ClientInvoiceStatus status);
}

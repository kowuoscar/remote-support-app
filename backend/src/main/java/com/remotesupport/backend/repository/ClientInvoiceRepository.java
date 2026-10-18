package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import java.time.Instant;
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

  /**
   * The summed stored line total of a tenant's Client Invoices of one billing month in one of
   * {@code statuses}, per currency. Meant for statuses past the send, whose lines are stored.
   */
  @Query(
      """
      select new com.remotesupport.backend.repository.CurrencyTotalRow(ci.currency, sum(l.amount))
      from ClientInvoice ci
      join ClientInvoiceLine l on l.clientInvoice = ci
      where ci.tenant.id = :tenantId and ci.billingMonth = :billingMonth and ci.status in :statuses
      group by ci.currency
      """)
  List<CurrencyTotalRow> sumTotalsByCurrency(
      @Param("tenantId") UUID tenantId,
      @Param("billingMonth") LocalDate billingMonth,
      @Param("statuses") Collection<ClientInvoiceStatus> statuses);

  /**
   * The {@code DRAFT} invoices an Agent's Contracts have had sent back, oldest send-back first, any
   * Contract and billing month. A resent invoice is {@code SENT} again, so it is no longer here.
   */
  @Query(
      """
      select new com.remotesupport.backend.repository.ClientInvoiceSentBackRow(
          ci.id, c.id, cl.name, a.country, ci.billingMonth, ci.currency,
          ci.sentBackAt, ci.sentBackReason)
      from ClientInvoice ci
      join ci.contract c
      join c.client cl
      join c.agent a
      where ci.tenant.id = :tenantId and a.id = :agentId
        and ci.status = com.remotesupport.backend.domain.ClientInvoiceStatus.DRAFT
        and ci.sentBackAt is not null
      order by ci.sentBackAt asc, ci.id asc
      """)
  List<ClientInvoiceSentBackRow> findSentBackRows(
      @Param("tenantId") UUID tenantId, @Param("agentId") UUID agentId);

  /**
   * The Agent's month, as a JPQL predicate over alias {@code ci} (params {@code agentId},
   * {@code billingMonth}): one definition for the month send's locked read and its sent date.
   */
  String AGENTS_MONTH =
      "ci.billingMonth = :billingMonth"
          + " and ci.contract.id in (select c.id from Contract c where c.agent.id = :agentId)";

  /**
   * Every Client Invoice of an Agent's Contracts for one billing month, re-read under a row lock
   * and ordered by id, so every month send locks in one order: the locked rows are the month
   * send's batch. The Agent's Contracts are a subquery, so no Contract row is locked.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select ci from ClientInvoice ci where " + AGENTS_MONTH + " order by ci.id")
  List<ClientInvoice> findMonthForUpdate(
      @Param("agentId") UUID agentId, @Param("billingMonth") LocalDate billingMonth);

  /**
   * When an Agent's month was first sent: the earliest {@code firstSentAt} over the month's
   * invoices, or {@code null} while none has been sent.
   */
  @Query("select min(ci.firstSentAt) from ClientInvoice ci where " + AGENTS_MONTH)
  Instant monthSentAt(@Param("agentId") UUID agentId, @Param("billingMonth") LocalDate billingMonth);
}

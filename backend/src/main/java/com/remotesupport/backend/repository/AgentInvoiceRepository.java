package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentInvoiceRepository extends JpaRepository<AgentInvoice, UUID> {

  Optional<AgentInvoice> findByAgentIdAndBillingMonth(UUID agentId, LocalDate billingMonth);

  Optional<AgentInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * The invoice re-read under a row lock (edit-client-invoice-lines spec, "Local Support Fees across
   * the Agent Invoice's lifecycle"): send, approve, mark-paid, override and a Client Invoice edit's
   * follow all take it, so none writes from a stale read. Always taken after a Client Invoice lock,
   * never before.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select ai from AgentInvoice ai where ai.id = :id")
  Optional<AgentInvoice> findByIdForUpdate(@Param("id") UUID id);

  /** The Agent's invoice for a month under a row lock, as {@link #findByIdForUpdate}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select ai from AgentInvoice ai where ai.agent.id = :agentId and ai.billingMonth = :billingMonth")
  Optional<AgentInvoice> findByAgentIdAndBillingMonthForUpdate(
      @Param("agentId") UUID agentId, @Param("billingMonth") LocalDate billingMonth);

  /** Every Agent Invoice in one of {@code statuses} for a tenant, any Agent and billing month. */
  @Query(
      """
      select new com.remotesupport.backend.repository.AgentInvoiceQueueRow(
          ai.id, ai.status, ai.billingMonth, a.id, a.name, ai.currency,
          ai.snapshotLocalSupportFees + ai.snapshotSalary
              + ai.snapshotRolloutAdvanceRepayment + ai.snapshotRolloutAdvanceNewAdvance,
          ai.sentAt, ai.approvedAt)
      from AgentInvoice ai
      join ai.agent a
      where ai.tenant.id = :tenantId and ai.status in :statuses
      """)
  List<AgentInvoiceQueueRow> findQueueRows(
      @Param("tenantId") UUID tenantId, @Param("statuses") Collection<AgentInvoiceStatus> statuses);
}

package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<Request, UUID> {

  List<Request> findByContractIdOrderByCreatedAtAsc(UUID contractId);

  Optional<Request> findByIdAndContractId(UUID id, UUID contractId);

  /**
   * A Request addressed by its own identity, scoped to the caller's tenant — the shape {@code
   * RequestByIdController} needs (manager-approves-requests ticket), mirroring {@code
   * AgentInvoiceRepository#findByIdAndTenantId}: an unknown or other-tenant id is the same 404,
   * never leaking which one.
   */
  Optional<Request> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Every Request at {@code status} across the whole tenant, for the Manager's Pending Requests
   * page (spec.md Manager approval) — the Request-side equivalent of {@code
   * ClientInvoiceRepository#findQueueRows}/{@code AgentInvoiceRepository#findQueueRows}. Joins to
   * the Contract's Client and Agent, and the Tester's own login, for the row's display fields; no
   * aggregation needed (unlike the invoice queues), so this reads straight off {@link Request}
   * rather than through a constructor-expression projection.
   */
  @Query(
      """
      select r from Request r
      join fetch r.contract c
      join fetch c.client
      join fetch c.agent
      join fetch r.tester t
      join fetch t.user
      where r.tenant.id = :tenantId and r.status = :status
      """)
  List<Request> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") RequestStatus status);
}

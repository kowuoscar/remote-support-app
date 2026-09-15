package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A support action raised against one {@link Contract} (spec.md Solution). Always attributed to
 * the {@link Tester} it is raised on behalf of — {@code tester} models "whose Request this is",
 * not "who clicked submit". {@code raisedByUser} is who actually clicked submit: the Tester's own
 * login for a Tester-authored Request, or the Agent's login for one logged proactively on a
 * Tester's behalf (agent-request-fulfillment ticket AC 3). {@code agentAuthored} makes that
 * provenance directly queryable (fee-logging-and-provisioning ticket cares whether a Request was
 * proactive) without joining back through {@code raisedByUser} to its role.
 *
 * <p>Deliberately has no reference to a specific Smartphone/SIM Card yet. A Repair or SIM Swap
 * will eventually need one (fee-logging-and-provisioning ticket: "retiring the unit it
 * replaces"), but which Fleet table it points to is ambiguous today (Smartphone vs. SIM Card) and
 * no acceptance criterion here needs it — a nullable FK is trivial for that ticket to add later,
 * so it isn't guessed at now.
 */
@Entity
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
public class Request {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tester_id", nullable = false)
  private Tester tester;

  @ManyToOne(optional = false)
  @JoinColumn(name = "raised_by_user_id", nullable = false)
  private User raisedByUser;

  @Column(name = "agent_authored", nullable = false)
  private boolean agentAuthored;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RequestType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RequestStatus status;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

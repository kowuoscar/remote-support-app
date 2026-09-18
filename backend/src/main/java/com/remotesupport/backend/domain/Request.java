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
 * <p>{@code replacesSmartphoneId}/{@code replacesSimCardId} (fee-logging-and-provisioning ticket):
 * which existing Fleet unit, if any, this Request retires when it completes. Only ever set for a
 * {@code PROVISION_SMARTPHONE}/{@code PROVISION_SIM} Request respectively, and only when the Agent
 * named a unit being replaced — null for a first-time provisioning with nothing to retire. Plain
 * {@code UUID} columns rather than a {@code @ManyToOne} to {@link Smartphone}/{@link SimCard}: a
 * Request never needs to navigate to the replaced unit as an object, only to know its id (the
 * provisioning side-effect looks the row up directly by id + Contract, exactly like every other
 * Fleet-status write), so the extra association mapping would buy nothing. The database still
 * enforces referential integrity via a foreign key (V10 migration). Modeled as two nullable
 * columns rather than one polymorphic reference: a Request's {@code type} already disambiguates
 * which one (if either) can be set, so a single "kind + id" column would only add a second field
 * to keep in sync for no real savings.
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

  /**
   * Free-text detail given at submission (request-types-and-flow spec, Details at submission;
   * other-replaces-repair ticket): optional for every type except {@link RequestType#OTHER},
   * where {@link com.remotesupport.backend.web.RequestController} and {@link
   * com.remotesupport.backend.web.FeeController} both refuse a blank one before saving — the same
   * validation both the Tester and the Agent-proactive paths call. Nullable at the database level
   * because every other type leaves it empty far more often than not.
   */
  @Column private String description;

  @Column(name = "replaces_smartphone_id")
  private UUID replacesSmartphoneId;

  @Column(name = "replaces_sim_card_id")
  private UUID replacesSimCardId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

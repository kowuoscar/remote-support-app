package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

  /**
   * The Smartphone a {@link RequestType#REBOOT} Request asks to reboot (request-types-and-flow
   * spec, Details at submission; reboot-and-topup-details ticket). Set at submission by {@link
   * com.remotesupport.backend.web.requestdetails.RebootRequestDetailsHandler}, on both the
   * Tester and the Agent-proactive path. Null for every other type, and for a Reboot Request that
   * existed before this ticket. An actual {@code @ManyToOne} (unlike {@code replacesSmartphoneId}
   * above) because the Requests lists need to show the target's model on every row (AC:
   * "summarise the details"), not just its id.
   */
  @ManyToOne
  @JoinColumn(name = "target_smartphone_id")
  private Smartphone targetSmartphone;

  /**
   * The SIM Card a {@link RequestType#TOPUP} Request asks to top up — same shape and rationale as
   * {@code targetSmartphone} above, set by {@code TopupRequestDetailsHandler}.
   */
  @ManyToOne
  @JoinColumn(name = "target_sim_card_id")
  private SimCard targetSimCard;

  /**
   * The Topup Option a Topup Request asked for, when {@code targetSimCard}'s Carrier had an
   * active one at submission (request-types-and-flow spec, Details at submission: "otherwise a
   * description" — {@code description} above carries that fallback). Archiving the Option
   * afterwards leaves this Request's choice valid (spec.md: "archiving hides an entry from
   * pickers, it never invalidates a record that already uses it"). Completing this Request reads
   * it back to pre-fill the Fee amount and link the Fee to it (ticket AC), but never re-reads its
   * price at submission time beyond the one check that it belongs to this SIM Card's Carrier.
   */
  @ManyToOne
  @JoinColumn(name = "topup_option_id")
  private TopupOption topupOption;

  /**
   * The brand-and-model text a {@link RequestType#PROVISION_SMARTPHONE} Request asks for
   * (request-types-and-flow spec, Details at submission; provision-request-details ticket). Set
   * at submission by {@link com.remotesupport.backend.web.requestdetails.ProvisionSmartphoneRequestDetailsHandler}.
   * Null for every other type, and for a Provision Smartphone Request that existed before this
   * ticket — completing one falls back to the previous full form (ticket AC).
   */
  @Column(name = "requested_model")
  private String requestedModel;

  /**
   * The flavor a {@link RequestType#PROVISION_SIM} Request asks for, together with {@code
   * requestedCarrier}/{@code requestedPostpaidPlan} below (provision-request-details ticket). Set
   * by {@link com.remotesupport.backend.web.requestdetails.ProvisionSimRequestDetailsHandler}.
   * Null for every other type, and for a Provision SIM Request that existed before this ticket.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "requested_flavor")
  private SimCardFlavor requestedFlavor;

  /**
   * The Carrier a Provision SIM Request asks for — an active Carrier of the Contract's Country at
   * submission (spec.md: "archiving hides an entry from pickers, it never invalidates a record
   * that already uses it" — this Request's choice stays valid even once archived).
   */
  @ManyToOne
  @JoinColumn(name = "requested_carrier_id")
  private Carrier requestedCarrier;

  /**
   * The Postpaid Plan a postpaid Provision SIM Request asks for, when {@code requestedFlavor} is
   * {@code POSTPAID} — null for a prepaid Provision SIM Request. Completing the Request copies its
   * price as the new SIM Card's monthly fee, exactly like {@code SimCardFactory} already does for
   * every other SIM-creation path (carrier-catalog spec).
   */
  @ManyToOne
  @JoinColumn(name = "requested_postpaid_plan_id")
  private PostpaidPlan requestedPostpaidPlan;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /**
   * A one-time, non-persisted note set by a completion effect to tell the Agent something about
   * what just happened that isn't otherwise visible on the Request (provision-request-details
   * ticket AC: "the SIM Card is installed in the target Smartphone when one was named and it has
   * room; otherwise it is added uninstalled and the Agent is told"). Never stored — it only rides
   * back on the one {@code RequestResponse} the completion PATCH itself returns.
   */
  @Transient private String completionNote;
}

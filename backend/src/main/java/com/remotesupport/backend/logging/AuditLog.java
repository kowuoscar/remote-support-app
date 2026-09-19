package com.remotesupport.backend.logging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured audit trail for entity-creation actions (manager-entity-setup ticket
 * Observability: "audit log entry (actor, tenant, entity, action) on every entity creation").
 * Actor and tenant already ride the MDC via {@link RequestLoggingFilter}, populated before any
 * controller runs; this adds entity + action + entity id to that same vocabulary rather than
 * inventing a parallel logging mechanism, and repeats actor/tenant explicitly in the message
 * text so a log-content assertion doesn't depend on the console pattern's MDC placeholders.
 */
public final class AuditLog {

  private static final Logger log = LoggerFactory.getLogger("AUDIT");

  private AuditLog() {}

  public static void created(String entity, UUID entityId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=CREATE entity={} entityId={} actorUserId={} tenantId={}",
        entity,
        entityId,
        actorUserId,
        tenantId);
  }

  /**
   * A login created for an Agent (agent-login-on-creation spec user story 13): who gave which
   * Agent access, through which User. Never carries the password.
   */
  public static void agentLoginCreated(UUID agentId, UUID userId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=AGENT_LOGIN_CREATED entity=User entityId={} agentId={} actorUserId={} tenantId={}",
        userId,
        agentId,
        actorUserId,
        tenantId);
  }

  /**
   * Fleet status transitions (fleet-management ticket Observability: "status-change events
   * logged with resource id, old/new status, actor").
   */
  public static void statusChanged(
      String entity, UUID entityId, String oldStatus, String newStatus, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=STATUS_CHANGE entity={} entityId={} oldStatus={} newStatus={} actorUserId={} tenantId={}",
        entity,
        entityId,
        oldStatus,
        newStatus,
        actorUserId,
        tenantId);
  }

  /**
   * A Request's submission (tester-request-submission ticket Observability: "Request-submitted
   * event logged with Contract, Request type, actor"). {@code descriptionGiven} is whether the
   * submitter gave an optional description — never its text (request-types-and-flow spec,
   * Details at submission; other-replaces-repair ticket Observability). {@code
   * targetSmartphoneId}/{@code targetSimCardId}/{@code topupOptionId} (reboot-and-topup-details
   * ticket Observability: "The Request submitted and logged audit events carry the target unit id
   * and the Topup Option id") are null for every type but the one that set them.
   */
  public static void requestSubmitted(
      UUID requestId,
      UUID contractId,
      String requestType,
      boolean descriptionGiven,
      UUID targetSmartphoneId,
      UUID targetSimCardId,
      UUID topupOptionId,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=REQUEST_SUBMITTED entity=Request entityId={} contractId={} requestType={} "
            + "descriptionGiven={} targetSmartphoneId={} targetSimCardId={} topupOptionId={} "
            + "actorUserId={} tenantId={}",
        requestId,
        contractId,
        requestType,
        descriptionGiven,
        targetSmartphoneId,
        targetSimCardId,
        topupOptionId,
        actorUserId,
        tenantId);
  }

  /**
   * An Agent logging a Request proactively, on a Tester's behalf (agent-request-fulfillment
   * ticket Observability), distinct from {@link #requestSubmitted} so a log scan can tell a
   * Tester-authored submission from an Agent-authored one, and see which starting status the
   * Agent chose (Submitted or immediately Completed). {@code descriptionGiven} is whether an
   * optional description was given — never its text (other-replaces-repair ticket Observability).
   * {@code targetSmartphoneId}/{@code targetSimCardId}/{@code topupOptionId}
   * (reboot-and-topup-details ticket Observability) are null for every type but the one that set
   * them.
   */
  public static void requestLoggedByAgent(
      UUID requestId,
      UUID contractId,
      String requestType,
      String startingStatus,
      boolean descriptionGiven,
      UUID targetSmartphoneId,
      UUID targetSimCardId,
      UUID topupOptionId,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=REQUEST_LOGGED_BY_AGENT entity=Request entityId={} contractId={} "
            + "requestType={} startingStatus={} descriptionGiven={} targetSmartphoneId={} "
            + "targetSimCardId={} topupOptionId={} actorUserId={} tenantId={}",
        requestId,
        contractId,
        requestType,
        startingStatus,
        descriptionGiven,
        targetSmartphoneId,
        targetSimCardId,
        topupOptionId,
        actorUserId,
        tenantId);
  }

  /**
   * An Agent logging a Fee against a Request (fee-logging-and-provisioning ticket Observability:
   * "Fee-logged ... events logged with Contract, Request id, amount, actor"). {@code topupOptionId}
   * is the Topup Option a Topup Fee was bought from, or {@code null} (topup-fee-from-option ticket).
   */
  public static void feeLogged(
      UUID feeId,
      UUID contractId,
      UUID requestId,
      String feeType,
      BigDecimal amount,
      UUID topupOptionId,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=FEE_LOGGED entity=Fee entityId={} contractId={} requestId={} feeType={} "
            + "amount={} topupOptionId={} actorUserId={} tenantId={}",
        feeId,
        contractId,
        requestId,
        feeType,
        amount,
        topupOptionId,
        actorUserId,
        tenantId);
  }

  /**
   * A new Smartphone/SIM Card added to a Contract's Fleet as the side-effect of completing a
   * Provision Request (fee-logging-and-provisioning ticket Observability: "Fleet-item-provisioned
   * ... events logged with Contract, Request id ... actor"). Distinct from {@link #created} so a
   * log scan can tell a Manager's direct Fleet addition from one that happened as a byproduct of
   * fulfilling a Request.
   */

  /**
   * A Manager adding a Smartphone to a Fleet directly: {@link #created}'s event, plus the
   * Contract and the Owner (smartphone-owner-and-optional-serial ticket Observability: "Owner
   * added to the Smartphone created ... events").
   */
  public static void smartphoneCreated(
      UUID smartphoneId, UUID contractId, String owner, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=CREATE entity=Smartphone entityId={} contractId={} owner={} actorUserId={} tenantId={}",
        smartphoneId,
        contractId,
        owner,
        actorUserId,
        tenantId);
  }

  /**
   * {@link #fleetItemProvisioned}'s event for a Smartphone, plus the Owner -- always {@code
   * COMPANY} (smartphone-owner-and-optional-serial ticket Observability: "Owner added to ... the
   * provisioned events").
   */
  public static void smartphoneProvisioned(
      UUID smartphoneId, UUID contractId, UUID requestId, String owner, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=FLEET_ITEM_PROVISIONED entity=Smartphone entityId={} contractId={} requestId={} "
            + "owner={} actorUserId={} tenantId={}",
        smartphoneId,
        contractId,
        requestId,
        owner,
        actorUserId,
        tenantId);
  }

  /**
   * A Smartphone's serial set or changed from the Fleet page (smartphone-owner-and-optional-serial
   * ticket Observability: "Audit event for a serial set or changed: Smartphone id, actor,
   * tenant").
   */
  public static void smartphoneSerialChanged(
      UUID smartphoneId, String oldSerial, String newSerial, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=SERIAL_CHANGED entity=Smartphone entityId={} oldSerial={} newSerial={} "
            + "actorUserId={} tenantId={}",
        smartphoneId,
        oldSerial,
        newSerial,
        actorUserId,
        tenantId);
  }

  /**
   * A Manager changing an Agent's standing salary or standing Rollout Advance
   * (agent-standing-amounts-and-invoice-generation ticket Observability: "standing-amount change
   * events logged with Agent id, old/new value, effective month, actor (Manager)"). {@code
   * effectiveMonth} is always the invoice month *after* the one in progress when the change was
   * made — see {@link com.remotesupport.backend.domain.AgentStandingAmount}'s Javadoc.
   */
  public static void standingAmountChanged(
      UUID agentId,
      String amountType,
      BigDecimal oldAmount,
      BigDecimal newAmount,
      LocalDate effectiveMonth,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=STANDING_AMOUNT_CHANGED entity=Agent entityId={} amountType={} oldAmount={} "
            + "newAmount={} effectiveMonth={} actorUserId={} tenantId={}",
        agentId,
        amountType,
        oldAmount,
        newAmount,
        effectiveMonth,
        actorUserId,
        tenantId);
  }

  /**
   * A Manager overriding one Agent Invoice's Salary or Rollout Advance (new-advance) line at
   * approval time (agent-invoice-submission-and-approval ticket Observability: "override events
   * logged with Agent Invoice id, actor, old/new value"). Distinct from {@link #statusChanged} —
   * an override never changes the invoice's status — and from {@link #standingAmountChanged},
   * which is for the Agent's standing rate itself; this is strictly a one-invoice edit and never
   * touches {@code agent_standing_amounts} (see {@code AgentInvoiceController#override}'s
   * Javadoc).
   */
  public static void agentInvoiceOverridden(
      UUID invoiceId, String field, BigDecimal oldValue, BigDecimal newValue, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=AGENT_INVOICE_OVERRIDDEN entity=AgentInvoice entityId={} field={} oldValue={} "
            + "newValue={} actorUserId={} tenantId={}",
        invoiceId,
        field,
        oldValue,
        newValue,
        actorUserId,
        tenantId);
  }

  /**
   * Carrier catalog changes (agent-maintains-carriers ticket Observability: "Carrier created,
   * renamed and archived: Carrier id, Country, actor, tenant, and the old and new name on a
   * rename"), so a surprising catalog entry can be traced back to its author.
   */
  public static void carrierCreated(
      UUID carrierId, String country, String name, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=CARRIER_CREATED entity=Carrier entityId={} country={} name={} actorUserId={} tenantId={}",
        carrierId,
        country,
        name,
        actorUserId,
        tenantId);
  }

  public static void carrierRenamed(
      UUID carrierId, String country, String oldName, String newName, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=CARRIER_RENAMED entity=Carrier entityId={} country={} oldName={} newName={} "
            + "actorUserId={} tenantId={}",
        carrierId,
        country,
        oldName,
        newName,
        actorUserId,
        tenantId);
  }

  public static void carrierArchived(UUID carrierId, String country, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=CARRIER_ARCHIVED entity=Carrier entityId={} country={} actorUserId={} tenantId={}",
        carrierId,
        country,
        actorUserId,
        tenantId);
  }

  /**
   * Topup Option and Postpaid Plan changes (topup-options-and-postpaid-plans ticket Observability:
   * "entry id, Carrier id, actor, tenant, and the old and new name and price on an edit").
   * {@code actionPrefix} is {@code TOPUP_OPTION} or {@code POSTPAID_PLAN}; {@code entity} names the
   * matching domain type.
   */
  public static void carrierOfferCreated(
      String actionPrefix,
      String entity,
      UUID entryId,
      UUID carrierId,
      String name,
      BigDecimal price,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action={}_CREATED entity={} entityId={} carrierId={} name={} price={} actorUserId={} "
            + "tenantId={}",
        actionPrefix,
        entity,
        entryId,
        carrierId,
        name,
        price,
        actorUserId,
        tenantId);
  }

  /**
   * A Manager adding a SIM Card to a Fleet: {@link #created}'s event, plus the Contract and the
   * Carrier it names (sim-card-carrier ticket Observability).
   */
  public static void simCardCreated(
      UUID simCardId,
      UUID contractId,
      UUID carrierId,
      UUID postpaidPlanId,
      BigDecimal monthlyFeeAmount,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=CREATE entity=SimCard entityId={} contractId={} carrierId={} postpaidPlanId={} "
            + "monthlyFeeAmount={} actorUserId={} tenantId={}",
        simCardId,
        contractId,
        carrierId,
        postpaidPlanId,
        monthlyFeeAmount,
        actorUserId,
        tenantId);
  }

  public static void carrierOfferEdited(
      String actionPrefix,
      String entity,
      UUID entryId,
      UUID carrierId,
      String oldName,
      String newName,
      BigDecimal oldPrice,
      BigDecimal newPrice,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action={}_EDITED entity={} entityId={} carrierId={} oldName={} newName={} oldPrice={} "
            + "newPrice={} actorUserId={} tenantId={}",
        actionPrefix,
        entity,
        entryId,
        carrierId,
        oldName,
        newName,
        oldPrice,
        newPrice,
        actorUserId,
        tenantId);
  }

  public static void carrierOfferArchived(
      String actionPrefix, String entity, UUID entryId, UUID carrierId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action={}_ARCHIVED entity={} entityId={} carrierId={} actorUserId={} tenantId={}",
        actionPrefix,
        entity,
        entryId,
        carrierId,
        actorUserId,
        tenantId);
  }

  /**
   * {@link #fleetItemProvisioned}'s event for a SIM Card, plus the Carrier it names
   * (sim-card-carrier ticket Observability).
   */
  public static void simCardProvisioned(
      UUID simCardId,
      UUID contractId,
      UUID requestId,
      UUID carrierId,
      UUID postpaidPlanId,
      BigDecimal monthlyFeeAmount,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=FLEET_ITEM_PROVISIONED entity=SimCard entityId={} contractId={} requestId={} "
            + "carrierId={} postpaidPlanId={} monthlyFeeAmount={} actorUserId={} tenantId={}",
        simCardId,
        contractId,
        requestId,
        carrierId,
        postpaidPlanId,
        monthlyFeeAmount,
        actorUserId,
        tenantId);
  }

  /**
   * A SIM Card installed into a Smartphone (sim-installed-in-smartphone ticket Observability:
   * "SIM Card id, Smartphone id, actor, tenant, and the Request id when a Request caused it").
   * {@code requestId} is {@code null} for a Fleet-page action with no Request behind it.
   */
  public static void simCardInstalled(
      UUID simCardId, UUID smartphoneId, UUID requestId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=SIM_CARD_INSTALLED entity=SimCard entityId={} smartphoneId={} requestId={} "
            + "actorUserId={} tenantId={}",
        simCardId,
        smartphoneId,
        requestId,
        actorUserId,
        tenantId);
  }

  /**
   * A SIM Card uninstalled from a Smartphone, whether by an explicit clear/move or as the
   * cascading side-effect of retiring the Smartphone or the SIM Card itself
   * (sim-installed-in-smartphone ticket Observability). {@code requestId} is {@code null} for a
   * Fleet-page action with no Request behind it.
   */
  public static void simCardUninstalled(
      UUID simCardId, UUID smartphoneId, UUID requestId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=SIM_CARD_UNINSTALLED entity=SimCard entityId={} smartphoneId={} requestId={} "
            + "actorUserId={} tenantId={}",
        simCardId,
        smartphoneId,
        requestId,
        actorUserId,
        tenantId);
  }

  /**
   * Completing a Replace Smartphone/SIM Request (replace-requests ticket Observability: "A
   * unit-replaced audit event: Request id, retired unit id, new unit id, actor, tenant"). {@code
   * entity} is {@code Smartphone} or {@code SimCard}, matching {@link #statusChanged}'s own
   * vocabulary. Distinct from {@link #fleetItemProvisioned} — a replacement always retires one
   * unit too, which this event ties directly to the new one it was traded for.
   */
  public static void unitReplaced(
      String entity, UUID requestId, UUID retiredUnitId, UUID newUnitId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=UNIT_REPLACED entity={} requestId={} retiredUnitId={} newUnitId={} "
            + "actorUserId={} tenantId={}",
        entity,
        requestId,
        retiredUnitId,
        newUnitId,
        actorUserId,
        tenantId);
  }

  /**
   * A Manager approving a Pending Approval Request, moving it to Submitted
   * (manager-approves-requests ticket Observability: "Request approved and rejected: Request id,
   * actor, tenant, and that a reason was given" — approving never carries one, so this event omits
   * the field entirely rather than always logging {@code reasonGiven=false}).
   */
  public static void requestApproved(UUID requestId, UUID contractId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=REQUEST_APPROVED entity=Request entityId={} contractId={} actorUserId={} tenantId={}",
        requestId,
        contractId,
        actorUserId,
        tenantId);
  }

  /**
   * A Manager rejecting a Pending Approval Request, moving it to Rejected — {@code reasonGiven} is
   * always {@code true} (a reject without one is refused before this is ever logged), kept as an
   * explicit field to match the ticket's Observability wording and mirror {@link
   * #requestSubmitted}'s own {@code descriptionGiven}, never logging the reason's text itself.
   */
  public static void requestRejected(
      UUID requestId, UUID contractId, boolean reasonGiven, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=REQUEST_REJECTED entity=Request entityId={} contractId={} reasonGiven={} "
            + "actorUserId={} tenantId={}",
        requestId,
        contractId,
        reasonGiven,
        actorUserId,
        tenantId);
  }

  /**
   * One unit named on a completed Return (return-client-owned-smartphones ticket Observability:
   * "A unit-returned audit event per unit: Request id, unit id, Disposition, actor, tenant").
   * {@code entity} is {@code Smartphone} or {@code SimCard}, matching {@link #statusChanged}'s own
   * vocabulary; {@code unitId} is that unit's own id, not the {@link
   * com.remotesupport.backend.domain.ReturnedUnit} row's.
   */
  public static void unitReturned(
      String entity, UUID unitId, UUID requestId, String disposition, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=UNIT_RETURNED entity={} entityId={} requestId={} disposition={} "
            + "actorUserId={} tenantId={}",
        entity,
        unitId,
        requestId,
        disposition,
        actorUserId,
        tenantId);
  }

  public static void fleetItemProvisioned(
      String entity, UUID entityId, UUID contractId, UUID requestId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=FLEET_ITEM_PROVISIONED entity={} entityId={} contractId={} requestId={} "
            + "actorUserId={} tenantId={}",
        entity,
        entityId,
        contractId,
        requestId,
        actorUserId,
        tenantId);
  }
}

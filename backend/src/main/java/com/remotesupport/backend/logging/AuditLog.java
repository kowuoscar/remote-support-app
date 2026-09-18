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
   * event logged with Contract, Request type, actor").
   */
  public static void requestSubmitted(
      UUID requestId, UUID contractId, String requestType, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=REQUEST_SUBMITTED entity=Request entityId={} contractId={} requestType={} actorUserId={} tenantId={}",
        requestId,
        contractId,
        requestType,
        actorUserId,
        tenantId);
  }

  /**
   * An Agent logging a Request proactively, on a Tester's behalf (agent-request-fulfillment
   * ticket Observability), distinct from {@link #requestSubmitted} so a log scan can tell a
   * Tester-authored submission from an Agent-authored one, and see which starting status the
   * Agent chose (Submitted or immediately Completed).
   */
  public static void requestLoggedByAgent(
      UUID requestId,
      UUID contractId,
      String requestType,
      String startingStatus,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=REQUEST_LOGGED_BY_AGENT entity=Request entityId={} contractId={} "
            + "requestType={} startingStatus={} actorUserId={} tenantId={}",
        requestId,
        contractId,
        requestType,
        startingStatus,
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
      UUID simCardId, UUID contractId, UUID carrierId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=CREATE entity=SimCard entityId={} contractId={} carrierId={} actorUserId={} tenantId={}",
        simCardId,
        contractId,
        carrierId,
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
      UUID simCardId, UUID contractId, UUID requestId, UUID carrierId, UUID actorUserId, UUID tenantId) {
    log.info(
        "audit action=FLEET_ITEM_PROVISIONED entity=SimCard entityId={} contractId={} requestId={} "
            + "carrierId={} actorUserId={} tenantId={}",
        simCardId,
        contractId,
        requestId,
        carrierId,
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

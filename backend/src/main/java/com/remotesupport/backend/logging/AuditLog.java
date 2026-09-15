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
   * "Fee-logged ... events logged with Contract, Request id, amount, actor").
   */
  public static void feeLogged(
      UUID feeId,
      UUID contractId,
      UUID requestId,
      String feeType,
      BigDecimal amount,
      UUID actorUserId,
      UUID tenantId) {
    log.info(
        "audit action=FEE_LOGGED entity=Fee entityId={} contractId={} requestId={} feeType={} "
            + "amount={} actorUserId={} tenantId={}",
        feeId,
        contractId,
        requestId,
        feeType,
        amount,
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

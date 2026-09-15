package com.remotesupport.backend.logging;

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
}

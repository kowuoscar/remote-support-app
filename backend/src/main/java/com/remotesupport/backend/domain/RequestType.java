package com.remotesupport.backend.domain;

/**
 * The six support actions a Tester can raise, or an Agent can log on a Tester's behalf (spec.md
 * Solution: "Request: a support action ... raised by a Tester or logged by an Agent"). Whether a
 * given type accepts a Fee (fee-logging-and-provisioning ticket) is a downstream concern, not
 * modeled here.
 *
 * <p>{@code OTHER} replaces the former {@code REPAIR} (request-types-and-flow spec, Types;
 * other-replaces-repair ticket): a free-text Request for support no other type covers, described
 * by its submitter via {@link com.remotesupport.backend.domain.Request#getDescription()}, which is
 * required for this type and optional for every other. The V31 migration rewrites every existing
 * {@code REPAIR} row to {@code OTHER}, defaulting its description to "Repair" where none was
 * given, so history keeps its meaning. {@code replace-requests} adds {@code REPLACE_SMARTPHONE}/
 * {@code REPLACE_SIM} later in this same feature — not yet part of this enum.
 */
public enum RequestType {
  REBOOT,
  TOPUP,
  SIM_SWAP,
  PROVISION_SMARTPHONE,
  PROVISION_SIM,
  OTHER
}

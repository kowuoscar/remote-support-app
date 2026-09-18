package com.remotesupport.backend.domain;

/**
 * The eight support actions a Tester can raise, or an Agent can log on a Tester's behalf (spec.md
 * Solution: "Request: a support action ... raised by a Tester or logged by an Agent"). Whether a
 * given type accepts a Fee (fee-logging-and-provisioning ticket) is a downstream concern, not
 * modeled here.
 *
 * <p>{@code OTHER} replaces the former {@code REPAIR} (request-types-and-flow spec, Types;
 * other-replaces-repair ticket): a free-text Request for support no other type covers, described
 * by its submitter via {@link com.remotesupport.backend.domain.Request#getDescription()}, which is
 * required for this type and optional for every other. The V31 migration rewrites every existing
 * {@code REPAIR} row to {@code OTHER}, defaulting its description to "Repair" where none was
 * given, so history keeps its meaning.
 *
 * <p>{@code REPLACE_SMARTPHONE}/{@code REPLACE_SIM} (replace-requests ticket): swap out one named
 * unit of a Contract's Fleet for a new one — distinct from {@code PROVISION_SMARTPHONE}/{@code
 * PROVISION_SIM}, which only ever mean a net-new unit with nothing to retire (spec.md Solution:
 * "Provision no longer names a unit to replace — that is what Replace is for").
 */
public enum RequestType {
  REBOOT,
  TOPUP,
  SIM_SWAP,
  PROVISION_SMARTPHONE,
  PROVISION_SIM,
  REPLACE_SMARTPHONE,
  REPLACE_SIM,
  OTHER
}

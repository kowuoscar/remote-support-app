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
 *
 * <p>{@code RETURN} (returns-and-agent-stock spec, Return type; return-client-owned-smartphones
 * ticket): names one or more Smartphones and SIM Cards leaving a Contract's Fleet, each with its
 * own {@link Disposition}. Never carries a Fee (see {@link FeeType}, which structurally excludes
 * it exactly like {@code REBOOT} and {@code SIM_SWAP}).
 */
public enum RequestType {
  REBOOT,
  TOPUP,
  SIM_SWAP,
  PROVISION_SMARTPHONE,
  PROVISION_SIM,
  REPLACE_SMARTPHONE,
  REPLACE_SIM,
  OTHER,
  RETURN;

  /**
   * Whether this type always starts at {@link RequestStatus#PENDING_APPROVAL}, whoever raises it
   * (request-types-and-flow spec, Lifecycle: "Provision Smartphone, Provision SIM, Replace
   * Smartphone and Replace SIM always start at Pending Approval"; manager-approves-requests
   * ticket). The four types this names are exactly the ones that add a net-new unit or retire one
   * — a real spend the Company Manager agrees to before it happens.
   *
   * <p>{@code RETURN} is {@code false} here even though a Return holding a company-owned unit does
   * need approval (spec.md Solution: "A Return holding at least one company-owned unit ... starts
   * at Pending Approval") — that depends on a Return's own *content*, not its type alone, unlike
   * every type this method already names, so it's decided at the content level instead, by {@code
   * ReturnApprovalHandler} ({@code web.requestapproval} package, manager-decides-return-disposition
   * ticket; moved out of {@code RequestController} into this per-type seam by the feature's
   * finisher pass), which checks the named units' ownership directly, before/alongside {@code
   * ReturnRequestDetailsHandler} building them. {@link
   * com.remotesupport.backend.web.requestapproval.RequestApprovalValidator} is what every caller
   * actually asks — it falls back to this method for a type, like the four named above, whose
   * approval doesn't depend on content.
   */
  public boolean requiresApproval() {
    return switch (this) {
      case PROVISION_SMARTPHONE, PROVISION_SIM, REPLACE_SMARTPHONE, REPLACE_SIM -> true;
      case REBOOT, TOPUP, SIM_SWAP, OTHER, RETURN -> false;
    };
  }
}

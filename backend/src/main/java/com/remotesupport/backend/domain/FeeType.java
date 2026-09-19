package com.remotesupport.backend.domain;

/**
 * The six {@link RequestType}s that can carry a {@link Fee} (spec.md Solution: "The fee-capable
 * subset (FeeType) is Topup, Provision Smartphone, Provision SIM, Replace Smartphone, Replace
 * SIM, Other"; fee-logging-and-provisioning, other-replaces-repair and replace-requests tickets).
 * Deliberately narrower than {@code RequestType} rather than reusing it directly: {@code REBOOT}
 * and {@code SIM_SWAP} must never be constructible as a Fee's type, so the compiler rules them out
 * everywhere a {@code FeeType} is asked for, instead of relying on a runtime check alone. A
 * like-for-like SIM Swap never gets here; one that required provisioning a new physical SIM is
 * logged as a {@code PROVISION_SIM} Fee instead (see {@code Fee}'s Javadoc).
 */
public enum FeeType {
  TOPUP,
  PROVISION_SMARTPHONE,
  PROVISION_SIM,
  REPLACE_SMARTPHONE,
  REPLACE_SIM,
  OTHER;

  /** The {@link RequestType} this Fee type corresponds to 1:1 — the two enums share every name. */
  public RequestType toRequestType() {
    return RequestType.valueOf(name());
  }

  /** Whether an existing Request of this type is allowed to carry a Fee at all. */
  public static boolean requestTypeCanCarryFee(RequestType requestType) {
    return switch (requestType) {
      case TOPUP, PROVISION_SMARTPHONE, PROVISION_SIM, REPLACE_SMARTPHONE, REPLACE_SIM, OTHER -> true;
      // RETURN (returns-and-agent-stock spec: "It never carries a Fee") joins REBOOT and SIM_SWAP
      // here — postage isn't modelled (spec.md Non-goals).
      case REBOOT, SIM_SWAP, RETURN -> false;
    };
  }
}

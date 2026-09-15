package com.remotesupport.backend.domain;

/**
 * A Smartphone's lifecycle (spec.md Solution: "Active -> In Repair -> Active, or Retired when
 * replaced"). {@code RETIRED} is terminal — no further transition is valid.
 */
public enum SmartphoneStatus {
  ACTIVE,
  IN_REPAIR,
  RETIRED;

  /** Whether moving from this status directly to {@code target} is a valid Fleet transition. */
  public boolean canTransitionTo(SmartphoneStatus target) {
    return switch (this) {
      case ACTIVE -> target == IN_REPAIR || target == RETIRED;
      case IN_REPAIR -> target == ACTIVE || target == RETIRED;
      case RETIRED -> false;
    };
  }
}

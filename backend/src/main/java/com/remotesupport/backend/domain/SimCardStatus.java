package com.remotesupport.backend.domain;

/**
 * A SIM Card's lifecycle (spec.md Solution: "Status Active or Retired"). Unlike Smartphone,
 * Retired isn't terminal here — a SIM can be reactivated — so any change to the other value is a
 * valid transition; only a no-op (same status) is rejected.
 */
public enum SimCardStatus {
  ACTIVE,
  RETIRED;

  public boolean canTransitionTo(SimCardStatus target) {
    return target != this;
  }
}

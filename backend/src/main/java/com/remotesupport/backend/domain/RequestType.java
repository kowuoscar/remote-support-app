package com.remotesupport.backend.domain;

/**
 * The six support actions a Tester can raise, or an Agent can log on a Tester's behalf (spec.md
 * Solution: "Request: a support action ... raised by a Tester or logged by an Agent"). Whether a
 * given type accepts a Fee (fee-logging-and-provisioning ticket) is a downstream concern, not
 * modeled here.
 */
public enum RequestType {
  REBOOT,
  TOPUP,
  SIM_SWAP,
  PROVISION_SMARTPHONE,
  PROVISION_SIM,
  REPAIR
}

package com.remotesupport.backend.domain;

/**
 * A SIM Card's billing flavor (spec.md Solution / CONTEXT.md "Postpaid SIM / Prepaid SIM"): a
 * Postpaid SIM carries a fixed monthly fee that rolls into its Contract's base amount; a Prepaid
 * SIM carries none.
 */
public enum SimCardFlavor {
  POSTPAID,
  PREPAID
}

package com.remotesupport.backend.domain;

/**
 * The countries an Agent can be based in this iteration, each deterministically fixing one
 * {@link Currency} (spec.md: "a person hired in one country (which fixes their currency)"). A
 * fixed, small enum rather than a full ISO-3166 list — see {@code docs/adr/} for the trade-off.
 */
public enum Country {
  FRANCE(Currency.EUR),
  SPAIN(Currency.EUR),
  UNITED_KINGDOM(Currency.GBP),
  MEXICO(Currency.MXN),
  PHILIPPINES(Currency.PHP),
  UNITED_STATES(Currency.USD);

  private final Currency currency;

  Country(Currency currency) {
    this.currency = currency;
  }

  public Currency currency() {
    return currency;
  }
}

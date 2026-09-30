package com.remotesupport.backend.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Billing months are identified by their first day, in UTC. */
public final class BillingMonth {

  private BillingMonth() {}

  /** The first day of the current UTC month. */
  public static LocalDate current() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }
}

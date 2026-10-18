package com.remotesupport.backend.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** The {@code YYYY-MM} month of a request path, as the first day of that month. */
final class MonthPath {

  private MonthPath() {}

  /** {@code 2026-03} is 2026-03-01; anything else is an {@link InvalidRequestException} (400). */
  static LocalDate parse(String month) {
    try {
      return YearMonth.parse(month).atDay(1);
    } catch (DateTimeParseException e) {
      throw new InvalidRequestException("month must be YYYY-MM");
    }
  }
}

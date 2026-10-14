package com.remotesupport.backend.web;

/**
 * A Postpaid Plan {@link SimCardFactory#requireUsablePlan} refuses because it is not one of the
 * Carrier's ({@code PLAN_NOT_ON_CARRIER}) or is archived ({@code PLAN_ARCHIVED}). Still an
 * {@link InvalidRequestException}, so every existing caller keeps its plain 400; a Fleet
 * correction reads the {@code code} to show the right message at the field.
 */
public class UnusablePlanException extends InvalidRequestException {

  private final String code;

  public UnusablePlanException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}

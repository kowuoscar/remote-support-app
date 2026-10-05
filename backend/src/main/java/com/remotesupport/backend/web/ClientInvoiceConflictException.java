package com.remotesupport.backend.web;

/**
 * A 409 on a Client Invoice that carries which cause applies, so the client can tell it from the
 * plain "wrong status" conflict. {@link ClientInvoiceByIdController} renders it as {@code {code,
 * message}}, as {@link AgentController} does for {@link AgentLoginConflictException}.
 */
public class ClientInvoiceConflictException extends ConflictException {

  /** The machine-readable cause, sent to the client as {@code code}. */
  public enum Reason {
    /** A draft of a past billing month that was never sent: it could only be built from today's Fleet. */
    PAST_MONTH_DRAFT_NOT_SENDABLE
  }

  private final Reason reason;

  public ClientInvoiceConflictException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}

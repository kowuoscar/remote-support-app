package com.remotesupport.backend.web;

/**
 * A 409 from creating a Tester, carrying which of its two causes applies so the client can tell
 * them apart: the username is taken (pick another email) or the Client already has a primary
 * contact. {@link TesterController} renders it as {@code {code, message}} — the same shape
 * {@link AgentLoginConflictException} already gives the Agent path, which this mirrors
 * (globally-unique-usernames spec.md "The Tester path's asymmetry is fixed here, not left";
 * review finding F7 — top-level and public, matching the class it mirrors, not a nested
 * package-private type inside the controller).
 */
public class TesterConflictException extends ConflictException {

  /** The machine-readable cause, sent to the client as {@code code}. */
  public enum Reason {
    USERNAME_TAKEN,
    PRIMARY_CONTACT_EXISTS
  }

  private final Reason reason;

  public TesterConflictException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}

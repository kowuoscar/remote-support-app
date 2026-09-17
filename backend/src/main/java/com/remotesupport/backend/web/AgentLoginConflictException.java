package com.remotesupport.backend.web;

/**
 * A 409 from creating an Agent's login, carrying which of its two causes applies so the client
 * can tell them apart: the username is taken (pick another email) or the Agent already has a
 * login (the page is stale). {@link AgentController} renders it as {@code {code, message}}.
 */
public class AgentLoginConflictException extends ConflictException {

  /** The machine-readable cause, sent to the client as {@code code}. */
  public enum Reason {
    USERNAME_TAKEN,
    AGENT_ALREADY_HAS_LOGIN
  }

  private final Reason reason;

  public AgentLoginConflictException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}

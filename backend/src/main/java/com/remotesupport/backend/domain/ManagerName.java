package com.remotesupport.backend.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The one rule for a valid Manager name, applied by every path that writes one: surrounding
 * whitespace is trimmed, an empty result is refused ({@link Reason#NAME_BLANK}), and so is more
 * than {@value #MAX_LENGTH} characters after trimming ({@link Reason#NAME_TOO_LONG}). Inner
 * spacing and any script are kept as typed.
 */
public record ManagerName(String value) {

  public static final int MAX_LENGTH = 100;

  /** Why a name was refused; the machine-readable {@code code} sent to the client. */
  public enum Reason {
    NAME_BLANK,
    NAME_TOO_LONG
  }

  /**
   * Thrown by {@link #of} for a name that breaks the rule; always {@code 400}, with the {@link
   * Reason} rendered as {@code code} by the controller's handler.
   */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public static class Refused extends RuntimeException {
    private final Reason reason;

    public Refused(Reason reason, String message) {
      super(message);
      this.reason = reason;
    }

    public Reason reason() {
      return reason;
    }
  }

  /** The normalised name, ready to store. */
  public static ManagerName of(String raw) {
    String name = raw == null ? "" : raw.strip();
    if (name.isEmpty()) {
      throw new Refused(Reason.NAME_BLANK, "A name is required");
    }
    if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
      throw new Refused(Reason.NAME_TOO_LONG, "A name is at most " + MAX_LENGTH + " characters");
    }
    return new ManagerName(name);
  }
}

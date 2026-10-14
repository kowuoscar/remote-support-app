package com.remotesupport.backend.domain;

/**
 * The one rule for a valid Manager name, applied by every path that writes one: surrounding
 * whitespace is trimmed, an empty result is refused ({@link Reason#NAME_BLANK}), and so is more
 * than {@value #MAX_LENGTH} characters after trimming ({@link Reason#NAME_TOO_LONG}). Inner
 * spacing and any script are kept as typed.
 */
public final class ManagerName {

  public static final int MAX_LENGTH = 100;

  /** Why a name was refused; the machine-readable {@code code} sent to the client. */
  public enum Reason {
    NAME_BLANK,
    NAME_TOO_LONG
  }

  /** Thrown by {@link #of} for a name that breaks the rule. */
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

  private ManagerName() {}

  /** The normalised name, ready to store. */
  public static String of(String raw) {
    String name = raw == null ? "" : raw.strip();
    if (name.isEmpty()) {
      throw new Refused(Reason.NAME_BLANK, "A name is required");
    }
    if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
      throw new Refused(Reason.NAME_TOO_LONG, "A name is at most " + MAX_LENGTH + " characters");
    }
    return name;
  }
}

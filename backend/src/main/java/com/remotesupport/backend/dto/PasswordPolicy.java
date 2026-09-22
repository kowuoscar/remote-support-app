package com.remotesupport.backend.dto;

/**
 * The one rule a password is held to everywhere one is <em>written</em> (password-minimum-length
 * ticket, spec.md "The password rule"): at least {@link #MIN_LENGTH} characters, no complexity
 * classes. Every request DTO that writes a password — {@link ChangePasswordRequest#newPassword()},
 * {@link AgentCreateRequest#password()}, {@link TesterCreateRequest#password()} — annotates its
 * field with {@code @Size(min = PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)}
 * rather than each repeating its own length and message, per spec.md Decisions taken: "three DTOs
 * is three annotations and one shared message constant."
 *
 * <p>{@link LoginRequest} deliberately does not use this and keeps {@code @NotBlank} alone — a
 * shape rule at sign-in would lock out every existing password shorter than this minimum, and
 * there is no upgrade path for a password nobody can read (spec.md Constraints).
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 8;
  public static final String TOO_SHORT_MESSAGE = "Password must be at least 8 characters long";

  private PasswordPolicy() {}
}

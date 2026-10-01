package com.remotesupport.backend.dto;

/**
 * The one rule a password is held to everywhere one is <em>written</em> (password-minimum-length
 * ticket, spec.md "The password rule"): at least {@link #MIN_LENGTH} characters, no complexity
 * classes. The one request DTO that accepts a typed password, {@link
 * ChangePasswordRequest#newPassword()}, annotates its field with {@code @Size(min =
 * PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)} rather than repeating
 * its own length and message. Login creation takes no typed password (the product generates it)
 * and resets generate too.
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

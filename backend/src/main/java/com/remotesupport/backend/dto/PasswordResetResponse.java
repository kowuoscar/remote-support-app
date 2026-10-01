package com.remotesupport.backend.dto;

/**
 * The {@code 200} body of a Manager's password reset: the generated password, shown once.
 * {@code toString} redacts it.
 */
public record PasswordResetResponse(String password) {

  @Override
  public String toString() {
    return "PasswordResetResponse[password=[redacted]]";
  }
}

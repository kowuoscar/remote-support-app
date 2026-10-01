package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The one place a Login's password is written: encodes a raw value with the application's {@link
 * PasswordEncoder} and sets {@link User#setPasswordHash}. Does not validate (the minimum length
 * lives on the request) and does not save; the caller owns the transaction.
 */
@Component
public class PasswordWrite {

  private final PasswordEncoder passwordEncoder;

  public PasswordWrite(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  public void setPassword(User user, String rawPassword) {
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
  }
}

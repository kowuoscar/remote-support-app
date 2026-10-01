package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The one place a Login's password is written: encodes a raw value with the application's {@link
 * PasswordEncoder} and sets {@link User#setPasswordHash}. Does not validate (the minimum length
 * lives on the request) and does not save; the caller owns the transaction.
 *
 * <p>Two operations: {@link #setPassword} for a value the person typed, {@link
 * #setGeneratedPassword} for one the product generates.
 */
@Component
public class PasswordWrite {

  private static final int MAX_DRAWS = 3;

  private final PasswordEncoder passwordEncoder;
  private final PasswordGenerator passwordGenerator;

  public PasswordWrite(PasswordEncoder passwordEncoder) {
    this(passwordEncoder, new PasswordGenerator());
  }

  @Autowired
  public PasswordWrite(PasswordEncoder passwordEncoder, PasswordGenerator passwordGenerator) {
    this.passwordEncoder = passwordEncoder;
    this.passwordGenerator = passwordGenerator;
  }

  public void setPassword(User user, String rawPassword) {
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
  }

  /**
   * Gives the Login a freshly generated password and returns it in the clear; the caller is its
   * only holder from then on. A candidate that matches the Login's current hash is discarded, up to
   * {@value #MAX_DRAWS} draws, then {@link IllegalStateException}. A Login with no hash skips the
   * check.
   */
  public String setGeneratedPassword(User user) {
    String currentHash = user.getPasswordHash();
    for (int draw = 0; draw < MAX_DRAWS; draw++) {
      String candidate = passwordGenerator.generate();
      if (currentHash == null || !passwordEncoder.matches(candidate, currentHash)) {
        setPassword(user, candidate);
        return candidate;
      }
    }
    throw new IllegalStateException("No new password after " + MAX_DRAWS + " draws");
  }
}

package com.remotesupport.backend.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Draws a candidate password: three hyphen-joined groups of four symbols from a 32-symbol alphabet
 * without the look-alikes {@code l}, {@code o}, {@code 0} and {@code 1} (60 bits, 14 characters).
 * Knows nothing about any Login; {@link PasswordWrite} decides whether a candidate is acceptable.
 */
@Component
public class PasswordGenerator {

  private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
  private static final int GROUPS = 3;
  private static final int GROUP_LENGTH = 4;

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    StringBuilder password = new StringBuilder(GROUPS * (GROUP_LENGTH + 1));
    for (int group = 0; group < GROUPS; group++) {
      if (group > 0) {
        password.append('-');
      }
      for (int i = 0; i < GROUP_LENGTH; i++) {
        password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
    }
    return password.toString();
  }
}

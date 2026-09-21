package com.remotesupport.backend.domain;

/**
 * The one normalization every username-creation path applies before a username is read, stored
 * or checked (globally-unique-usernames spec.md "Normalization, the same three layers as
 * carrier-catalog") — held here once rather than copied at each call site (review finding F6),
 * and defined to agree exactly with what the database enforces rather than with {@link
 * String#strip()}.
 *
 * <p>V55's {@code uq_users_username_global} index is {@code lower(btrim(username))}, and {@link
 * com.remotesupport.backend.repository.UserRepository#existsByUsernameNormalized} is JPQL's
 * {@code lower(trim(...))}. Both Postgres functions, called with no explicit character set,
 * strip only the ASCII space character (U+0020) from each end — never a tab, a newline, or a
 * non-breaking space. {@link String#strip()} strips every Unicode whitespace character it
 * recognizes (which includes tabs, though not non-breaking spaces), so a username padded with a
 * tab would normalize one way in Java and another way at the index that enforces uniqueness.
 * {@link #trim} strips the same single character the database does, so the two can never
 * disagree.
 */
public final class Username {

  private static final char ASCII_SPACE = ' ';

  private Username() {}

  /** {@code username} with any leading or trailing ASCII spaces removed — nothing else. */
  public static String trim(String username) {
    int start = 0;
    int end = username.length();
    while (start < end && username.charAt(start) == ASCII_SPACE) {
      start++;
    }
    while (end > start && username.charAt(end - 1) == ASCII_SPACE) {
      end--;
    }
    return username.substring(start, end);
  }
}

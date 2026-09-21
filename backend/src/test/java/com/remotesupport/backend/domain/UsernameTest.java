package com.remotesupport.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link Username#trim} to the one normalization dialect that matters: what Postgres's
 * {@code lower(btrim(username))} (V55's {@code uq_users_username_global} index) and {@code
 * lower(trim(...))} ({@link com.remotesupport.backend.repository.UserRepository
 * #existsByUsernameNormalized}) actually enforce — trimming only the ASCII space character,
 * U+0020, from each end (review finding F6, globally-unique-usernames). {@link String#strip()}
 * disagrees: it also removes tabs, newlines and most other Unicode whitespace, which would let a
 * username padded with one of those normalize differently in Java than at the database's own
 * uniqueness index.
 */
class UsernameTest {

  @Test
  void trimsLeadingAndTrailingAsciiSpaces() {
    assertThat(Username.trim("  tom.reyes@example.com  ")).isEqualTo("tom.reyes@example.com");
  }

  @Test
  void leavesInteriorSpacesAlone() {
    assertThat(Username.trim(" tom reyes ")).isEqualTo("tom reyes");
  }

  @Test
  void leavesATabInPlaceUnlikeStringStrip() {
    String padded = "\ttom.reyes@example.com\t";

    assertThat(Username.trim(padded)).isEqualTo(padded);
    // The behaviour being rejected: String.strip() would remove the tab, disagreeing with what
    // the database's btrim/trim leave behind.
    assertThat(padded.strip()).isEqualTo("tom.reyes@example.com");
  }

  @Test
  void leavesANonBreakingSpaceInPlace() {
    String padded = " tom.reyes@example.com ";

    assertThat(Username.trim(padded)).isEqualTo(padded);
  }

  @Test
  void isANoOpWhenThereIsNothingToTrim() {
    assertThat(Username.trim("tom.reyes@example.com")).isEqualTo("tom.reyes@example.com");
  }

  @Test
  void trimsDownToEmptyWhenTheUsernameIsAllSpaces() {
    assertThat(Username.trim("   ")).isEqualTo("");
  }
}

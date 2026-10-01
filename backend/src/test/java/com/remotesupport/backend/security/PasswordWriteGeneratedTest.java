package com.remotesupport.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.remotesupport.backend.domain.User;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordWriteGeneratedTest {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  /** A generator that hands out the given candidates in order. */
  private static final class ScriptedGenerator extends PasswordGenerator {
    private final Deque<String> candidates;
    int draws;

    ScriptedGenerator(String... candidates) {
      this.candidates = new ArrayDeque<>(List.of(candidates));
    }

    @Override
    public String generate() {
      draws++;
      return candidates.removeFirst();
    }
  }

  private User userWithPassword(String raw) {
    User user = new User();
    new PasswordWrite(encoder).setPassword(user, raw);
    return user;
  }

  @Test
  void skipsACandidateEqualToTheCurrentPassword() {
    User user = userWithPassword("same-same-same");
    ScriptedGenerator generator = new ScriptedGenerator("same-same-same", "next-next-next");

    String result = new PasswordWrite(encoder, generator).setGeneratedPassword(user);

    assertThat(result).isEqualTo("next-next-next");
    assertThat(encoder.matches("next-next-next", user.getPasswordHash())).isTrue();
    assertThat(encoder.matches("same-same-same", user.getPasswordHash())).isFalse();
  }

  @Test
  void threeCandidatesEqualToTheCurrentPasswordFail() {
    User user = userWithPassword("same-same-same");
    String oldHash = user.getPasswordHash();
    ScriptedGenerator generator =
        new ScriptedGenerator("same-same-same", "same-same-same", "same-same-same", "never-drawn");

    assertThatThrownBy(() -> new PasswordWrite(encoder, generator).setGeneratedPassword(user))
        .isInstanceOf(IllegalStateException.class);
    assertThat(generator.draws).isEqualTo(3);
    assertThat(user.getPasswordHash()).isEqualTo(oldHash);
  }

  @Test
  void aLoginWithNoHashTakesTheFirstCandidate() {
    User user = new User();
    ScriptedGenerator generator = new ScriptedGenerator("first-first-first");

    String result = new PasswordWrite(encoder, generator).setGeneratedPassword(user);

    assertThat(result).isEqualTo("first-first-first");
    assertThat(generator.draws).isEqualTo(1);
    assertThat(encoder.matches("first-first-first", user.getPasswordHash())).isTrue();
  }
}

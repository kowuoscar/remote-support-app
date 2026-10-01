package com.remotesupport.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordWriteTest {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();
  private final PasswordWrite passwordWrite = new PasswordWrite(encoder);

  @Test
  void setPasswordLeavesAHashThatMatchesTheRawValueAndNoOther() {
    User user = new User();

    passwordWrite.setPassword(user, "correct-horse-battery");

    assertThat(encoder.matches("correct-horse-battery", user.getPasswordHash())).isTrue();
    assertThat(encoder.matches("a-different-one", user.getPasswordHash())).isFalse();
  }
}

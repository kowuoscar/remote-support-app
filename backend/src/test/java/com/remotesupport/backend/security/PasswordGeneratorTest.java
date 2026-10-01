package com.remotesupport.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.dto.PasswordPolicy;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {

  private static final Pattern SHAPE = Pattern.compile("^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$");

  @Test
  void everyDrawIsThreeHyphenatedGroupsOfFourLongEnoughToPassThePolicy() {
    PasswordGenerator generator = new PasswordGenerator();

    for (int i = 0; i < 10_000; i++) {
      String password = generator.generate();

      assertThat(password).matches(SHAPE);
      assertThat(password.length()).isGreaterThanOrEqualTo(PasswordPolicy.MIN_LENGTH);
    }
  }
}

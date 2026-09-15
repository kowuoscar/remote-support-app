package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Login is the one unauthenticated write endpoint, so its outcome — who tried, whether it
 * succeeded, and which tenant/role they landed with — has to be in the logs even though
 * {@link com.remotesupport.backend.logging.RequestLoggingFilter}'s MDC is empty for this request
 * (there's no authenticated principal yet when it runs).
 */
class AuthLoginObservabilityTest extends IntegrationTest {

  private static final String SEEDED_USERNAME = "manager@example.com";
  private static final String SEEDED_PASSWORD = "ChangeMe123!";
  private static final String SEEDED_TENANT_ID = "11111111-1111-1111-1111-111111111111";
  private static final String SEEDED_USER_ID = "22222222-2222-2222-2222-222222222222";

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(AuthController.class)).addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    ((Logger) LoggerFactory.getLogger(AuthController.class)).detachAppender(appender);
  }

  @Test
  void successfulLoginLogsTenantUserIdAndRole() throws Exception {
    mockMvc.perform(
        post("/api/auth/login")
            .contentType(APPLICATION_JSON)
            .content(
                """
                {"username":"%s","password":"%s"}
                """
                    .formatted(SEEDED_USERNAME, SEEDED_PASSWORD)));

    String logged =
        appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    assertThat(logged).contains(SEEDED_TENANT_ID);
    assertThat(logged).contains(SEEDED_USER_ID);
    assertThat(logged).contains("MANAGER");
  }

  @Test
  void failedLoginLogsTheAttemptedUsername() throws Exception {
    mockMvc.perform(
        post("/api/auth/login")
            .contentType(APPLICATION_JSON)
            .content(
                """
                {"username":"%s","password":"wrong-password"}
                """
                    .formatted(SEEDED_USERNAME)));

    String logged =
        appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    assertThat(logged).contains(SEEDED_USERNAME);
  }
}

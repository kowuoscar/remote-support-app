package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Tester creation and listing, nested under a Client (manager-entity-setup ticket): a Tester is
 * a login belonging to exactly one Client, with at most one flagged as the primary contact
 * (spec.md Core entities).
 */
class TesterApiTest extends IntegrationTest {

  @Test
  void managerCanCreateAndListTestersUnderAClient() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Meridian Logistics");

    mockMvc
        .perform(
            post("/api/clients/" + clientId + "/testers")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TesterCreateRequest("tom.reyes@meridian.example", "Passw0rd!23", true))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("tom.reyes@meridian.example"))
        .andExpect(jsonPath("$.isPrimaryContact").value(true));

    mockMvc
        .perform(
            get("/api/clients/" + clientId + "/testers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void aSecondPrimaryContactForTheSameClientIsRejected() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Kessler & Vance LLP");

    mockMvc.perform(
        post("/api/clients/" + clientId + "/testers")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    new TesterCreateRequest("helena.voss@kessler.example", "Passw0rd!23", true))));

    mockMvc
        .perform(
            post("/api/clients/" + clientId + "/testers")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TesterCreateRequest("second@kessler.example", "Passw0rd!23", true))))
        .andExpect(status().isConflict());
  }

  @Test
  void creatingATesterUnderAnUnknownClientReturnsNotFound() throws Exception {
    String token = managerToken();

    mockMvc
        .perform(
            post("/api/clients/00000000-0000-0000-0000-000000000000/testers")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TesterCreateRequest("ghost@example.com", "Passw0rd!23", false))))
        .andExpect(status().isNotFound());
  }

  @Test
  void agentAndTesterCannotCreateOrListTesters() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");

    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/clients/" + clientId + "/testers")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new TesterCreateRequest("rejected@example.com", "Passw0rd!23", false))))
          .andExpect(status().isForbidden());

      mockMvc
          .perform(
              get("/api/clients/" + clientId + "/testers")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void creatingATesterLogsAnAuditEntry() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String token = managerToken();
      UUID clientId = createClient(token, "Bright Path Clinics");

      mockMvc.perform(
          post("/api/clients/" + clientId + "/testers")
              .header("Authorization", "Bearer " + token)
              .contentType(APPLICATION_JSON)
              .content(
                  objectMapper.writeValueAsString(
                      new TesterCreateRequest("audited@brightpath.example", "Passw0rd!23", false))));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=Tester");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}

package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.dto.ClientCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Client creation and listing (manager-entity-setup ticket): Manager-only, per spec.md's Core
 * entities ("Client: the company whose Testers receive support. Has many Testers and many
 * Contracts.").
 */
class ClientApiTest extends IntegrationTest {

  @Test
  void managerCanCreateAndListClients() throws Exception {
    String token = managerToken();

    mockMvc
        .perform(
            post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClientCreateRequest("Aurora Retail Group"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Aurora Retail Group"))
        .andExpect(jsonPath("$.contractCount").value(0))
        .andExpect(jsonPath("$.primaryContactUsername").doesNotExist());

    mockMvc
        .perform(get("/api/clients").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Aurora Retail Group')]").exists());
  }

  @Test
  void creatingAClientRejectsBlankName() throws Exception {
    String token = managerToken();

    mockMvc
        .perform(
            post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClientCreateRequest(" "))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void agentCannotCreateOrListClients() throws Exception {
    String token = agentToken();

    mockMvc
        .perform(
            post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClientCreateRequest("Rejected Co"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/clients").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void testerCannotCreateOrListClients() throws Exception {
    String token = testerToken();

    mockMvc
        .perform(
            post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClientCreateRequest("Rejected Co"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/clients").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatingAClientLogsAnAuditEntry() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String token = managerToken();
      mockMvc.perform(
          post("/api/clients")
              .header("Authorization", "Bearer " + token)
              .contentType(APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(new ClientCreateRequest("Audited Co"))));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=Client");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}

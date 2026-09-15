package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Agent creation and listing (manager-entity-setup ticket): an Agent's currency is fixed
 * deterministically by its country, never chosen independently (spec.md: "a person hired in one
 * country (which fixes their currency)").
 */
class AgentApiTest extends IntegrationTest {

  @Test
  void managerCanCreateAnAgentWithCountryDerivingItsCurrency() throws Exception {
    String token = managerToken();
    AgentCreateRequest request =
        new AgentCreateRequest("Camille Duforet", Country.FRANCE, new BigDecimal("2400.00"));

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Camille Duforet"))
        .andExpect(jsonPath("$.country").value("FRANCE"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.salaryAmount").value(2400.00))
        .andExpect(jsonPath("$.contractCount").value(0));

    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Camille Duforet')].currency").value("EUR"));
  }

  @Test
  void differentCountriesDeriveDifferentCurrencies() throws Exception {
    String token = managerToken();
    AgentCreateRequest mexican =
        new AgentCreateRequest("Luis Bautista", Country.MEXICO, new BigDecimal("1800.00"));
    AgentCreateRequest filipino =
        new AgentCreateRequest("Priya Nair", Country.PHILIPPINES, new BigDecimal("1450.00"));

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mexican)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("MXN"));

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filipino)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("PHP"));
  }

  @Test
  void creatingAnAgentRejectsANegativeSalary() throws Exception {
    String token = managerToken();
    AgentCreateRequest request =
        new AgentCreateRequest(
            "Negative Salary", Country.UNITED_STATES, new BigDecimal("-1.00"));

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void agentAndTesterCannotCreateOrListAgents() throws Exception {
    AgentCreateRequest request =
        new AgentCreateRequest("Rejected", Country.UNITED_KINGDOM, new BigDecimal("100.00"));

    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/agents")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());

      mockMvc
          .perform(get("/api/agents").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void creatingAnAgentLogsAnAuditEntry() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String token = managerToken();
      AgentCreateRequest request =
          new AgentCreateRequest(
              "Owen Whitfield", Country.UNITED_KINGDOM, new BigDecimal("2200.00"));

      mockMvc.perform(
          post("/api/agents")
              .header("Authorization", "Bearer " + token)
              .contentType(APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=Agent");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}

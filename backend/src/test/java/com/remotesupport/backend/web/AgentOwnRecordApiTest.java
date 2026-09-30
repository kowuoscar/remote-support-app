package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentStandingAmount;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.AgentStandingAmountRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code GET /api/me/agent} (agent-own-record-read ticket): an Agent reads their own record and the
 * standing amounts in effect this billing month, while setting them stays Manager-only.
 */
class AgentOwnRecordApiTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";

  @Autowired private AgentRepository agentRepository;
  @Autowired private AgentStandingAmountRepository agentStandingAmountRepository;
  @Autowired private UserRepository userRepository;

  private static LocalDate currentMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  private record AgentLogin(UUID agentId, String token) {}

  private AgentLogin createAgentWithLogin(String name, Country country, String salary)
      throws Exception {
    String username = "own-record-" + UUID.randomUUID() + "@agents.example";
    MvcResult result =
        postJson(
                "/api/agents",
                managerToken(),
                new AgentCreateRequest(name, country, new BigDecimal(salary), username, PASSWORD))
            .andExpect(status().isCreated())
            .andReturn();
    UUID agentId =
        UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    return new AgentLogin(agentId, loginAs(username, PASSWORD));
  }

  private void insertStandingAmountRow(
      UUID agentId, StandingAmountType type, String amount, LocalDate effectiveMonth) {
    Agent agent = agentRepository.findById(agentId).orElseThrow();
    AgentStandingAmount row = new AgentStandingAmount();
    row.setId(UUID.randomUUID());
    row.setTenant(agent.getTenant());
    row.setAgent(agent);
    row.setAmountType(type);
    row.setAmount(new BigDecimal(amount));
    row.setEffectiveMonth(effectiveMonth);
    row.setSetByUserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    row.setSetAt(Instant.now());
    agentStandingAmountRepository.saveAndFlush(row);
  }

  private void managerSetsStandingAmount(UUID agentId, String type, String amount)
      throws Exception {
    mockMvc
        .perform(
            post("/api/agents/" + agentId + "/standing-amounts")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(APPLICATION_JSON)
                .content("{\"amountType\":\"%s\",\"amount\":\"%s\"}".formatted(type, amount)))
        .andExpect(status().isCreated());
  }

  @Test
  void anAgentGetsTheirOwnRecordWithRolloutAdvanceZeroWhenNeverSet() throws Exception {
    AgentLogin agent = createAgentWithLogin("Own Record Agent", Country.UNITED_STATES, "2000.00");

    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + agent.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agentId").value(agent.agentId().toString()))
        .andExpect(jsonPath("$.name").value("Own Record Agent"))
        .andExpect(jsonPath("$.country").value("UNITED_STATES"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.salaryAmount").value(2000.00))
        .andExpect(jsonPath("$.rolloutAdvanceAmount").value(0));
  }

  @Test
  void twoAgentsEachGetOnlyTheirOwnRecord() throws Exception {
    AgentLogin first = createAgentWithLogin("First Own Agent", Country.UNITED_STATES, "1500.00");
    AgentLogin second = createAgentWithLogin("Second Own Agent", Country.UNITED_STATES, "2500.00");

    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + first.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agentId").value(first.agentId().toString()))
        .andExpect(jsonPath("$.name").value("First Own Agent"))
        .andExpect(jsonPath("$.salaryAmount").value(1500.00));
    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + second.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agentId").value(second.agentId().toString()))
        .andExpect(jsonPath("$.name").value("Second Own Agent"))
        .andExpect(jsonPath("$.salaryAmount").value(2500.00));
  }

  @Test
  void amountsAreThoseInEffectThisMonthNotAFutureEffectiveChange() throws Exception {
    AgentLogin agent = createAgentWithLogin("Resolving Agent", Country.UNITED_STATES, "2000.00");
    insertStandingAmountRow(
        agent.agentId(),
        StandingAmountType.ROLLOUT_ADVANCE,
        "300.00",
        currentMonth().minusMonths(1));

    // A Manager's change takes effect next month, so this month's read must not show it.
    managerSetsStandingAmount(agent.agentId(), "SALARY", "9999.00");
    managerSetsStandingAmount(agent.agentId(), "ROLLOUT_ADVANCE", "8888.00");

    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + agent.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salaryAmount").value(2000.00))
        .andExpect(jsonPath("$.rolloutAdvanceAmount").value(300.00));
  }

  @Test
  void aManagerGetsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isNotFound());
  }

  @Test
  void aTesterGetsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + testerToken()))
        .andExpect(status().isNotFound());
  }

  @Test
  void anAgentLoginNotLinkedToAnAgentGetsNotFound() throws Exception {
    AgentLogin agent = createAgentWithLogin("Unlinked Agent", Country.UNITED_STATES, "2000.00");
    userRepository.findAll().stream()
        .filter(u -> u.getAgent() != null && agent.agentId().equals(u.getAgent().getId()))
        .forEach(
            u -> {
              u.setAgent(null);
              userRepository.saveAndFlush(u);
            });

    mockMvc
        .perform(get("/api/me/agent").header("Authorization", "Bearer " + agent.token()))
        .andExpect(status().isNotFound());
  }

  @Test
  void withNoTokenIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/me/agent")).andExpect(status().isUnauthorized());
  }

  @Test
  void anAgentStillCannotReadTheManagerOnlyStandingAmountsRoute() throws Exception {
    mockMvc
        .perform(
            get("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts")
                .header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aManagerCanStillSetAStandingAmount() throws Exception {
    managerSetsStandingAmount(SEEDED_AGENT_ID, "SALARY", "2100.00");
  }
}

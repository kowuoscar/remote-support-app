package com.remotesupport.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Prefactor for fleet-management: an AGENT-role User resolves to the Agent record it corresponds
 * to (the same shape Tester already has — a User subtype carrying a foreign key back to its
 * owning entity, here a nullable {@code agent_id} on `users`), so Fleet visibility can be
 * authorized against "is this my Contract's Agent". {@code GET /api/me} is the seam: it already
 * exposes the authenticated identity, so it's where the resolved link surfaces too.
 */
class AgentIdentityApiTest extends IntegrationTest {

  @Test
  void theSeededAgentUserResolvesToARealAgentRecord() throws Exception {
    String token = agentToken();

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AGENT"))
        .andExpect(jsonPath("$.agentId").isNotEmpty())
        .andExpect(jsonPath("$.clientId").doesNotExist());
  }

  @Test
  void aManagerHasNoLinkedAgentOrClient() throws Exception {
    String token = managerToken();

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.agentId").doesNotExist())
        .andExpect(jsonPath("$.clientId").doesNotExist());
  }

  @Test
  void theSeededTesterUserResolvesToItsClient() throws Exception {
    String token = testerToken();

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TESTER"))
        .andExpect(jsonPath("$.clientId").doesNotExist())
        .andExpect(jsonPath("$.agentId").doesNotExist());
  }
}

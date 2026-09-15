package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remotesupport.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The JWT enforcement half of the auth story: a valid token reaches a protected endpoint, a
 * missing/invalid one is rejected. {@code GET /api/me} is the protected endpoint used as the
 * seam — it also doubles as "who am I", useful once real business endpoints exist.
 */
class ProtectedEndpointTest extends IntegrationTest {

  private static final String SEEDED_USERNAME = "manager@example.com";
  private static final String SEEDED_PASSWORD = "ChangeMe123!";

  @Autowired private ObjectMapper objectMapper;

  @Test
  void authenticatedRequestToProtectedEndpointSucceeds() throws Exception {
    String token = login();

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(SEEDED_USERNAME))
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  void unauthenticatedRequestToProtectedEndpointIsRejected() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void requestWithGarbageTokenIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer not-a-real-token"))
        .andExpect(status().isUnauthorized());
  }

  private String login() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","password":"%s"}
                        """
                            .formatted(SEEDED_USERNAME, SEEDED_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("token").asText();
  }
}

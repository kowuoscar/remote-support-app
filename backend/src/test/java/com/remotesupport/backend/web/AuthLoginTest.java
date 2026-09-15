package com.remotesupport.backend.web;

import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Login endpoint, the entry point of JWT auth. Seed data (one Tenant, one Manager user) is
 * created by a Flyway migration so it exists identically in every environment, local dev
 * included.
 */
class AuthLoginTest extends IntegrationTest {

  private static final String SEEDED_USERNAME = "manager@example.com";
  private static final String SEEDED_PASSWORD = "ChangeMe123!";

  @Test
  void loginWithValidCredentialsReturnsToken() throws Exception {
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
        .andExpect(jsonPath("$.token").value(not(org.hamcrest.Matchers.emptyOrNullString())))
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  void loginWithSeededAgentCredentialsReturnsAgentRole() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"username":"agent@example.com","password":"AgentDemo123!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AGENT"));
  }

  @Test
  void loginWithSeededTesterCredentialsReturnsTesterRole() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"username":"tester@example.com","password":"TesterDemo123!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TESTER"));
  }

  @Test
  void loginWithInvalidCredentialsIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"wrong-password"}
                    """
                        .formatted(SEEDED_USERNAME)))
        .andExpect(status().isUnauthorized());
  }
}

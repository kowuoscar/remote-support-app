package com.remotesupport.backend.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for HTTP-API-seam integration tests: boots the full Spring context against a real
 * PostgreSQL 16 instance provided by Testcontainers, and exposes {@link MockMvc} for driving
 * requests through the real REST controllers.
 *
 * <p>The container is started once, in a static initializer, and deliberately never stopped by
 * this class ("singleton container" pattern) so it is shared across every subclass in the run:
 * Testcontainers' own {@code @Container}/{@code @Testcontainers} lifecycle stops the container in
 * each test class's {@code afterAll}, which breaks every subsequent test class sharing the same
 * static field. The JVM shutdown hook Testcontainers registers (via Ryuk) tears it down when the
 * test run ends.
 *
 * <p>Every test method runs inside its own transaction, rolled back at the end (Spring's
 * standard test-transaction support): MockMvc requests run in-process on the test thread, so
 * their JPA writes join the same transaction and are visible to later requests in the same test
 * method, but never leak into other test methods or classes sharing the singleton container.
 * Tests that assert log content (audit/observability) are unaffected — logging isn't
 * transactional.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTest {

  // Credentials for the three roles seeded by Flyway (V2/V3 migrations), shared across every
  // HTTP-seam test that needs a token for a given role rather than re-declaring them per class.
  public static final String MANAGER_USERNAME = "manager@example.com";
  public static final String MANAGER_PASSWORD = "ChangeMe123!";
  public static final String AGENT_USERNAME = "agent@example.com";
  public static final String AGENT_PASSWORD = "AgentDemo123!";
  public static final String TESTER_USERNAME = "tester@example.com";
  public static final String TESTER_PASSWORD = "TesterDemo123!";

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  /** Logs in as the given seeded user and returns the bearer token, ready for an Authorization header. */
  protected String loginAs(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","password":"%s"}
                        """
                            .formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("token").asText();
  }

  protected String managerToken() throws Exception {
    return loginAs(MANAGER_USERNAME, MANAGER_PASSWORD);
  }

  protected String agentToken() throws Exception {
    return loginAs(AGENT_USERNAME, AGENT_PASSWORD);
  }

  protected String testerToken() throws Exception {
    return loginAs(TESTER_USERNAME, TESTER_PASSWORD);
  }
}

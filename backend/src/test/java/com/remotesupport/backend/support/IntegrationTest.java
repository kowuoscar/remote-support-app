package com.remotesupport.backend.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.ClientCreateRequest;
import com.remotesupport.backend.dto.ContractCreateRequest;
import com.remotesupport.backend.dto.TesterCreateRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
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

  // demo.tester@example.com (V17 migration): unlike TESTER_USERNAME above, this login resolves
  // to a real Tester with its own Client/Contract/Fleet, for manual/local testing of the Tester
  // shell without first having to create fixtures through the Manager UI.
  public static final String DEMO_TESTER_USERNAME = "demo.tester@example.com";
  public static final String DEMO_TESTER_PASSWORD = "DemoTesterDemo123!";
  public static final UUID SEEDED_DEMO_CLIENT_ID =
      UUID.fromString("77777777-7777-7777-7777-777777777777");
  public static final UUID SEEDED_DEMO_CONTRACT_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  // The Agent row (V5 migration) the seeded agent@example.com login resolves to — "Jordan
  // Ellis", United States/USD. Tests that need a real Contract for the seeded Agent token use
  // this id directly rather than re-deriving it by name.
  public static final UUID SEEDED_AGENT_ID =
      UUID.fromString("55555555-5555-5555-5555-555555555555");

  // The seeded United States "Verizon" Carrier (V20 migration) — the Carrier a new SIM Card on a
  // Contract of the seeded Agent names, for tests that only need some valid Carrier.
  public static final UUID SEEDED_US_CARRIER_ID =
      UUID.fromString("c0000000-0000-0000-0000-000000000003");

  // Public so a test that migrates its own throwaway database (V23's migration test) can reach
  // the same container rather than starting a second one.
  @ServiceConnection
  public static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  /**
   * POSTs a JSON body with a bearer token — the shape behind most write requests in this suite,
   * for tests that chain their own {@code andExpect}s rather than wanting a created id back.
   */
  protected ResultActions postJson(String url, String token, Object body) throws Exception {
    return mockMvc.perform(
        post(url)
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
  }

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

  protected String demoTesterToken() throws Exception {
    return loginAs(DEMO_TESTER_USERNAME, DEMO_TESTER_PASSWORD);
  }

  /** Creates a Client as the Manager and returns its id — shared fixture-building across tests. */
  protected UUID createClient(String managerToken, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/clients")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ClientCreateRequest(name))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /**
   * Creates an Agent as the Manager and returns its id — shared fixture-building across tests.
   * Every Agent is created together with its login (create-agent-with-login ticket); this helper
   * gives it a unique throwaway email, for tests that never sign in as that Agent.
   */
  protected UUID createAgent(String managerToken, String name, Country country) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new AgentCreateRequest(
                                name,
                                country,
                                new BigDecimal("2000.00"),
                                "agent-" + UUID.randomUUID() + "@agents.example",
                                "Passw0rd!23"))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /** Creates an active Carrier in a Country, as the Manager, and returns its id. */
  protected UUID createCarrier(String managerToken, Country country, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/carriers")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"country":"%s","name":"%s"}
                        """
                            .formatted(country.name(), name)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /**
   * An active Carrier a new SIM Card on this Contract may name — one of its Agent's Country's, or a
   * freshly created one when that Country has none yet. For tests whose subject isn't the Carrier.
   */
  protected UUID carrierFor(String managerToken, UUID contractId) throws Exception {
    JsonNode contracts =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/contracts").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String country = null;
    for (JsonNode contract : contracts) {
      if (contract.get("id").asText().equals(contractId.toString())) {
        country = contract.get("country").asText();
      }
    }
    if (country == null) {
      throw new IllegalStateException("No contract with id " + contractId);
    }
    JsonNode carriers =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/carriers?country=" + country).header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("carriers");
    if (!carriers.isEmpty()) {
      return UUID.fromString(carriers.get(0).get("id").asText());
    }
    return createCarrier(managerToken, Country.valueOf(country), "Test Carrier");
  }

  /** Archives a Carrier, as the Manager. */
  protected void archiveCarrier(String managerToken, UUID carrierId) throws Exception {
    mockMvc
        .perform(
            post("/api/carriers/" + carrierId + "/archive").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
  }

  /** Creates a Contract linking a Client and an Agent, as the Manager, and returns its id. */
  protected UUID createContract(String managerToken, UUID clientId, UUID agentId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ContractCreateRequest(clientId, agentId))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /**
   * Creates a Tester under the given Client, as the Manager, then logs in as that Tester and
   * returns its bearer token — for tests that need a Tester scoped to a specific Client rather
   * than the unlinked seeded {@code tester@example.com} login.
   */
  protected String createTesterAndLogin(
      String managerToken, UUID clientId, String username, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/clients/" + clientId + "/testers")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new TesterCreateRequest(username, password, false))))
        .andExpect(status().isCreated());
    return loginAs(username, password);
  }
}

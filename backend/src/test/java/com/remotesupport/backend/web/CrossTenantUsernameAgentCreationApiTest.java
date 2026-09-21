package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantLogin;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating an Agent with its login is refused when the username is already taken in a
 * <b>different</b> Tenant (globally-unique-usernames spec.md, "One rule, five creation paths";
 * global-username-index ticket).
 *
 * <p><b>Replaces {@code CollidingUsernameSignInApiTest}</b>, deleted by this same ticket: that
 * test's fixture call, {@code OtherTenantFixture.managerLoginInAnotherTenant}, inserts its second
 * Tenant's colliding row via {@code saveAndFlush} straight through the repository, so once V55's
 * global unique index exists that insert itself fails before the deleted test could ever reach
 * its assertion. Its own Javadoc named the replacement: "creating a login whose username is
 * already taken in another Tenant is refused." {@code POST /api/agents}
 * (create-an-Agent-with-its-login) is where that is demonstrated, because — unlike giving an
 * existing Agent a login — it has no pre-check ahead of the write (see {@code AgentController}'s
 * own Javadoc): the request reaches {@link AgentLoginService#create}'s flush-time
 * {@code DataIntegrityViolationException} catch for real, which is exactly the path whose
 * violation-name matching this ticket widens to recognize V55's {@code
 * uq_users_username_global} alongside V1's {@code uq_users_tenant_username}. Also this ticket's
 * own proof that the branch stays green: without that widened matching, this same request would
 * 500 instead of 409 the moment V55 lands.
 *
 * <p>Runs outside the shared test transaction, like {@link AgentCreationAtomicityTest}: inside
 * it, the whole test rolls back anyway, so a create endpoint that committed the Agent before its
 * login failed would pass unnoticed. Here the request commits or rolls back for real, and
 * whatever this test (or the fixture it uses) committed is deleted afterwards.
 */
@Import(OtherTenantFixture.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CrossTenantUsernameAgentCreationApiTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";
  private static final String NAME_PREFIX = "Cross Tenant " + UUID.randomUUID() + " ";

  @Autowired private OtherTenantFixture otherTenantFixture;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID otherTenantId;
  private String otherTenantUsername;

  @AfterEach
  void deleteWhatThisTestCommitted() {
    String agentsOfThisClass = "SELECT id FROM agents WHERE name LIKE ?";
    String pattern = NAME_PREFIX + "%";
    jdbcTemplate.update(
        "DELETE FROM agent_standing_amounts WHERE agent_id IN (" + agentsOfThisClass + ")",
        pattern);
    jdbcTemplate.update(
        "DELETE FROM users WHERE agent_id IN (" + agentsOfThisClass + ")", pattern);
    jdbcTemplate.update("DELETE FROM agents WHERE name LIKE ?", pattern);
    if (otherTenantId != null) {
      jdbcTemplate.update("DELETE FROM clients WHERE tenant_id = ?", otherTenantId);
      jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", otherTenantId);
      jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", otherTenantId);
    }
  }

  @Test
  void aUsernameTakenInAnotherTenantIsRefusedLeavingNoAgentStandingAmountOrUserBehind()
      throws Exception {
    OtherTenantLogin otherTenantLogin =
        otherTenantFixture.managerLoginInAnotherTenant(
            "cross-tenant-taken-" + UUID.randomUUID() + "@example.com", "Different#Passw0rd1");
    otherTenantId = otherTenantLogin.tenantId();
    otherTenantUsername = otherTenantLogin.username();

    String token = managerToken();
    String name = NAME_PREFIX + "Other Tenant Name Clash";
    Snapshot before = snapshot();

    postJson("/api/agents", token, agentBody(name, otherTenantUsername))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agents WHERE name = ?", Long.class, name))
        .as("Agent rows named %s", name)
        .isZero();
    assertThat(snapshot()).isEqualTo(before);

    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isEmpty());
  }

  private record Snapshot(long agents, long standingAmounts, long users) {}

  private Snapshot snapshot() {
    return new Snapshot(
        count("SELECT COUNT(*) FROM agents"),
        count("SELECT COUNT(*) FROM agent_standing_amounts"),
        count("SELECT COUNT(*) FROM users"));
  }

  private long count(String sql) {
    return jdbcTemplate.queryForObject(sql, Long.class);
  }

  private Map<String, Object> agentBody(String name, String username) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("country", "FRANCE");
    body.put("salaryAmount", 2000);
    body.put("username", username);
    body.put("password", PASSWORD);
    return body;
  }
}

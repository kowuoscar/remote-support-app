package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantLogin;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * The Tester path's own username conflict, given the shape the Agent path already has
 * (refuse-taken-username-on-tester-login ticket): a pre-check refuses a username already taken
 * anywhere in the deployment, told apart by {@code code} from the pre-existing primary-contact
 * conflict (globally-unique-usernames spec.md "The Tester path's asymmetry is fixed here, not
 * left"). Runs in the default rolled-back test transaction — unlike {@link
 * CrossTenantUsernameAgentCreationApiTest}, nothing here ever reaches the flush-time {@code
 * DataIntegrityViolationException} catch for real: the pre-check answers first, so there is no
 * broken statement to poison the shared transaction. Row counts are read through the
 * repositories, not raw JDBC — a repository count runs a JPQL query, which Hibernate flushes the
 * pending session against first, unlike a plain {@code JdbcTemplate} query on the same
 * connection, which would not see an unflushed write.
 */
@Import(OtherTenantFixture.class)
class TesterUsernameConflictApiTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";

  @Autowired private OtherTenantFixture otherTenantFixture;
  @Autowired private UserRepository userRepository;
  @Autowired private TesterRepository testerRepository;

  @Test
  void aUsernameTakenInAnotherTenantIsRefusedLeavingNoTesterOrUserBehind() throws Exception {
    OtherTenantLogin otherTenantLogin =
        otherTenantFixture.managerLoginInAnotherTenant(
            "cross-tenant-tester-" + UUID.randomUUID() + "@example.com", "Different#Passw0rd1");

    String token = managerToken();
    UUID clientId = createClient(token, "Cross Tenant Tester Client " + UUID.randomUUID());
    long usersBefore = countUsers();
    long testersBefore = countTesters();

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest(otherTenantLogin.username(), PASSWORD, false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
        .andExpect(
            jsonPath("$.message")
                .value("That email is already in use. Choose another one and try again."));

    assertThat(countUsers()).isEqualTo(usersBefore);
    assertThat(countTesters()).isEqualTo(testersBefore);
  }

  @Test
  void aSameTenantUsernameAlreadyTakenIsRefusedWithTheSameCode() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Same Tenant Tester Client " + UUID.randomUUID());
    String username = "same-tenant-taken-" + UUID.randomUUID() + "@example.com";

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest(username, PASSWORD, false))
        .andExpect(status().isCreated());
    long usersAfterFirst = countUsers();
    long testersAfterFirst = countTesters();

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest(username, PASSWORD, false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

    assertThat(countUsers()).isEqualTo(usersAfterFirst);
    assertThat(countTesters()).isEqualTo(testersAfterFirst);
  }

  @Test
  void aSecondPrimaryContactTesterIsRefusedWithADistinctCodeFromUsernameTaken() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Distinct Code Client " + UUID.randomUUID());

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest(
                "first-primary-" + UUID.randomUUID() + "@example.com", PASSWORD, true))
        .andExpect(status().isCreated());
    long usersAfterFirst = countUsers();
    long testersAfterFirst = countTesters();

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest(
                "second-primary-" + UUID.randomUUID() + "@example.com", PASSWORD, true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRIMARY_CONTACT_EXISTS"));

    assertThat(countUsers()).isEqualTo(usersAfterFirst);
    assertThat(countTesters()).isEqualTo(testersAfterFirst);
  }

  @Test
  void surroundingWhitespaceIsStrippedBeforeItIsStoredOrChecked() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Whitespace Tester Client " + UUID.randomUUID());
    String clean = "whitespace-tester-" + UUID.randomUUID() + "@example.com";

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest("  " + clean + "  ", PASSWORD, false))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value(clean));

    postJson(
            "/api/clients/" + clientId + "/testers",
            token,
            new TesterCreateRequest(clean.toUpperCase(), PASSWORD, false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
  }

  private long countUsers() {
    return userRepository.count();
  }

  private long countTesters() {
    return testerRepository.count();
  }
}

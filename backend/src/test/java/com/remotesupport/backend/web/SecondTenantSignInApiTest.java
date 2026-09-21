package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantLogin;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Signing in against a second Tenant's own login, and that Tenant's Manager reading only its own
 * data afterwards (second-tenant-test-seam spec, stories 1-6, 9, 10) — the seam the epic's
 * {@code globally-unique-usernames} feature needs before it can turn a red test green. Both
 * cases here use a username that exists nowhere else; the colliding-username case is
 * {@code pin-colliding-username-sign-in}'s.
 */
@Import(OtherTenantFixture.class)
class SecondTenantSignInApiTest extends IntegrationTest {

  @Autowired private OtherTenantFixture otherTenantFixture;

  @Test
  void signingInWithASecondTenantsOnlyLoginNamesThatTenant() throws Exception {
    OtherTenantLogin login =
        otherTenantFixture.managerLoginInAnotherTenant("second-tenant-manager@example.com", "Passw0rd!23");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","password":"%s"}
                        """
                            .formatted(login.username(), login.password())))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    UUID tenantId = tenantIdOf(body.get("token").asText());

    assertThat(tenantId).isEqualTo(login.tenantId());
    assertThat(tenantId).isNotEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  }

  @Test
  void aSecondTenantsManagerReadsOnlyThatTenantsOwnClients() throws Exception {
    OtherTenantLogin login =
        otherTenantFixture.managerLoginInAnotherTenant("second-tenant-clients-manager@example.com", "Passw0rd!23");
    String secondTenantManagerToken = loginAs(login.username(), login.password());

    JsonNode clients =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/clients").header("Authorization", "Bearer " + secondTenantManagerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(clients).hasSize(1);
    assertThat(clients.get(0).get("id").asText()).isEqualTo(login.clientId().toString());
  }
}

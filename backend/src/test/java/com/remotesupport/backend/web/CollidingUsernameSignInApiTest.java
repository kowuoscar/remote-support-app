package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Pins today's (`main`'s) defective behaviour for a username that exists in two Tenants
 * (second-tenant-test-seam spec, "The pinning test, precisely"; stories 7, 8) — a record of the
 * defect, not a regression net.
 *
 * <p><b>Observed vs. predicted.</b> The epic's feature line predicts a colliding username
 * "authenticates against whichever row is found first". Run against this ticket's fixture,
 * {@code POST /api/auth/login} instead returns {@code 401 Unauthorized} with an empty body: the
 * two matching rows make {@code UserRepository.findByUsername}'s derived {@code Optional} query
 * fail rather than pick one, and that failure surfaces through Spring Security as an
 * authentication failure — not as a 200 naming the wrong Tenant. This test pins that observed
 * 401, and only that.
 *
 * <p><b>Deleted by {@code globally-unique-usernames}.</b> That feature adds a global unique index
 * on username, above the Tenant-scoped {@code uq_users_tenant_username}; at that point the
 * fixture's second insert of a colliding username fails at flush, before this test can even reach
 * its assertion. It is deleted and replaced, not adapted — its replacement asserts that creating
 * a login whose username is already taken in another Tenant is refused.
 */
@Import(OtherTenantFixture.class)
class CollidingUsernameSignInApiTest extends IntegrationTest {

  @Autowired private OtherTenantFixture otherTenantFixture;

  @Test
  void collidingUsernameDoesNotSignTheCallerInToTheSecondTenant() throws Exception {
    OtherTenantLogin collidingLogin =
        otherTenantFixture.managerLoginInAnotherTenant(MANAGER_USERNAME, "Different#Passw0rd1");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"%s"}
                    """
                        .formatted(collidingLogin.username(), collidingLogin.password())))
        .andExpect(status().isUnauthorized());
  }

  /**
   * Control for the test above: the same fixture method, called with a username that collides
   * with nothing, still signs in 200 — so the 401 above can only be attributed to the collision
   * itself, never to a mistyped password or a broken fixture call.
   */
  @Test
  void aNonCollidingLoginFromTheSameFixtureStillSignsIn() throws Exception {
    OtherTenantLogin cleanLogin =
        otherTenantFixture.managerLoginInAnotherTenant(
            "colliding-username-control@example.com", "Different#Passw0rd1");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"%s"}
                    """
                        .formatted(cleanLogin.username(), cleanLogin.password())))
        .andExpect(status().isOk());
  }
}

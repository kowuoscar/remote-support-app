package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Carrier catalog (carrier-catalog spec; agent-maintains-carriers ticket): a Country's
 * Carriers, maintained by that Country's Agents and by the Company Manager. The seeded Agent
 * (agent@example.com, "Jordan Ellis") is in the United States, so an Agent's own Country here is
 * always UNITED_STATES.
 */
@Import(OtherTenantFixture.class)
class CarrierApiTest extends IntegrationTest {

  @Autowired private OtherTenantFixture otherTenantFixture;

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createCarrier(String token, String country, String name) throws Exception {
    return idOf(
        postJson("/api/carriers", token, Map.of("country", country, "name", name))
            .andExpect(status().isCreated())
            .andReturn());
  }

  @Test
  void anAgentCreatesACarrierInTheirOwnCountryAndSeesItListed() throws Exception {
    String agentToken = agentToken();

    postJson("/api/carriers", agentToken, Map.of("country", "UNITED_STATES", "name", "  Mint Mobile "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Mint Mobile"))
        .andExpect(jsonPath("$.country").value("UNITED_STATES"))
        .andExpect(jsonPath("$.archivedAt").doesNotExist());

    // No country given: an Agent's catalog is their own Country's.
    mockMvc
        .perform(get("/api/carriers").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("UNITED_STATES"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.carriers[*].name", hasItem("Mint Mobile")));
  }

  @Test
  void theSeededCatalogHoldsSeveralCarriersForTheSeededAgentsCountryOneOfThemArchived()
      throws Exception {
    String agentToken = agentToken();

    mockMvc
        .perform(get("/api/carriers").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.carriers[*].name", hasItem("Verizon")))
        .andExpect(jsonPath("$.carriers[*].name", hasItem("T-Mobile")))
        .andExpect(jsonPath("$.carriers[*].name", hasItem("AT&T")))
        .andExpect(jsonPath("$.carriers[*].name", not(hasItem("Sprint"))));

    mockMvc
        .perform(
            get("/api/carriers")
                .param("includeArchived", "true")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.carriers[?(@.name == 'Sprint')].archivedAt").isNotEmpty());
  }

  @Test
  void anAgentRenamesACarrier() throws Exception {
    String agentToken = agentToken();
    UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Cricket Wirless");

    patchJson("/api/carriers/" + carrierId, agentToken, Map.of("name", "Cricket Wireless"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(carrierId.toString()))
        .andExpect(jsonPath("$.name").value("Cricket Wireless"));

    mockMvc
        .perform(get("/api/carriers").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.carriers[*].name", hasItem("Cricket Wireless")))
        .andExpect(jsonPath("$.carriers[*].name", not(hasItem("Cricket Wirless"))));
  }

  @Test
  void anArchivedCarrierLeavesTheDefaultListButStaysInTheArchivedList() throws Exception {
    String agentToken = agentToken();
    UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Boost Mobile");

    postJson("/api/carriers/" + carrierId + "/archive", agentToken, Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());

    mockMvc
        .perform(get("/api/carriers").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.carriers[*].name", not(hasItem("Boost Mobile"))));
    mockMvc
        .perform(
            get("/api/carriers")
                .param("includeArchived", "true")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.carriers[?(@.name == 'Boost Mobile')].archivedAt").isNotEmpty());
  }

  @Test
  void anArchivedCarrierCanNeitherBeRenamedNorArchivedAgain() throws Exception {
    String agentToken = agentToken();
    UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Visible");
    postJson("/api/carriers/" + carrierId + "/archive", agentToken, Map.of()).andExpect(status().isOk());

    patchJson("/api/carriers/" + carrierId, agentToken, Map.of("name", "Visible Wireless"))
        .andExpect(status().isConflict());
    postJson("/api/carriers/" + carrierId + "/archive", agentToken, Map.of())
        .andExpect(status().isConflict());
  }

  @Test
  void aDuplicateActiveNameInTheSameCountryIsRefusedIgnoringCase() throws Exception {
    String agentToken = agentToken();
    createCarrier(agentToken, "UNITED_STATES", "US Cellular");

    postJson("/api/carriers", agentToken, Map.of("country", "UNITED_STATES", "name", "us cellular"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARRIER_NAME_TAKEN"));

    UUID otherId = createCarrier(agentToken, "UNITED_STATES", "Consumer Cellular");
    patchJson("/api/carriers/" + otherId, agentToken, Map.of("name", "US Cellular"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARRIER_NAME_TAKEN"));
  }

  @Test
  void renamingACarrierToItsOwnNameIsAllowed() throws Exception {
    String agentToken = agentToken();
    UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Ting");

    patchJson("/api/carriers/" + carrierId, agentToken, Map.of("name", "TING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("TING"));
  }

  @Test
  void anArchivedCarriersNameCanBeReused() throws Exception {
    String agentToken = agentToken();
    UUID archivedId = createCarrier(agentToken, "UNITED_STATES", "Tracfone");
    postJson("/api/carriers/" + archivedId + "/archive", agentToken, Map.of()).andExpect(status().isOk());

    postJson("/api/carriers", agentToken, Map.of("country", "UNITED_STATES", "name", "Tracfone"))
        .andExpect(status().isCreated());
  }

  @Test
  void theSameNameMayExistInTwoCountries() throws Exception {
    String managerToken = managerToken();
    createCarrier(managerToken, "FRANCE", "Orange");

    postJson("/api/carriers", managerToken, Map.of("country", "SPAIN", "name", "Orange"))
        .andExpect(status().isCreated());
  }

  @Test
  void aBlankNameIsRefused() throws Exception {
    postJson("/api/carriers", agentToken(), Map.of("country", "UNITED_STATES", "name", "   "))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentCannotReadOrWriteAnotherCountrysCarriers() throws Exception {
    String managerToken = managerToken();
    UUID mexicanCarrier = createCarrier(managerToken, "MEXICO", "Telcel");
    String agentToken = agentToken();

    mockMvc
        .perform(get("/api/carriers").param("country", "MEXICO").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());
    postJson("/api/carriers", agentToken, Map.of("country", "MEXICO", "name", "Movistar"))
        .andExpect(status().isForbidden());
    patchJson("/api/carriers/" + mexicanCarrier, agentToken, Map.of("name", "Telcel Mexico"))
        .andExpect(status().isForbidden());
    postJson("/api/carriers/" + mexicanCarrier + "/archive", agentToken, Map.of())
        .andExpect(status().isForbidden());
  }

  @Test
  void anAgentMayNameTheirOwnCountryExplicitly() throws Exception {
    mockMvc
        .perform(
            get("/api/carriers").param("country", "UNITED_STATES").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("UNITED_STATES"));
  }

  @Test
  void theManagerMaintainsAnyCountrysCarriers() throws Exception {
    String managerToken = managerToken();
    UUID carrierId = createCarrier(managerToken, "PHILIPPINES", "Globe");

    patchJson("/api/carriers/" + carrierId, managerToken, Map.of("name", "Globe Telecom"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/carriers").param("country", "PHILIPPINES").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("PHP"))
        .andExpect(jsonPath("$.carriers[*].name", hasItem("Globe Telecom")));

    postJson("/api/carriers/" + carrierId + "/archive", managerToken, Map.of()).andExpect(status().isOk());

    // The Manager also reaches the Agent's Country.
    mockMvc
        .perform(
            get("/api/carriers").param("country", "UNITED_STATES").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.carriers[*].name", hasItem("Verizon")));
  }

  @Test
  void theManagerMustNameACountry() throws Exception {
    String managerToken = managerToken();
    mockMvc
        .perform(get("/api/carriers").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest());
    postJson("/api/carriers", managerToken, Map.of("name", "Nowhere Mobile"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTesterIsRefusedOnEveryCarrierRoute() throws Exception {
    UUID seededCarrier = createCarrier(managerToken(), "UNITED_STATES", "Google Fi");

    for (String token : new String[] {testerToken(), linkedTesterToken()}) {
      mockMvc
          .perform(
              get("/api/carriers").param("country", "UNITED_STATES").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
      postJson("/api/carriers", token, Map.of("country", "UNITED_STATES", "name", "Tester Mobile"))
          .andExpect(status().isForbidden());
      patchJson("/api/carriers/" + seededCarrier, token, Map.of("name", "Renamed"))
          .andExpect(status().isForbidden());
      postJson("/api/carriers/" + seededCarrier + "/archive", token, Map.of())
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void anotherTenantsCarrierIsNotFound() throws Exception {
    UUID foreignCarrier = otherTenantFixture.carrierInAnotherTenant();

    for (String token : new String[] {managerToken(), agentToken()}) {
      patchJson("/api/carriers/" + foreignCarrier, token, Map.of("name", "Hijacked"))
          .andExpect(status().isNotFound());
      postJson("/api/carriers/" + foreignCarrier + "/archive", token, Map.of())
          .andExpect(status().isNotFound());
    }

    mockMvc
        .perform(
            get("/api/carriers")
                .param("country", "UNITED_STATES")
                .param("includeArchived", "true")
                .header("Authorization", "Bearer " + managerToken()))
        .andExpect(jsonPath("$.carriers[*].name", not(hasItem("Other Tenant Wireless"))));
  }

  @Test
  void noRouteDeletesACarrier() throws Exception {
    String managerToken = managerToken();
    UUID carrierId = createCarrier(managerToken, "UNITED_STATES", "Straight Talk");

    mockMvc
        .perform(delete("/api/carriers/" + carrierId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isMethodNotAllowed());

    mockMvc
        .perform(
            get("/api/carriers").param("country", "UNITED_STATES").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.carriers[*].name", hasItem("Straight Talk")));
  }

  @Test
  void creatingRenamingAndArchivingACarrierAreAudited() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String agentToken = agentToken();
      UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Metro");
      patchJson("/api/carriers/" + carrierId, agentToken, Map.of("name", "Metro by T-Mobile"))
          .andExpect(status().isOk());
      postJson("/api/carriers/" + carrierId + "/archive", agentToken, Map.of()).andExpect(status().isOk());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged)
          .contains("action=CARRIER_CREATED entity=Carrier entityId=" + carrierId + " country=UNITED_STATES")
          .contains("action=CARRIER_RENAMED entity=Carrier entityId=" + carrierId)
          .contains("oldName=Metro newName=Metro by T-Mobile")
          .contains("action=CARRIER_ARCHIVED entity=Carrier entityId=" + carrierId)
          .contains("actorUserId=33333333-3333-3333-3333-333333333333")
          .contains("tenantId=11111111-1111-1111-1111-111111111111");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  private ResultActions patchJson(String url, String token, Object body) throws Exception {
    return mockMvc.perform(
        patch(url)
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
  }
}

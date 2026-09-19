package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Agent Stock read (returns-and-agent-stock spec, Solution's Agent Stock; agent-stock ticket
 * ACs: "Agent sees their own Stock", "Manager's ... filterable by Agent", "an Agent sees only
 * their own Stock; a Tester gets 403; another tenant's Stock is invisible"). Mirrors {@link
 * ReturnRequestsApiTest}'s fixture shape for putting a unit into Stock in the first place.
 */
@Import(OtherTenantFixture.class)
class StockApiTest extends IntegrationTest {

  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;
  private String agentToken;
  private String testerToken;
  private UUID contractId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Stockholm Fixtures Ltd");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "ines.moreau@stockholm.example", "Passw0rd!23");
    agentToken = agentToken();
  }

  @Test
  void anEmptyStockReadsAsAnEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/stock").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));
  }

  @Test
  void theAgentSeesTheirOwnStockUnit() throws Exception {
    UUID smartphoneId = keepSmartphoneInStock("Pixel 8");

    JsonNode stock = readStock(agentToken, null);
    assertThat(idsOf(stock)).contains(smartphoneId.toString());
  }

  @Test
  void aTesterGets403OnStock() throws Exception {
    mockMvc
        .perform(get("/api/stock").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void theManagerSeesEveryAgentsStockUnfiltered() throws Exception {
    UUID smartphoneId = keepSmartphoneInStock("Pixel 8");

    JsonNode stock = readStock(managerToken, null);
    assertThat(idsOf(stock)).contains(smartphoneId.toString());
  }

  @Test
  void theManagerCanFilterStockByAgent() throws Exception {
    UUID smartphoneId = keepSmartphoneInStock("Pixel 8");
    UUID otherAgentId = createAgent(managerToken, "Riley Oduya", Country.UNITED_KINGDOM);

    JsonNode filteredToOwnAgent = readStock(managerToken, SEEDED_AGENT_ID);
    assertThat(idsOf(filteredToOwnAgent)).contains(smartphoneId.toString());

    JsonNode filteredToOtherAgent = readStock(managerToken, otherAgentId);
    assertThat(idsOf(filteredToOtherAgent)).doesNotContain(smartphoneId.toString());
  }

  @Test
  void anotherTenantsStockIsInvisibleToTheManager() throws Exception {
    UUID otherTenantSmartphoneId = otherTenantFixture.stockSmartphoneInAnotherTenant();

    JsonNode stock = readStock(managerToken, null);
    assertThat(idsOf(stock)).doesNotContain(otherTenantSmartphoneId.toString());
  }

  @Test
  void theStockUnitCarriesTheContractItCameFrom() throws Exception {
    UUID smartphoneId = keepSmartphoneInStock("Pixel 8");

    JsonNode stock = readStock(agentToken, null);
    JsonNode unit = unitById(stock, smartphoneId);
    assertThat(unit.get("fromContractId").asText()).isEqualTo(contractId.toString());
    assertThat(unit.get("agentId").asText()).isEqualTo(SEEDED_AGENT_ID.toString());
  }

  // --- helpers ------------------------------------------------------------------------------

  /** Puts a fresh company-owned Smartphone into the seeded Agent's Stock via a completed Return. */
  private UUID keepSmartphoneInStock(String model) throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, model);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    JsonNode returnedUnits =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("returnedUnits");
    UUID unitId = UUID.fromString(returnedUnits.get(0).get("id").asText());

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"KEPT_IN_STOCK\"}]}"
                        .formatted(unitId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk());

    return smartphoneId;
  }

  private JsonNode readStock(String token, UUID agentId) throws Exception {
    String url = agentId == null ? "/api/stock" : "/api/stock?agentId=" + agentId;
    MvcResult result =
        mockMvc
            .perform(get(url).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private java.util.List<String> idsOf(JsonNode stock) {
    java.util.List<String> ids = new java.util.ArrayList<>();
    stock.forEach(unit -> ids.add(unit.get("id").asText()));
    return ids;
  }

  private JsonNode unitById(JsonNode stock, UUID id) {
    for (JsonNode unit : stock) {
      if (unit.get("id").asText().equals(id.toString())) {
        return unit;
      }
    }
    throw new IllegalStateException("No Stock unit with id " + id);
  }
}

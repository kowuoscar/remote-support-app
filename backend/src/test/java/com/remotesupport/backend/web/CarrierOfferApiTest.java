package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A Carrier's Topup Options and Postpaid Plans (carrier-catalog spec; topup-options-and-postpaid-
 * plans ticket). The two lists behave identically — a name and a price in the Country's currency,
 * archived rather than deleted — so most cases run once per list, named by its URL segment. The
 * seeded Agent (agent@example.com) is in the United States; AT&T, T-Mobile and Verizon are its
 * seeded active Carriers.
 */
@Import(OtherTenantFixture.class)
class CarrierOfferApiTest extends IntegrationTest {

  private static final UUID SEEDED_ATT = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID SEEDED_TMOBILE = UUID.fromString("c0000000-0000-0000-0000-000000000002");
  private static final UUID SEEDED_VERIZON = UUID.fromString("c0000000-0000-0000-0000-000000000003");

  @Autowired private OtherTenantFixture otherTenantFixture;

  private static String listUrl(UUID carrierId, String list) {
    return "/api/carriers/" + carrierId + "/" + list;
  }

  private static String entryUrl(UUID carrierId, String list, UUID entryId) {
    return listUrl(carrierId, list) + "/" + entryId;
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anAgentAddsAnEntryToACarrierOfTheirCountryAndSeesItListed(String list) throws Exception {
    String agentToken = agentToken();

    postJson(listUrl(SEEDED_ATT, list), agentToken, Map.of("name", "  Unlimited 50 ", "price", "50.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Unlimited 50"))
        .andExpect(jsonPath("$.price").value(50.00))
        .andExpect(jsonPath("$.carrierId").value(SEEDED_ATT.toString()))
        .andExpect(jsonPath("$.archivedAt").doesNotExist());

    mockMvc
        .perform(get(listUrl(SEEDED_ATT, list)).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", hasItem("Unlimited 50")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anAgentEditsAnEntrysNameAndPrice(String list) throws Exception {
    String agentToken = agentToken();
    UUID entryId = createEntry(agentToken, SEEDED_ATT, list, "Starter", "20.00");

    patchJson(entryUrl(SEEDED_ATT, list, entryId), agentToken, Map.of("name", "Starter Plus", "price", "22.50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(entryId.toString()))
        .andExpect(jsonPath("$.name").value("Starter Plus"))
        .andExpect(jsonPath("$.price").value(22.50));

    mockMvc
        .perform(get(listUrl(SEEDED_ATT, list)).header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[?(@.name == 'Starter Plus')].price", hasItem(22.50)))
        .andExpect(jsonPath("$[*].name", not(hasItem("Starter"))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anArchivedEntryLeavesTheDefaultListButStaysInTheArchivedList(String list) throws Exception {
    String agentToken = agentToken();
    UUID entryId = createEntry(agentToken, SEEDED_ATT, list, "Legacy 10", "10.00");

    postJson(entryUrl(SEEDED_ATT, list, entryId) + "/archive", agentToken, Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());

    mockMvc
        .perform(get(listUrl(SEEDED_ATT, list)).header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[*].name", not(hasItem("Legacy 10"))));
    mockMvc
        .perform(
            get(listUrl(SEEDED_ATT, list))
                .param("includeArchived", "true")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[?(@.name == 'Legacy 10')].archivedAt").isNotEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anArchivedEntryCanNeitherBeEditedNorArchivedAgain(String list) throws Exception {
    String agentToken = agentToken();
    UUID entryId = createEntry(agentToken, SEEDED_ATT, list, "Retired", "15.00");
    postJson(entryUrl(SEEDED_ATT, list, entryId) + "/archive", agentToken, Map.of())
        .andExpect(status().isOk());

    patchJson(entryUrl(SEEDED_ATT, list, entryId), agentToken, Map.of("name", "Revived", "price", "15.00"))
        .andExpect(status().isConflict());
    postJson(entryUrl(SEEDED_ATT, list, entryId) + "/archive", agentToken, Map.of())
        .andExpect(status().isConflict());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void aZeroNegativeOrMissingPriceIsRefused(String list) throws Exception {
    String agentToken = agentToken();

    postJson(listUrl(SEEDED_ATT, list), agentToken, Map.of("name", "Free", "price", "0"))
        .andExpect(status().isBadRequest());
    postJson(listUrl(SEEDED_ATT, list), agentToken, Map.of("name", "Refund", "price", "-5.00"))
        .andExpect(status().isBadRequest());
    postJson(listUrl(SEEDED_ATT, list), agentToken, Map.of("name", "Unpriced"))
        .andExpect(status().isBadRequest());

    UUID entryId = createEntry(agentToken, SEEDED_ATT, list, "Priced", "30.00");
    patchJson(entryUrl(SEEDED_ATT, list, entryId), agentToken, Map.of("name", "Priced", "price", "0.00"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void aBlankNameIsRefused(String list) throws Exception {
    postJson(listUrl(SEEDED_ATT, list), agentToken(), Map.of("name", "  ", "price", "5.00"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void aDuplicateActiveNameUnderTheSameCarrierIsRefusedIgnoringCase(String list) throws Exception {
    String agentToken = agentToken();
    String code = list.equals("topup-options") ? "TOPUP_OPTION_NAME_TAKEN" : "POSTPAID_PLAN_NAME_TAKEN";
    createEntry(agentToken, SEEDED_ATT, list, "Family Share", "80.00");

    postJson(listUrl(SEEDED_ATT, list), agentToken, Map.of("name", "family share", "price", "81.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(code));

    UUID otherId = createEntry(agentToken, SEEDED_ATT, list, "Solo", "40.00");
    patchJson(entryUrl(SEEDED_ATT, list, otherId), agentToken, Map.of("name", "Family Share", "price", "40.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(code));

    // Keeping its own name while changing the price is not a clash.
    patchJson(entryUrl(SEEDED_ATT, list, otherId), agentToken, Map.of("name", "SOLO", "price", "45.00"))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void aNameMayBeReusedAfterArchivingOrUnderAnotherCarrier(String list) throws Exception {
    String agentToken = agentToken();
    UUID archivedId = createEntry(agentToken, SEEDED_ATT, list, "Weekend Pass", "12.00");
    postJson(entryUrl(SEEDED_ATT, list, archivedId) + "/archive", agentToken, Map.of())
        .andExpect(status().isOk());

    postJson(listUrl(SEEDED_ATT, list), agentToken, Map.of("name", "Weekend Pass", "price", "12.00"))
        .andExpect(status().isCreated());
    postJson(listUrl(SEEDED_TMOBILE, list), agentToken, Map.of("name", "Weekend Pass", "price", "12.00"))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void nothingCanBeAddedToOrChangedUnderAnArchivedCarrier(String list) throws Exception {
    String agentToken = agentToken();
    UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Ultra Mobile");
    UUID entryId = createEntry(agentToken, carrierId, list, "Ultra 30", "30.00");
    postJson("/api/carriers/" + carrierId + "/archive", agentToken, Map.of()).andExpect(status().isOk());

    postJson(listUrl(carrierId, list), agentToken, Map.of("name", "Ultra 40", "price", "40.00"))
        .andExpect(status().isConflict());
    patchJson(entryUrl(carrierId, list, entryId), agentToken, Map.of("name", "Ultra 35", "price", "35.00"))
        .andExpect(status().isConflict());
    postJson(entryUrl(carrierId, list, entryId) + "/archive", agentToken, Map.of())
        .andExpect(status().isConflict());

    // Still readable: whatever already uses it keeps showing it.
    mockMvc
        .perform(get(listUrl(carrierId, list)).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", hasItem("Ultra 30")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void theManagerMaintainsAnyCountrysEntries(String list) throws Exception {
    String managerToken = managerToken();
    UUID carrierId = createCarrier(managerToken, "PHILIPPINES", "Smart");
    UUID entryId = createEntry(managerToken, carrierId, list, "Giga 99", "99.00");

    patchJson(entryUrl(carrierId, list, entryId), managerToken, Map.of("name", "Giga 99+", "price", "109.00"))
        .andExpect(status().isOk());
    postJson(entryUrl(carrierId, list, entryId) + "/archive", managerToken, Map.of())
        .andExpect(status().isOk());

    // The Manager also reaches the Agent's Country.
    postJson(listUrl(SEEDED_ATT, list), managerToken, Map.of("name", "Manager Pick", "price", "9.00"))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anAgentCannotReadOrWriteAnotherCountrysEntries(String list) throws Exception {
    String managerToken = managerToken();
    UUID mexicanCarrier = createCarrier(managerToken, "MEXICO", "Telcel");
    UUID entryId = createEntry(managerToken, mexicanCarrier, list, "Amigo 100", "100.00");
    String agentToken = agentToken();

    mockMvc
        .perform(get(listUrl(mexicanCarrier, list)).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());
    postJson(listUrl(mexicanCarrier, list), agentToken, Map.of("name", "Amigo 200", "price", "200.00"))
        .andExpect(status().isForbidden());
    patchJson(entryUrl(mexicanCarrier, list, entryId), agentToken, Map.of("name", "Amigo", "price", "1.00"))
        .andExpect(status().isForbidden());
    postJson(entryUrl(mexicanCarrier, list, entryId) + "/archive", agentToken, Map.of())
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void aTesterIsRefusedOnEveryRoute(String list) throws Exception {
    UUID entryId = createEntry(managerToken(), SEEDED_ATT, list, "Tester Bait", "5.00");

    for (String token : new String[] {testerToken(), linkedTesterToken()}) {
      mockMvc
          .perform(get(listUrl(SEEDED_ATT, list)).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
      postJson(listUrl(SEEDED_ATT, list), token, Map.of("name", "Tester Pick", "price", "5.00"))
          .andExpect(status().isForbidden());
      patchJson(entryUrl(SEEDED_ATT, list, entryId), token, Map.of("name", "Renamed", "price", "5.00"))
          .andExpect(status().isForbidden());
      postJson(entryUrl(SEEDED_ATT, list, entryId) + "/archive", token, Map.of())
          .andExpect(status().isForbidden());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anotherTenantsCarrierIsNotFound(String list) throws Exception {
    UUID foreignCarrier = otherTenantFixture.carrierInAnotherTenant();
    UUID someEntry = UUID.randomUUID();

    for (String token : new String[] {managerToken(), agentToken()}) {
      mockMvc
          .perform(get(listUrl(foreignCarrier, list)).header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
      postJson(listUrl(foreignCarrier, list), token, Map.of("name", "Hijack", "price", "1.00"))
          .andExpect(status().isNotFound());
      patchJson(entryUrl(foreignCarrier, list, someEntry), token, Map.of("name", "Hijack", "price", "1.00"))
          .andExpect(status().isNotFound());
      postJson(entryUrl(foreignCarrier, list, someEntry) + "/archive", token, Map.of())
          .andExpect(status().isNotFound());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void anEntryIsOnlyReachableUnderItsOwnCarrier(String list) throws Exception {
    String agentToken = agentToken();
    UUID entryId = createEntry(agentToken, SEEDED_ATT, list, "Only on AT&T", "7.00");

    patchJson(entryUrl(SEEDED_TMOBILE, list, entryId), agentToken, Map.of("name", "Moved", "price", "7.00"))
        .andExpect(status().isNotFound());
    postJson(entryUrl(SEEDED_TMOBILE, list, entryId) + "/archive", agentToken, Map.of())
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"topup-options", "postpaid-plans"})
  void noRouteDeletesAnEntry(String list) throws Exception {
    String agentToken = agentToken();
    UUID entryId = createEntry(agentToken, SEEDED_ATT, list, "Keeper", "11.00");

    mockMvc
        .perform(delete(entryUrl(SEEDED_ATT, list, entryId)).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isMethodNotAllowed());

    mockMvc
        .perform(get(listUrl(SEEDED_ATT, list)).header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[*].name", hasItem("Keeper")));
  }

  @Test
  void theCatalogCarriesEachCarriersActiveEntriesAndArchivedOnesOnRequest() throws Exception {
    String agentToken = agentToken();
    UUID carrierId = createCarrier(agentToken, "UNITED_STATES", "Red Pocket");
    createEntry(agentToken, carrierId, "topup-options", "Refill 25", "25.00");
    UUID oldOption = createEntry(agentToken, carrierId, "topup-options", "Refill 5", "5.00");
    postJson(entryUrl(carrierId, "topup-options", oldOption) + "/archive", agentToken, Map.of())
        .andExpect(status().isOk());
    createEntry(agentToken, carrierId, "postpaid-plans", "Unlimited", "60.00");

    String carrier = "$.carriers[?(@.name == 'Red Pocket')]";
    mockMvc
        .perform(get("/api/carriers").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath(carrier + ".topupOptions[*].name", hasItem("Refill 25")))
        .andExpect(jsonPath(carrier + ".topupOptions[*].name", not(hasItem("Refill 5"))))
        .andExpect(jsonPath(carrier + ".topupOptions[*].price", hasItem(25.00)))
        .andExpect(jsonPath(carrier + ".postpaidPlans[*].name", hasItem("Unlimited")));

    mockMvc
        .perform(
            get("/api/carriers").param("includeArchived", "true").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath(carrier + ".topupOptions[*].name", hasItem("Refill 5")));
  }

  @Test
  void theSeededCatalogGivesEachActiveCarrierOptionsAndPlansSomeArchived() throws Exception {
    String agentToken = agentToken();

    for (UUID carrierId : new UUID[] {SEEDED_ATT, SEEDED_TMOBILE, SEEDED_VERIZON}) {
      for (String list : new String[] {"topup-options", "postpaid-plans"}) {
        mockMvc
            .perform(get(listUrl(carrierId, list)).header("Authorization", "Bearer " + agentToken))
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
      }
    }
    for (String field : new String[] {"topupOptions", "postpaidPlans"}) {
      mockMvc
          .perform(
              get("/api/carriers").param("includeArchived", "true").header("Authorization", "Bearer " + agentToken))
          .andExpect(jsonPath("$.carriers[*]." + field + "[?(@.archivedAt)].name", not(empty())));
    }
  }

  @Test
  void creatingEditingAndArchivingEntriesAreAudited() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String agentToken = agentToken();
      UUID optionId = createEntry(agentToken, SEEDED_ATT, "topup-options", "Data 5GB", "15.00");
      patchJson(
              entryUrl(SEEDED_ATT, "topup-options", optionId),
              agentToken,
              Map.of("name", "Data 6GB", "price", "17.50"))
          .andExpect(status().isOk());
      postJson(entryUrl(SEEDED_ATT, "topup-options", optionId) + "/archive", agentToken, Map.of())
          .andExpect(status().isOk());
      UUID planId = createEntry(agentToken, SEEDED_ATT, "postpaid-plans", "Business", "70.00");
      patchJson(
              entryUrl(SEEDED_ATT, "postpaid-plans", planId),
              agentToken,
              Map.of("name", "Business", "price", "72.00"))
          .andExpect(status().isOk());
      postJson(entryUrl(SEEDED_ATT, "postpaid-plans", planId) + "/archive", agentToken, Map.of())
          .andExpect(status().isOk());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged)
          .contains(
              "action=TOPUP_OPTION_CREATED entity=TopupOption entityId=" + optionId + " carrierId=" + SEEDED_ATT)
          .contains("action=TOPUP_OPTION_EDITED entity=TopupOption entityId=" + optionId)
          .contains("oldName=Data 5GB newName=Data 6GB oldPrice=15.00 newPrice=17.50")
          .contains("action=TOPUP_OPTION_ARCHIVED entity=TopupOption entityId=" + optionId)
          .contains("action=POSTPAID_PLAN_CREATED entity=PostpaidPlan entityId=" + planId)
          .contains("action=POSTPAID_PLAN_EDITED entity=PostpaidPlan entityId=" + planId)
          .contains("oldPrice=70.00 newPrice=72.00")
          .contains("action=POSTPAID_PLAN_ARCHIVED entity=PostpaidPlan entityId=" + planId)
          .contains("actorUserId=33333333-3333-3333-3333-333333333333")
          .contains("tenantId=11111111-1111-1111-1111-111111111111");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createCarrier(String token, String country, String name) throws Exception {
    return idOf(
        postJson("/api/carriers", token, Map.of("country", country, "name", name))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private UUID createEntry(String token, UUID carrierId, String list, String name, String price)
      throws Exception {
    return idOf(
        postJson(listUrl(carrierId, list), token, Map.of("name", name, "price", price))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private ResultActions patchJson(String url, String token, Object body) throws Exception {
    return mockMvc.perform(
        patch(url)
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
  }
}

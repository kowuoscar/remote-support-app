package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Reboot and Topup Requests naming their unit, and a Topup its Topup Option
 * (reboot-and-topup-details ticket: request-types-and-flow spec's Details-at-submission table for
 * these two types, the Contract-scoped catalog read, and the Topup Fee pre-fill). One module —
 * {@link com.remotesupport.backend.web.requestdetails.RequestDetailsValidator} — enforces every
 * rule below identically on the Tester path ({@link RequestController#create}), the
 * Agent-proactive Request path (same method), and a proactive Fee's auto-created linking Request
 * ({@link FeeController}) — see {@link TopupFeeFromOptionApiTest} for the last of those three.
 */
class RebootAndTopupDetailsApiTest extends IntegrationTest {

  @Autowired private RequestRepository requestRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private TesterRepository testerRepository;

  private record Fixture(String managerToken, String testerToken, String agentToken, UUID contractId, UUID testerId) {}

  private Fixture aContractWithATester() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "priya.raman@aurora.example");
    return new Fixture(managerToken, testerToken, agentToken, contractId, testerId);
  }

  private void retireSmartphone(String managerToken, UUID contractId, UUID smartphoneId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk());
  }

  private void retireSimCard(String managerToken, UUID contractId, UUID simCardId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk());
  }

  // --- Reboot: target Smartphone ---------------------------------------------------------------

  @Test
  void aTesterSubmittingARebootNamesTheSmartphoneToReboot() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID smartphoneId = createSmartphone(fixture.managerToken(), fixture.contractId(), "Pixel 9");

    Map<String, Object> body = new HashMap<>();
    body.put("type", "REBOOT");
    body.put("targetSmartphoneId", smartphoneId);

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSmartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.targetSmartphoneModel").value("Pixel 9"));
  }

  @Test
  void aRebootWithNoTargetSmartphoneIsRefused() throws Exception {
    Fixture fixture = aContractWithATester();
    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"REBOOT"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aRebootRefusesASmartphoneOfAnotherContract() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID otherContract = createContract(fixture.managerToken(), createClient(fixture.managerToken(), "Solene Cosmetics"), SEEDED_AGENT_ID);
    UUID foreignSmartphoneId = createSmartphone(fixture.managerToken(), otherContract, "iPhone 15");

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"REBOOT","targetSmartphoneId":"%s"}
                    """
                        .formatted(foreignSmartphoneId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aRebootRefusesARetiredSmartphone() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID smartphoneId = createSmartphone(fixture.managerToken(), fixture.contractId(), "Galaxy S24");
    retireSmartphone(fixture.managerToken(), fixture.contractId(), smartphoneId);

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"REBOOT","targetSmartphoneId":"%s"}
                    """
                        .formatted(smartphoneId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentLoggingARebootProactivelyFollowsTheSameRule() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID otherClient = createClient(fixture.managerToken(), "Meridian Logistics");
    UUID otherContract = createContract(fixture.managerToken(), otherClient, SEEDED_AGENT_ID);
    UUID foreignSmartphoneId = createSmartphone(fixture.managerToken(), otherContract, "iPhone 14");

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"REBOOT","testerId":"%s","targetSmartphoneId":"%s"}
                    """
                        .formatted(fixture.testerId(), foreignSmartphoneId)))
        .andExpect(status().isBadRequest());

    UUID ownSmartphoneId = createSmartphone(fixture.managerToken(), fixture.contractId(), "Pixel 8");
    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"REBOOT","testerId":"%s","targetSmartphoneId":"%s"}
                    """
                        .formatted(fixture.testerId(), ownSmartphoneId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSmartphoneId").value(ownSmartphoneId.toString()));
  }

  // --- Topup: target SIM Card + Topup Option -----------------------------------------------------

  @Test
  void aTopupRequiresATopupOptionWhenItsSimCardsCarrierHasAnActiveOne() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Fixture Carrier " + UUID.randomUUID());
    createOption(fixture.managerToken(), carrierId, "Refill 20");
    UUID simCardId = createSimCard(fixture.managerToken(), fixture.contractId(), carrierId);

    // No topupOptionId, no description: refused.
    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s"}
                    """
                        .formatted(simCardId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTopupCanNameTheOptionOfItsSimCardsCarrier() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Fixture Carrier " + UUID.randomUUID());
    UUID optionId = createOption(fixture.managerToken(), carrierId, "Refill 20");
    UUID simCardId = createSimCard(fixture.managerToken(), fixture.contractId(), carrierId);

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s","topupOptionId":"%s"}
                    """
                        .formatted(simCardId, optionId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSimCardId").value(simCardId.toString()))
        .andExpect(jsonPath("$.topupOptionId").value(optionId.toString()))
        .andExpect(jsonPath("$.topupOptionName").value("Refill 20"))
        .andExpect(jsonPath("$.topupOptionPrice").value(20.00));
  }

  @Test
  void aTopupRefusesAnOptionOfAnotherCarrierEvenOfTheSameCountry() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Fixture Carrier " + UUID.randomUUID());
    UUID simCardId = createSimCard(fixture.managerToken(), fixture.contractId(), carrierId);

    UUID otherCarrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Other Carrier " + UUID.randomUUID());
    UUID otherOptionId = createOption(fixture.managerToken(), otherCarrierId, "Refill 30");

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s","topupOptionId":"%s"}
                    """
                        .formatted(simCardId, otherOptionId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTopupRefusesAnArchivedOption() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Fixture Carrier " + UUID.randomUUID());
    UUID optionId = createOption(fixture.managerToken(), carrierId, "Refill 20");
    UUID simCardId = createSimCard(fixture.managerToken(), fixture.contractId(), carrierId);
    archiveOption(fixture.agentToken(), carrierId, optionId);

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s","topupOptionId":"%s"}
                    """
                        .formatted(simCardId, optionId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTopupOnACarrierWithNoActiveOptionsRequiresADescriptionInstead() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID simCardId = createTopupTargetSimCard(fixture.managerToken(), fixture.contractId());

    // No option, no description: refused.
    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s"}
                    """
                        .formatted(simCardId)))
        .andExpect(status().isBadRequest());

    // A description instead of an Option is accepted.
    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s","description":"Cash top-up at kiosk"}
                    """
                        .formatted(simCardId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.topupOptionId").doesNotExist())
        .andExpect(jsonPath("$.description").value("Cash top-up at kiosk"));
  }

  @Test
  void aTopupRefusesASimCardOfAnotherContract() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID otherContract = createContract(fixture.managerToken(), createClient(fixture.managerToken(), "Solene Cosmetics"), SEEDED_AGENT_ID);
    UUID foreignSimCardId = createTopupTargetSimCard(fixture.managerToken(), otherContract);

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s","description":"Top-up"}
                    """
                        .formatted(foreignSimCardId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTopupRefusesARetiredSimCard() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID simCardId = createTopupTargetSimCard(fixture.managerToken(), fixture.contractId());
    retireSimCard(fixture.managerToken(), fixture.contractId(), simCardId);

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.testerToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","targetSimCardId":"%s","description":"Top-up"}
                    """
                        .formatted(simCardId)))
        .andExpect(status().isBadRequest());
  }

  // --- Contract-scoped Carrier catalog read ------------------------------------------------------

  @Test
  void theContractScopedCarrierCatalogIsReadableByTesterAgentAndManagerOfThatContract() throws Exception {
    Fixture fixture = aContractWithATester();

    mockMvc
        .perform(
            get("/api/contracts/" + fixture.contractId() + "/carriers")
                .header("Authorization", "Bearer " + fixture.testerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("UNITED_STATES"));

    mockMvc
        .perform(
            get("/api/contracts/" + fixture.contractId() + "/carriers")
                .header("Authorization", "Bearer " + fixture.agentToken()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + fixture.contractId() + "/carriers")
                .header("Authorization", "Bearer " + fixture.managerToken()))
        .andExpect(status().isOk());
  }

  @Test
  void theContractScopedCarrierCatalogOnlyShowsActiveEntries() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Archived Fixture " + UUID.randomUUID());
    archiveCarrier(fixture.managerToken(), carrierId);

    mockMvc
        .perform(
            get("/api/contracts/" + fixture.contractId() + "/carriers")
                .header("Authorization", "Bearer " + fixture.testerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.carriers[?(@.id == '" + carrierId + "')]").doesNotExist());
  }

  @Test
  void theContractScopedCarrierCatalogRefusesATesterOfAnotherClient() throws Exception {
    Fixture fixture = aContractWithATester();
    String managerToken = fixture.managerToken();
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    String otherTesterToken = createTesterAndLogin(managerToken, otherClient, "elise.fabron@solene.example", "Passw0rd!23");

    mockMvc
        .perform(
            get("/api/contracts/" + fixture.contractId() + "/carriers")
                .header("Authorization", "Bearer " + otherTesterToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void theContractScopedCarrierCatalogOnAnUnknownContractIsNotFound() throws Exception {
    Fixture fixture = aContractWithATester();
    mockMvc
        .perform(
            get("/api/contracts/" + UUID.randomUUID() + "/carriers")
                .header("Authorization", "Bearer " + fixture.managerToken()))
        .andExpect(status().isNotFound());
  }

  // --- Completing a Topup pre-fills the Fee from its Option and links it -------------------------

  @Test
  void completingATopupRequestsFeeCanCarryTheSameOptionTheRequestNamedAtItsPreFilledPrice() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId = createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Fixture Carrier " + UUID.randomUUID());
    UUID optionId = createOption(fixture.managerToken(), carrierId, "Refill 20");
    UUID simCardId = createSimCard(fixture.managerToken(), fixture.contractId(), carrierId);

    MvcResult created =
        mockMvc
            .perform(
                post("/api/contracts/" + fixture.contractId() + "/requests")
                    .header("Authorization", "Bearer " + fixture.testerToken())
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"TOPUP","targetSimCardId":"%s","topupOptionId":"%s"}
                        """
                            .formatted(simCardId, optionId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.topupOptionPrice").value(20.00))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

    // The Agent completes it, logging a Fee at the pre-filled amount, linked to the same Option —
    // the amount stays editable (here, adjusted from 20.00 to 22.50).
    mockMvc.perform(
        patch("/api/contracts/" + fixture.contractId() + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + fixture.agentToken())
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}
                """));
    mockMvc.perform(
        patch("/api/contracts/" + fixture.contractId() + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + fixture.agentToken())
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"COMPLETED"}
                """));

    mockMvc
        .perform(
            post("/api/contracts/" + fixture.contractId() + "/fees")
                .header("Authorization", "Bearer " + fixture.agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"TOPUP","amount":22.50,"topupOptionId":"%s"}
                    """
                        .formatted(requestId, optionId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(22.50))
        .andExpect(jsonPath("$.topupOptionId").value(optionId.toString()));
  }

  // --- Observability: the audit events carry the target unit id and the Topup Option id ----------

  @Test
  void theRequestSubmittedAuditEntryCarriesTheTargetSmartphoneId() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID smartphoneId = createSmartphone(fixture.managerToken(), fixture.contractId(), "Pixel 9");

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      mockMvc
          .perform(
              post("/api/contracts/" + fixture.contractId() + "/requests")
                  .header("Authorization", "Bearer " + fixture.testerToken())
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"type":"REBOOT","targetSmartphoneId":"%s"}
                      """
                          .formatted(smartphoneId)))
          .andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged).contains("action=REQUEST_SUBMITTED").contains("targetSmartphoneId=" + smartphoneId);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void theRequestLoggedByAgentAuditEntryCarriesTheTargetSimCardAndTopupOptionId() throws Exception {
    Fixture fixture = aContractWithATester();
    UUID carrierId =
        createCarrier(fixture.managerToken(), Country.UNITED_STATES, "Fixture Carrier " + UUID.randomUUID());
    UUID optionId = createOption(fixture.managerToken(), carrierId, "Refill 20");
    UUID simCardId = createSimCard(fixture.managerToken(), fixture.contractId(), carrierId);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      mockMvc
          .perform(
              post("/api/contracts/" + fixture.contractId() + "/requests")
                  .header("Authorization", "Bearer " + fixture.agentToken())
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"type":"TOPUP","testerId":"%s","targetSimCardId":"%s","topupOptionId":"%s"}
                      """
                          .formatted(fixture.testerId(), simCardId, optionId)))
          .andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged)
          .contains("action=REQUEST_LOGGED_BY_AGENT")
          .contains("targetSimCardId=" + simCardId)
          .contains("topupOptionId=" + optionId);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- Legacy Request (no details) ----------------------------------------------------------------

  @Test
  void aRequestFromBeforeThisTicketWithNoDetailsStillListsAndCompletes() throws Exception {
    Fixture fixture = aContractWithATester();
    Contract contract = contractRepository.findById(fixture.contractId()).orElseThrow();
    Tester tester = testerRepository.findById(fixture.testerId()).orElseThrow();

    Request legacy = new Request();
    legacy.setId(UUID.randomUUID());
    legacy.setTenant(contract.getTenant());
    legacy.setContract(contract);
    legacy.setTester(tester);
    legacy.setRaisedByUser(tester.getUser());
    legacy.setAgentAuthored(false);
    legacy.setType(RequestType.TOPUP);
    legacy.setStatus(RequestStatus.SUBMITTED);
    legacy.setCreatedAt(Instant.now());
    // No targetSimCardId/topupOptionId/description set at all — exactly what a Request created
    // before this ticket looks like.
    requestRepository.saveAndFlush(legacy);

    mockMvc
        .perform(
            get("/api/contracts/" + fixture.contractId() + "/requests")
                .header("Authorization", "Bearer " + fixture.agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(legacy.getId().toString()))
        .andExpect(jsonPath("$[0].targetSimCardId").doesNotExist())
        .andExpect(jsonPath("$[0].topupOptionId").doesNotExist());

    mockMvc
        .perform(
            patch("/api/contracts/" + fixture.contractId() + "/requests/" + legacy.getId() + "/status")
                .header("Authorization", "Bearer " + fixture.agentToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/contracts/" + fixture.contractId() + "/requests/" + legacy.getId() + "/status")
                .header("Authorization", "Bearer " + fixture.agentToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"COMPLETED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // --- fixture helpers not already on IntegrationTest ---------------------------------------------

  private UUID createOption(String managerToken, UUID carrierId, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/carriers/" + carrierId + "/topup-options")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"%s","price":"20.00"}
                        """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void archiveOption(String token, UUID carrierId, UUID optionId) throws Exception {
    mockMvc
        .perform(
            post("/api/carriers/" + carrierId + "/topup-options/" + optionId + "/archive")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}

package com.remotesupport.backend.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.BillingMonth;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A first send of a Client Invoice goes through the Agent's month send ({@code POST
 * /api/me/client-invoice-months/{YYYY-MM}/send}), so a test that needs an invoice {@code SENT}
 * sends the month. Beside {@link CarrierInvoiceTestSupport}, which covers each Postpaid SIM line
 * with its file first. The seeded Agent holds no Contract in the test database, so a test with one
 * Contract for him sends the same single invoice it always sent; a test that needs some Contracts
 * sent and others not uses a second Agent.
 */
public final class ClientInvoiceMonthTestSupport {

  private ClientInvoiceMonthTestSupport() {}

  /** The current UTC month as the route names it, {@code YYYY-MM}. */
  public static String currentMonth() {
    return YearMonth.from(BillingMonth.current()).toString();
  }

  /** Sends the Agent's current month with no body and expects {@code 200}; the result carries the answer. */
  public static MvcResult sendTheMonth(MockMvc mockMvc, String agentToken) throws Exception {
    return postTheMonth(mockMvc, agentToken, currentMonth(), null)
        .andExpect(status().isOk())
        .andReturn();
  }

  /**
   * The month send for {@code month} as {@code token}, whatever it answers. {@code jsonBody} is the
   * raw body ({@code null} for none).
   */
  public static ResultActions postTheMonth(MockMvc mockMvc, String token, String month, String jsonBody)
      throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/me/client-invoice-months/" + month + "/send").header("Authorization", "Bearer " + token);
    if (jsonBody != null) {
      request = request.contentType(APPLICATION_JSON).content(jsonBody);
    }
    return mockMvc.perform(request);
  }

  /**
   * A Manager's approval of the Agent's month ({@code POST
   * /api/agents/{agentId}/client-invoice-months/{month}/approve}), whatever it answers. {@code
   * jsonBody} is the raw body ({@code null} for none).
   */
  public static ResultActions approveTheMonth(
      MockMvc mockMvc, String token, UUID agentId, String month, String jsonBody) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/agents/" + agentId + "/client-invoice-months/" + month + "/approve")
            .header("Authorization", "Bearer " + token);
    if (jsonBody != null) {
      request = request.contentType(APPLICATION_JSON).content(jsonBody);
    }
    return mockMvc.perform(request);
  }
}

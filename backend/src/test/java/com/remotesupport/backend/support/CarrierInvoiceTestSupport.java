package com.remotesupport.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The send gate refuses a Client Invoice with a Postpaid SIM line that lacks its Carrier Invoice
 * File, so every test that sends one first attaches a tiny file per line through the real
 * endpoint. A no-op on an invoice with no Postpaid SIM line, on a line already covered, and on an
 * invoice that is no longer a {@code DRAFT}.
 */
public final class CarrierInvoiceTestSupport {

  private CarrierInvoiceTestSupport() {}

  /** Covers every uncovered Postpaid SIM line of the invoice, through the by-id route. */
  public static void attachCarrierInvoicesForEveryPostpaidLine(
      MockMvc mockMvc, ObjectMapper objectMapper, UUID invoiceId, String agentToken) throws Exception {
    attach(mockMvc, objectMapper, "/api/client-invoices/" + invoiceId, agentToken);
  }

  /**
   * The same for a Contract's current-month invoice, through the current-month route (which
   * creates the draft on first access, as the send does).
   */
  public static void attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
      MockMvc mockMvc, ObjectMapper objectMapper, UUID contractId, String agentToken) throws Exception {
    attach(mockMvc, objectMapper, "/api/contracts/" + contractId + "/client-invoice", agentToken);
  }

  private static void attach(MockMvc mockMvc, ObjectMapper objectMapper, String base, String token)
      throws Exception {
    JsonNode invoice =
        objectMapper.readTree(
            mockMvc
                .perform(get(base).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    if (!"DRAFT".equals(invoice.get("status").asText()) || !invoice.hasNonNull("basePostpaidSims")) {
      return;
    }
    for (JsonNode line : invoice.get("basePostpaidSims")) {
      if (!line.get("carrierInvoiceFileId").isNull()) {
        continue;
      }
      String simCardId = line.get("simCardId").asText();
      mockMvc
          .perform(
              multipart(base + "/files")
                  .file(new MockMultipartFile("file", "bill.pdf", "application/pdf", ("bill " + simCardId).getBytes()))
                  .param("simCardId", simCardId)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isCreated());
    }
  }
}

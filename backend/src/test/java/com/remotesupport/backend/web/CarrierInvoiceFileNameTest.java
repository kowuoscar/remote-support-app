package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CarrierInvoiceFileNameTest {

  private static final LocalDate OCT = LocalDate.of(2026, 10, 1);

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("plain", "Acme Corp", "Verizon", "+1-202-555-0102", "scan.PDF",
            "Acme Corp - October 2026 - Verizon - +1-202-555-0102.pdf"),
        Arguments.of("no carrier", "Acme Corp", null, "+1-202-555-0102", "scan.pdf",
            "Acme Corp - October 2026 - +1-202-555-0102.pdf"),
        Arguments.of("blank carrier", "Acme Corp", "  ", "123", "scan.pdf",
            "Acme Corp - October 2026 - 123.pdf"),
        Arguments.of("unsafe characters", "Acme/Labs \"Test\": North", "A\\B*C?", "1<2>3|4", "x.pdf",
            "Acme_Labs _Test__ North - October 2026 - A_B_C_ - 1_2_3_4.pdf"),
        Arguments.of("control characters", "Ac\u0000me\u0007", "Ver\u001fizon", "1", "x.pdf",
            "Ac_me_ - October 2026 - Ver_izon - 1.pdf"),
        Arguments.of("whitespace runs and trim", "  Acme   \t Corp ", "Veri   zon", " 1  2 ", "x.pdf",
            "Acme Corp - October 2026 - Veri zon - 1 2.pdf"),
        Arguments.of("no extension", "Acme", "V", "1", "invoice", "Acme - October 2026 - V - 1"),
        Arguments.of("extension with space rejected", "Acme", "V", "1", "scan.final version",
            "Acme - October 2026 - V - 1"),
        Arguments.of("extension too long", "Acme", "V", "1", "a.abcdefghijk", "Acme - October 2026 - V - 1"),
        Arguments.of("extension of 10 kept lowercased", "Acme", "V", "1", "a.ABCDEFGHI1",
            "Acme - October 2026 - V - 1.abcdefghi1"),
        Arguments.of("empty extension", "Acme", "V", "1", "a.", "Acme - October 2026 - V - 1"),
        Arguments.of("non ascii extension", "Acme", "V", "1", "a.pdé", "Acme - October 2026 - V - 1"),
        Arguments.of("null upload name", "Acme", "V", "1", null, "Acme - October 2026 - V - 1"),
        Arguments.of("non ascii client kept", "Zoë Ñandú 東京", "V", "1", "a.pdf",
            "Zoë Ñandú 東京 - October 2026 - V - 1.pdf"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void computesName(
      String label, String client, String carrier, String sim, String upload, String expected) {
    assertThat(CarrierInvoiceFileName.of(client, OCT, carrier, sim, upload)).isEqualTo(expected);
  }

  @Test
  void overlongNameShortensOnlyTheClientPart() {
    String tail = " - October 2026 - Verizon - +1-202-555-0102.pdf";
    String name = CarrierInvoiceFileName.of("C".repeat(400), OCT, "Verizon", "+1-202-555-0102", "a.pdf");

    assertThat(name).hasSize(255).endsWith(tail);
    assertThat(name.substring(0, name.length() - tail.length())).isEqualTo("C".repeat(255 - tail.length()));
  }

  @Test
  void nameOfExactly255IsNotShortened() {
    String tail = " - October 2026 - V - 1.pdf";
    String client = "C".repeat(255 - tail.length());

    assertThat(CarrierInvoiceFileName.of(client, OCT, "V", "1", "a.pdf")).isEqualTo(client + tail);
  }
}

package com.remotesupport.backend.web;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The computed name of a Carrier Invoice File: {@code {Client} - {Month Year} - {Carrier} - {SIM
 * number}{.ext}}. Pure; {@code ClientInvoiceService} evaluates it once at attach and stores it.
 */
final class CarrierInvoiceFileName {

  private static final String SEPARATOR = " - ";
  private static final int MAX_LENGTH = 255;
  private static final Pattern UNSAFE = Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}\\x{80}-\\x{9F}]");
  private static final Pattern SPACES = Pattern.compile(" {2,}");
  private static final Pattern EXTENSION = Pattern.compile("\\.([A-Za-z0-9]{1,10})$");

  private CarrierInvoiceFileName() {}

  /** A null or blank {@code carrierName} leaves the Carrier part out. */
  static String of(
      String clientName,
      LocalDate billingMonth,
      String carrierName,
      String simNumber,
      String uploadedFilename) {
    String month =
        billingMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            + " "
            + billingMonth.getYear();
    String carrier = carrierName == null ? "" : clean(carrierName);

    StringBuilder tail = new StringBuilder(SEPARATOR).append(month);
    if (!carrier.isEmpty()) {
      tail.append(SEPARATOR).append(carrier);
    }
    tail.append(SEPARATOR).append(clean(simNumber)).append(extension(uploadedFilename));

    String client = clean(clientName);
    int room = Math.max(0, MAX_LENGTH - tail.length());
    if (client.length() > room) {
      int end = room;
      if (end > 0 && Character.isHighSurrogate(client.charAt(end - 1))) {
        end--;
      }
      client = client.substring(0, end).stripTrailing();
    }
    return client + tail;
  }

  /**
   * Unsafe characters, tabs and newlines and the C1 controls included, become _; then runs of plain
   * spaces collapse to one and the part is trimmed.
   */
  private static String clean(String part) {
    String safe = UNSAFE.matcher(part).replaceAll("_");
    return SPACES.matcher(safe).replaceAll(" ").strip();
  }

  private static String extension(String uploadedFilename) {
    if (uploadedFilename == null) {
      return "";
    }
    Matcher m = EXTENSION.matcher(uploadedFilename);
    return m.find() ? "." + m.group(1).toLowerCase(Locale.ROOT) : "";
  }
}

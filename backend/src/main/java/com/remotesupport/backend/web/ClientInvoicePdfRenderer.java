package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.dto.FeeResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders a {@code SENT}/{@code APPROVED} Client Invoice as a PDF on demand (ticket AC: "A Tester
 * can generate a PDF of the Client Invoice on demand; no PDF is stored at rest"). Apache PDFBox —
 * a plain Java library with no native/external service dependency, matching spec.md's "no
 * external PDF service" constraint — renders straight to an in-memory byte array that {@link
 * ClientInvoiceController} streams back; nothing here ever touches disk, so there is no file to
 * clean up and no way for a stale PDF to drift from the data it was built from. This is a data
 * document, not a designed surface (see the ticket's own note), so the layout is a plain,
 * legible top-to-bottom list — no Impeccable/craft-floor involvement needed.
 */
@Component
public class ClientInvoicePdfRenderer {

  private static final float MARGIN = 56f;
  private static final float LINE_HEIGHT = 18f;
  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

  // PDFBox 3.x dropped the pre-instantiated PDType1Font.HELVETICA/HELVETICA_BOLD static fields
  // that 2.x had — a standard-14 font is now built explicitly from Standard14Fonts.FontName.
  private static final PDFont HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final PDFont HELVETICA_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

  public byte[] render(Contract contract, ClientInvoiceResponse invoice) {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);

      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        Writer writer = new Writer(content, page.getMediaBox().getHeight());

        writer.title("Client Invoice");
        writer.line(HELVETICA_BOLD, 11, "Client: " + contract.getClient().getName());
        writer.line(HELVETICA, 11, "Billing month: " + MONTH_FORMAT.format(invoice.billingMonth()));
        writer.line(HELVETICA, 11, "Status: " + invoice.status());
        writer.line(HELVETICA, 11, "Currency: " + invoice.currency());
        writer.gap();

        writer.line(
            HELVETICA_BOLD, 11, "Base amount: " + formatAmount(invoice.baseAmount()) + " " + invoice.currency());
        writer.gap();

        writer.line(HELVETICA_BOLD, 11, "Fee lines");
        if (invoice.feeLines().isEmpty()) {
          writer.line(HELVETICA, 10, "  (none this month)");
        } else {
          for (FeeResponse fee : invoice.feeLines()) {
            String description = fee.description() != null && !fee.description().isBlank() ? " — " + fee.description() : "";
            writer.line(
                HELVETICA,
                10,
                "  " + fee.feeType() + description + ": " + formatAmount(fee.amount()) + " " + fee.currency());
          }
        }
        writer.gap();

        writer.line(HELVETICA_BOLD, 12, "Total: " + formatAmount(invoice.totalAmount()) + " " + invoice.currency());
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to render Client Invoice PDF", e);
    }
  }

  private static String formatAmount(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  /** Small top-to-bottom text cursor over one PDFBox content stream — no layout library needed. */
  private static final class Writer {
    private final PDPageContentStream content;
    private float y;

    Writer(PDPageContentStream content, float pageHeight) throws IOException {
      this.content = content;
      this.y = pageHeight - MARGIN;
    }

    void title(String text) throws IOException {
      line(HELVETICA_BOLD, 16, text);
      gap();
    }

    void line(PDFont font, float size, String text) throws IOException {
      content.beginText();
      content.setFont(font, size);
      content.newLineAtOffset(MARGIN, y);
      content.showText(text);
      content.endText();
      y -= LINE_HEIGHT;
    }

    void gap() {
      y -= LINE_HEIGHT / 2;
    }
  }
}

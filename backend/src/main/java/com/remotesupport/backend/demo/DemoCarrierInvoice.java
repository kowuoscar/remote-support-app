package com.remotesupport.backend.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** A tiny generated PDF standing in for the carrier's bill, handed to the attach as an upload. */
final class DemoCarrierInvoice implements MultipartFile {

  private static final byte[] PDF =
      ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
              + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 100]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
          .getBytes(StandardCharsets.US_ASCII);

  @Override
  public String getName() {
    return "file";
  }

  @Override
  public String getOriginalFilename() {
    return "carrier-invoice.pdf";
  }

  @Override
  public String getContentType() {
    return "application/pdf";
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public long getSize() {
    return PDF.length;
  }

  @Override
  public byte[] getBytes() {
    return PDF;
  }

  @Override
  public InputStream getInputStream() {
    return new ByteArrayInputStream(PDF);
  }

  @Override
  public void transferTo(File dest) throws IOException {
    Files.write(dest.toPath(), PDF);
  }
}

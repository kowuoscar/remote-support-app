package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One carrier invoice file an Agent attached to a {@link ClientInvoice} draft (spec.md Solution:
 * "plus attached carrier-invoice files (opaque supporting documents, not modeled entities)";
 * client-invoice-generation ticket AC: "Agent can attach one or more carrier invoice files"). The
 * file's bytes live on disk (see {@link com.remotesupport.backend.web.CarrierInvoiceFileStorage});
 * {@code storagePath} is the opaque handle this row uses to find them again, never returned to a
 * client directly.
 */
@Entity
@Table(name = "carrier_invoice_files")
@Getter
@Setter
@NoArgsConstructor
public class CarrierInvoiceFile {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "client_invoice_id", nullable = false)
  private ClientInvoice clientInvoice;

  @Column(nullable = false)
  private String filename;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "storage_path", nullable = false)
  private String storagePath;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;
}

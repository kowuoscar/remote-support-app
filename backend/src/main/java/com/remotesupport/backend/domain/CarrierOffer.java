package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a {@link Carrier} sells: a {@link TopupOption} or a {@link PostpaidPlan} (CONTEXT.md
 * "Carrier"). Both are a name and a positive price in the Carrier's Country's currency, which is
 * never stored. Archived rather than deleted: {@code archivedAt} is empty while the entry is
 * active, and archiving is one-way.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class CarrierOffer {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "carrier_id", nullable = false)
  private Carrier carrier;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isArchived() {
    return archivedAt != null;
  }
}

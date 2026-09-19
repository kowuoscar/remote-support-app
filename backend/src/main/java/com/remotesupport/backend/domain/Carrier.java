package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A mobile operator in one {@link Country} (CONTEXT.md "Carrier"). Its currency is always its
 * Country's and is never stored. Archived rather than deleted: {@code archivedAt} is empty while
 * the Carrier is active, and archiving is one-way.
 */
@Entity
@Table(name = "carriers")
@Getter
@Setter
@NoArgsConstructor
public class Carrier {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Country country;

  @Column(nullable = false)
  private String name;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isArchived() {
    return archivedAt != null;
  }
}

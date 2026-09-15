package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The tenant boundary every core table hangs off (spec.md Solution/Architecture). The MVP seeds
 * and runs exactly one tenant; the schema is tenant-scoped throughout regardless.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Tenant(UUID id, String name) {
    this.id = id;
    this.name = name;
  }
}

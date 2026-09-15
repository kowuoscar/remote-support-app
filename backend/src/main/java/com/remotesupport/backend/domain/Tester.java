package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An individual login (backed 1:1 by a {@link User} with role {@code TESTER}) belonging to
 * exactly one {@link Client}. At most one Tester per Client may be flagged as the primary
 * contact (spec.md: "One Tester may be flagged as the primary contact") — enforced by a partial
 * unique index (V4 migration) and re-checked in {@code TesterController} for a clean 409.
 */
@Entity
@Table(name = "testers")
@Getter
@Setter
@NoArgsConstructor
public class Tester {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @OneToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "is_primary_contact", nullable = false)
  private boolean primaryContact;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

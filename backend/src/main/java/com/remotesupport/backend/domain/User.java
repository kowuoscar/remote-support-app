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
 * A login belonging to exactly one {@link Tenant}, with exactly one {@link Role}.
 *
 * <p>{@code agent} links an {@code AGENT}-role User to the {@link Agent} record it corresponds
 * to, the same shape {@link Tester} already uses the other way around ({@code testers.user_id}).
 * It exists because an Agent, unlike a Tester, is created standalone by the Manager before any
 * login exists for it (fleet-management ticket prefactor) — nullable, and only ever populated
 * for {@code AGENT}-role rows. At most one User links to a given Agent (V16's unique index;
 * agent-login-on-creation spec).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(nullable = false)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @ManyToOne(optional = true)
  @JoinColumn(name = "agent_id")
  private Agent agent;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

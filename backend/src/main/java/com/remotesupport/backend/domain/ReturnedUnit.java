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
 * One Smartphone or SIM Card named on a {@link RequestType#RETURN} Request, with its own {@link
 * Disposition} (spec.md Solution: "Return type" / Disposition table; return-client-owned-smartphones
 * ticket). A Return holds SEVERAL units — unlike every other type's single target — so each named
 * unit gets its own row here rather than more nullable columns on {@link Request} (see the
 * carrier-catalog-notes.md hard rule this ticket follows). Exactly one of {@code smartphone}/{@code
 * simCard} is set (V46 migration's own CHECK). {@code disposition} is nullable at the database
 * level ({@code manager-decides-return-disposition} leaves a company-owned unit's Disposition unset
 * until the Manager chooses it at approval), but {@link
 * com.remotesupport.backend.web.requestdetails.ReturnRequestDetailsHandler} always sets it to
 * {@link Disposition#POSTED_TO_CLIENT} here, since this ticket only ever accepts a Client-owned
 * Smartphone, fixed at submission (ticket AC).
 */
@Entity
@Table(name = "returned_units")
@Getter
@Setter
@NoArgsConstructor
public class ReturnedUnit {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "request_id", nullable = false)
  private Request request;

  @ManyToOne
  @JoinColumn(name = "smartphone_id")
  private Smartphone smartphone;

  @ManyToOne
  @JoinColumn(name = "sim_card_id")
  private SimCard simCard;

  @Enumerated(EnumType.STRING)
  @Column
  private Disposition disposition;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
